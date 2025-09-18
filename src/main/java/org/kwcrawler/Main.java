package org.kwcrawler;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.kwcrawler.analyser.RegisterAnalyser;
import org.kwcrawler.parser.ParsedRegister;
import org.kwcrawler.parser.ParsedRegisterSerialized;
import org.kwcrawler.parser.RegisterParser;
import org.kwcrawler.structure.Filenames;
import org.kwcrawler.teryt.TerytAnalyser;
import org.openqa.selenium.TimeoutException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static class CommonParameters {
        @Parameter(names = { "--help", "-h" }, help = true)
        private boolean help = false;
    }

    @Parameters(commandNames = "download", commandDescription = "Download KWs")
    public static class DownloadCommand {
        @Parameter(description = "KW number template", required = true)
        private String template;

        @Parameter(names = { "--max" }, description = "KW max number")
        private Integer max;

        @Parameter(names = { "--dont-shuffle" }, description = "Do not shuffle order of downloading")
        private boolean dontShuffle = false;

        @Parameter(names = { "--dry-run" }, description = "Do not download anything")
        private boolean dryRun = false;

        @Parameter(names = { "--update", "-u" }, description = "Update already downloaded files")
        private boolean update = false;

        @Parameter(names = { "--update-max" }, description = "Update max number")
        private boolean updateMax = false;

        @Parameter(names = { "--headless" }, description = "Run in headless mode")
        private boolean headless = false;

        @Parameter(names = { "--reuse-count" }, description = "Number of downloads before restarting the browser")
        private int reuseCount = 20;

        @Parameter(names = { "--proxy" }, description = "Proxy server, for instance: socks5://localhost:8080")
        private String proxy;

        @Parameter(names = { "--firefox" }, description = "Use Firefox instead of Chrome")
        private boolean firefox = false;

        @Parameter(names = { "--profile" }, description = "Firefox profile directory")
        private String profile;

        @Parameter(names = { "--max-gap" }, description = "Max gap between KWs, used for finding max KW number")
        private int maxGap = 5;
    }

    @Parameters(commandNames = "parse", commandDescription = "Parse downloaded KWs")
    public static class ParseCommand {
        @Parameter(required = true)
        private String kwNumber;
    }

    @Parameters(commandNames = "index", commandDescription = "Index downloaded KWs")
    public static class IndexCommand {
        @Parameter(description = "KW court code to index")
        private String courtCode = "";
    }

    @Parameters(commandNames = "generate-teryt", commandDescription = "Generate TERYT database from already downloaded KWs")
    public static class GenerateTerytCommand {
        @Parameter(description = "KW court code to index")
        private String courtCode = "";
    }

    @Parameters(commandNames = "map", commandDescription = "Map downloaded KWs")
    public static class MapCommand {
        @Parameter(description = "KW court code to index", required = true)
        String courtCode = "";

        @Parameter(names = { "--proxy" }, description = "Proxy server, for instance: socks5://localhost:8080")
        String proxy;
    }

    @Parameters(commandNames = "search", commandDescription = "Search indexed KWs")
    public static class SearchCommand {
        @Parameter(required = true)
        private String query;

        @Parameter(names = { "--raw" }, description = "Print raw JSON")
        private boolean raw = false;
    }

    @Parameters(commandNames = "info", commandDescription = "Print information about KW")
    public static class InfoCommand{
        @Parameter(required = true)
        private String kwNumber;

        @Parameter(names = { "--raw" }, description = "Print raw JSON")
        private boolean raw = false;
    }

    public static void main(String[] args) {
        var commonParameters = new CommonParameters();
        var downloadCommand = new DownloadCommand();
        var parseCommand = new ParseCommand();
        var indexCommand = new IndexCommand();
        var searchCommand = new SearchCommand();
        var infoCommand = new InfoCommand();
        var mapCommand = new MapCommand();
        var generateTerytCommand = new GenerateTerytCommand();

        JCommander jcommander;
        try {
            jcommander = JCommander.newBuilder()
                    .addObject(commonParameters)
                    .addCommand(downloadCommand)
                    .addCommand(parseCommand)
                    .addCommand(indexCommand)
                    .addCommand(searchCommand)
                    .addCommand(infoCommand)
                    .addCommand(mapCommand)
                    .addCommand(generateTerytCommand)
                    .build();
            jcommander.parse(args);

            if (commonParameters.help) {
                jcommander.usage();
                return;
            }
        } catch (ParameterException e) {
            System.err.println(e.getMessage());
            return;
        }

        switch (jcommander.getParsedCommand()) {
            case null -> jcommander.usage();
            case "download" -> download(downloadCommand);
            case "parse" -> parse(parseCommand);
            case "index" -> index(indexCommand);
            case "search" -> search(searchCommand);
            case "info" -> info(infoCommand);
            case "map" -> Mapping.map(mapCommand);
            case "generate-teryt" -> generateTeryt(generateTerytCommand);
            default -> {
                System.err.println("Unknown command");
                jcommander.usage();
            }
        }
    }

    public static void download(DownloadCommand downloadCommand) {
        var courtCode = validateTemplate(downloadCommand.template);
        var courtDirectory = Filenames.getCourtDir(courtCode);
        try {
            Files.createDirectories(courtDirectory);
        } catch (IOException e) {
            System.err.println("Error: Failed to create directory for court code " + courtDirectory);
            return;
        }

        try (var downloader = new Downloader(downloadCommand.headless, downloadCommand.proxy, downloadCommand.firefox, downloadCommand.profile, downloadCommand.reuseCount)) {
            // max detection
            var max = downloadCommand.max != null ? downloadCommand.max : findMax(downloader, courtCode, downloadCommand.updateMax, downloadCommand.maxGap);

            System.out.println("Max ledger number: " + max);

            // download all
            var kwNumbers = generateKwNumbers(downloadCommand.template, downloadCommand.dontShuffle, max);
            var originalSize = kwNumbers.size();
            kwNumbers = filterAlreadyDownloaded(kwNumbers, downloadCommand.update);
            downloadAllKw(kwNumbers, downloadCommand.update, downloadCommand.dryRun, originalSize, downloader);
        }
    }

    private static Integer findMax(Downloader downloader, CourtCode courtCode, boolean update, int maxGap) {
        int left = 1;
        int right = 10102;

        // quick incremental scan
        main: while (right < 1000000) {
            var doesExist = doesExist(courtCode, right, update, downloader);
            if (doesExist) {
                left = right;
                right += 10102;
                continue;
            }

            for (int depth = 1; depth <= maxGap; depth++) {
                System.out.println("Maybe just a " + depth + "-gap, checking next KW");
                if (doesExist(courtCode, right + depth, update, downloader)) {
                    System.out.println("It was a " + depth + "-gap!");
                    left = right + depth;
                    right += 10102;
                    continue main;
                }
            }

            break;
        }

        // binary search
        while (left + 1 < right) {
            int mid = left + (right - left) / 2;
            System.out.println("left: " + left + ", right: " + right + ", mid: " + mid);
            if (doesExist(courtCode, mid, update, downloader)) {
                left = mid;
            } else {
                boolean foundGap = false;
                for (int depth = 1; depth <= maxGap; depth++) {
                    if (mid + depth < right) {
                        System.out.println("Maybe just a " + depth + "-gap, checking next KW");
                        if (doesExist(courtCode, mid + depth, update, downloader)) {
                            System.out.println("It was a " + depth + "-gap!");
                            left = mid + depth;
                            foundGap = true;
                            break;
                        }
                    }
                }
                if (!foundGap) {
                    System.out.println("Assuming it is the end of the range");
                    right = mid - 1;
                }
            }
        }
        return left;
    }

    private static boolean doesExist(CourtCode courtCode, int ledgerNumber, boolean update, Downloader downloader) {
        var kwNumber = new KWNumber(courtCode, ledgerNumber);
        return doesExist(kwNumber, update, downloader);
    }

    private static boolean doesExist(KWNumber kwNumber, boolean update, Downloader downloader) {
        if (!update && Downloader.getDownloadStatus(kwNumber).correctlyDownloaded()) {
            System.out.println("Skipping " + kwNumber + " as it has already been downloaded");
            return Downloader.doesExist(kwNumber);
        } else {
            System.out.println("Checking " + kwNumber);
            while (true) {
                try {
                    return downloader.download(kwNumber);
                } catch (TimeoutException e) {
                    System.err.println("Timeout downloading " + kwNumber + ": " + e.getMessage() + ", retrying");
                } catch (RuntimeException e) {
                    System.err.println("Failed to download " + kwNumber + ", retrying");
                    e.printStackTrace();
                }
            }
        }
    }

    private static void downloadAllKw(List<KWNumber> kwNumbers, boolean update, boolean dryRun, int originalSize, Downloader downloader) {
        var startTime = Instant.now();
        var count = originalSize - kwNumbers.size();
        var downloadCount = 0;
        for (var kwNumber : kwNumbers) {
            var kwStartTime = Instant.now();
            try {
                count++;
                if (!update && Downloader.getDownloadStatus(kwNumber).correctlyDownloaded()) {
                    System.out.println("Skipping " + kwNumber + " as it has already been downloaded");
                    continue;
                }
                downloadCount++;

                if (dryRun) {
                    System.out.println("Would download " + kwNumber);
                    continue;
                }

                var found = downloader.download(kwNumber);
                if (!found) {
                    System.out.println("KW " + kwNumber + " not found");
                }

                printStatistics(kwNumber, kwStartTime, startTime, count, downloadCount, originalSize);
            } catch (TimeoutException e) {
                System.err.println("Timeout downloading " + kwNumber + ": " + e.getMessage());
            } catch (RuntimeException e) {
                System.err.println("Failed to download " + kwNumber);
                e.printStackTrace();
            }
        }
    }

    private static List<KWNumber> generateKwNumbers(String template, boolean dontShuffle, Integer max) {
        var kwNumbers = new ArrayList<KWNumber>();
        generateKWNumbersRecursive(template, 0, new StringBuilder(), kwNumbers, max);
        // randomize order of downloading
        if (!dontShuffle) {
            Collections.shuffle(kwNumbers);
        }
        return kwNumbers;
    }

    private static List<KWNumber> filterAlreadyDownloaded(List<KWNumber> kwNumbers, boolean update) {
        var brokenCount = new AtomicInteger();
        var result = kwNumbers.stream().parallel()
                .filter(kwNumber -> {
                    var status = Downloader.getDownloadStatus(kwNumber);
                    if (status == DownloadStatus.BROKEN) {
                        brokenCount.incrementAndGet();
                    }
                    return update || status.notDownloadedOrBroken();
                }).toList();

        System.out.println("Broken: " + brokenCount);
        System.out.println("Already downloaded: " + (kwNumbers.size() - result.size()));
        return result;
    }

    private static void printStatistics(KWNumber kwNumber, Instant kwStartTime, Instant startTime, int count, int downloadCount, int originalSize) {
        var now = Instant.now();

        long kwTimeInSeconds = Duration.between(kwStartTime, now).toSeconds();
        double totalTimeInSeconds = Duration.between(startTime, now).toSeconds();
        double downloadsPerMinute = downloadCount / totalTimeInSeconds * 60;

        long remainingKWs = originalSize - count;
        double averageTimePerKW = totalTimeInSeconds / (double) downloadCount;
        long estimatedRemainingTimeInSeconds = (long) (remainingKWs * averageTimePerKW);

        var estimatedDuration = Duration.ofSeconds(estimatedRemainingTimeInSeconds);
        long days = estimatedDuration.toDays();
        long hours = estimatedDuration.toHours() % 24;
        long minutes = estimatedDuration.toMinutes() % 60;

        System.out.printf("Downloaded %s (%d/%d), time: %ds, speed: %.2f KW/min, estimated remaining time: %d days, %d hours, %d minutes%n", 
            kwNumber, count, originalSize, kwTimeInSeconds, downloadsPerMinute, days, hours, minutes);
    }

    private static CourtCode validateTemplate(String template) {
        var regex = "^[A-Z0-9]{4}/[0-9X]{8}/[0-9X]$";
        if (!template.matches(regex)) {
            throw new IllegalArgumentException("Invalid template: " + template);
        }

        try {
            return new CourtCode(template.substring(0, 4));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid court code in template: " + template);
        }
    }

    /// Generates KW numbers from a template
    /// @return true if the ledger number is greater than max
    private static boolean generateKWNumbersRecursive(String template, int index, StringBuilder current, List<KWNumber> kwNumbers, Integer max) {
        if (index == template.length()) {
            var parts = current.toString().split("/");
            var courtCode = new CourtCode(parts[0]);
            var ledgerNumber = Integer.parseInt(parts[1]);

            if (max != null && ledgerNumber > max) {
                return true;
            }

            var controlDigit = Byte.parseByte(parts[2]);
            try {
                kwNumbers.add(new KWNumber(courtCode, ledgerNumber, controlDigit));
            } catch (IllegalArgumentException e) {
                // skip invalid numbers
                return false;
            }
            return false;
        }

        char ch = template.charAt(index);
        if (ch == 'X') {
            for (char digit = '0'; digit <= '9'; digit++) {
                current.append(digit);
                var maxReached = generateKWNumbersRecursive(template, index + 1, current, kwNumbers, max);
                current.setLength(current.length() - 1);

                if (maxReached) {
                    return true;
                }
            }
            return false;
        }

        current.append(ch);
        var maxReached = generateKWNumbersRecursive(template, index + 1, current, kwNumbers, max);
        current.setLength(current.length() - 1);
        return maxReached;
    }

    public static void parse(ParseCommand parseCommand) {
        var kwNumber = new KWNumber(parseCommand.kwNumber);

        var parsed = new RegisterParser(kwNumber.getCourtCode()).parse(kwNumber);

        var json = new ParsedRegisterSerialized(true).serialize(parsed);
        System.out.println(json);
    }

    public static void index(IndexCommand indexCommand) {
        FileUtils.createFileWithDirectories("index/lock");

        try (var file = new RandomAccessFile("index/lock", "rw")) {
            while (file.getChannel().tryLock() == null) {
                Thread.sleep(100);
            }
            var directory = FSDirectory.open(Paths.get("index"));
            var config = new IndexWriterConfig();
            try (var writer = new IndexWriter(directory, config)) {

                System.out.println("Indexing...");

                var courtCode = indexCommand.courtCode.isEmpty() ? null : new CourtCode(indexCommand.courtCode);
                int count = Processing.forEachAnalysedKw(courtCode, (kwNumber, analyzed, index, allCount) -> {
                    var contentBuilder = new StringBuilder();

                    var document = new Document();
                    document.add(new StringField("księga", kwNumber.toCode(), TextField.Store.YES));
                    contentBuilder.append(kwNumber).append(" ");
                    analyzed.locations().forEach(location -> {
                        if (location.voivodeship() != null) {
                            location.voivodeship().values().forEach(value -> {
                                document.add(new TextField("lokalizacja", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (location.district() != null) {
                            location.district().values().forEach(value -> {
                                document.add(new TextField("lokalizacja", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (location.commune() != null) {
                            location.commune().values().forEach(value -> {
                                document.add(new TextField("lokalizacja", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (location.city() != null) {
                            location.city().values().forEach(value -> {
                                document.add(new TextField("lokalizacja", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                    });
                    analyzed.apartments().forEach(apartment -> {
                        if (apartment.street() != null) {
                            apartment.street().values().forEach(value -> {
                                document.add(new TextField("lokal", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (apartment.buildingNumber() != null) {
                            apartment.buildingNumber().values().forEach(value -> {
                                document.add(new TextField("lokal", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (apartment.apartmentNumber() != null) {
                            apartment.apartmentNumber().values().forEach(value -> {
                                document.add(new TextField("lokal", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (apartment.purpose() != null) {
                            apartment.purpose().values().forEach(value -> {
                                document.add(new TextField("lokal", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (apartment.floor() != null) {
                            apartment.floor().values().forEach(value -> {
                                document.add(new TextField("lokal", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                    });
                    analyzed.parcels().forEach(parcel -> {
                        if (parcel.parcelId() != null) {
                            parcel.parcelId().values().forEach(value -> {
                                document.add(new TextField("działka", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (parcel.region() != null) {
                            parcel.region().values().forEach(value -> {
                                document.add(new TextField("działka", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }

                        if (parcel.parcelNumber() != null) {
                            parcel.parcelNumber().values().forEach(value -> {
                                document.add(new TextField("działka", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (parcel.usageType() != null) {
                            parcel.usageType().values().forEach(value -> {
                                document.add(new TextField("działka", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (parcel.street() != null) {
                            parcel.street().values().forEach(value -> {
                                document.add(new TextField("działka", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                    });

                    analyzed.owners().forEach(owner -> {
                        if (owner.name() != null) {
                            owner.name().values().forEach(value -> {
                                document.add(new TextField("właściciel", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (owner.surname() != null) {
                            owner.surname().values().forEach(value -> {
                                document.add(new TextField("właściciel", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (owner.pesel() != null) {
                            owner.pesel().values().forEach(value -> {
                                document.add(new TextField("właściciel", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                    });
                    analyzed.authorizeds().forEach(authorized -> {
                        if (authorized.name() != null) {
                            authorized.name().values().forEach(value -> {
                                document.add(new TextField("uprawniony", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (authorized.surname() != null) {
                            authorized.surname().values().forEach(value -> {
                                document.add(new TextField("uprawniony", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                        if (authorized.pesel() != null) {
                            authorized.pesel().values().forEach(value -> {
                                document.add(new TextField("uprawniony", value.value(), TextField.Store.NO));
                                contentBuilder.append(value.value()).append(" ");
                            });
                        }
                    });

                    // document.add(new TextField("content", serializer.serialize(parsed), TextField.Store.NO));
                    document.add(new TextField("content", contentBuilder.toString(), TextField.Store.NO));

                    try {
                        writer.updateDocument(new Term("księga", kwNumber.toCode()), document);
                        if (index % 2000 == 0) {
                            System.out.println("Indexed " + index + " documents. Committing");
                            writer.commit();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                System.out.println("Finished " + count + " documents. Committing");
                writer.commit();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

    }

    private static void printKwInfo(KWNumber kwNumber, boolean raw) {
        ParsedRegister parsed;
        try {
            parsed = new RegisterParser(kwNumber.getCourtCode()).parse(kwNumber);
        } catch (ParseException e) {
            System.out.println("Skipping " + kwNumber + " due to parse error: " + e.getMessage());
            return;
        }

        if (raw) {
            System.out.println(new ParsedRegisterSerialized(true).serialize(parsed));
            return;
        }

        var registerAnalyser = new RegisterAnalyser(kwNumber.getCourtCode());

        var analysed = registerAnalyser.analyse(kwNumber, parsed);

        System.out.println("Księga: " + kwNumber + " - " + analysed.basicInfo().registerType());
        for (var location : analysed.locations()) {
            System.out.println("    Lokalizacja " + location.number() + ": " + location.voivodeship() + ", " + location.district() + ", " + location.commune() + ", " + location.city());
        }
        for (var apartment : analysed.apartments()) {
            System.out.println("    Lokal: " + apartment.street() + " " + apartment.buildingNumber() + "/" + apartment.apartmentNumber() + ", " + apartment.purpose() + ", piętro: " + apartment.floor());
        }
        for (var parcel : analysed.parcels()) {
            System.out.print("    Działka: ");

            for (var value : parcel.parcelId().values()) {
                System.out.print("\u001B]8;;https://mapy.geoportal.gov.pl/imap/?identifyParcel=" + value.value() + "\u001B\\" + value + "\u001B]8;;\u001B\\");
            }

            System.out.print(",'");

            System.out.println(parcel.region() + " " + parcel.parcelNumber() + " Lokalizacja " + parcel.location() + " ul. " + parcel.street() + ", " + parcel.usageType());
        }
        if (analysed.area() != null) {
            System.out.println("    Obszar: " + analysed.area().area());
        }
        for (var owner : analysed.owners()) {
            System.out.println("    Właściciel: " + owner.name() + " " + owner.surname() + " (" + owner.fatherName() + ", " + owner.motherName() + "), " + owner.pesel());
        }
        for (var legalOwner : analysed.legalOwners()) {
            System.out.println("    Właściciel prawny: " + legalOwner.name() + ", " + legalOwner.place() + ", REGON: " + legalOwner.regon());
        }
        for (var treasuryOwner : analysed.treasuryOwners()) {
            System.out.println("    Właściciel Skarbu Państwa: " + treasuryOwner.name() + ", " + treasuryOwner.place() + ", REGON: " + treasuryOwner.regon() + ", Rola: " + treasuryOwner.role());
        }
        for (var communeOwner : analysed.communeOwners()) {
            System.out.println("    Właściciel jednostki samorządu terytorialnego: " + communeOwner.name() + ", " + communeOwner.place() + ", REGON: " + communeOwner.regon());
        }
        for (var authorized : analysed.authorizeds()) {
            System.out.println("    Uprawniony: " + authorized.name() + " " + authorized.surname() + " (" + authorized.fatherName() + ", " + authorized.motherName() + "), " + authorized.pesel());
        }
        System.out.println("    Zmiany:");
        for (var change : analysed.changes()) {
            System.out.println("        " + String.format("%3d", change.number()) + ". "
                    + (change.date() != null ? change.date() : "          ")
                    + (change.description() != null ? " " + change.description() : "")
                    + (change.entity() != null ? ", " + change.entity() : ""));
        }
    }

    public static void search(SearchCommand searchCommand) {
        try (var analyzer = new StandardAnalyzer()) {
            var parser = new QueryParser("content", analyzer);

            var query = parser.parse(searchCommand.query);

            var directory = FSDirectory.open(Paths.get("index"));
            var reader = DirectoryReader.open(directory);
            var indexSearcher = new IndexSearcher(reader);

            var hits = indexSearcher.search(query, 100).scoreDocs;
            var storedFields = indexSearcher.storedFields();

            for (var hit : hits) {
                var document = storedFields.document(hit.doc);
                var kwNumber = new KWNumber(document.get("księga"));

                printKwInfo(kwNumber, searchCommand.raw);
            }

        } catch (org.apache.lucene.queryparser.classic.ParseException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void info(InfoCommand infoCommand) {
        var kwNumber = new KWNumber(infoCommand.kwNumber);
        printKwInfo(kwNumber, infoCommand.raw);
    }

    private static void generateTeryt(GenerateTerytCommand generateTerytCommand) {
        var terytGuesser = new TerytAnalyser();
        var courtCode = new CourtCode(generateTerytCommand.courtCode);
        Processing.forEachAnalysedKw(courtCode, (kwNumber, analysed, index, allCount) -> {
            terytGuesser.learn(kwNumber, analysed);
        });

        terytGuesser.writeMappings(courtCode);
    }
}