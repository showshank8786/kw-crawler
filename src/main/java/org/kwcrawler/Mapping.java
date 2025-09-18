package org.kwcrawler;


import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.kwcrawler.analyser.AnalysedRegister;
import org.kwcrawler.analyser.AnalysedRegister.CommuneOwner;
import org.kwcrawler.analyser.AnalysedRegister.LegalOwner;
import org.kwcrawler.analyser.AnalysedRegister.Owner;
import org.kwcrawler.analyser.AnalysedRegister.Parcel;
import org.kwcrawler.analyser.AnalysedRegister.TreasuryOwner;
import org.kwcrawler.teryt.ParcelTeryt;
import org.kwcrawler.teryt.TerytGuesser;
import org.kwcrawler.teryt.TerytGuesser.GuessTerytResult.ParcelFound;
import org.kwcrawler.teryt.TerytGuesser.GuessTerytResult.ParcelNotFound;
import org.kwcrawler.teryt.TerytGuesser.GuessTerytResult.ParcelRemoved;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.kwcrawler.FileUtils.createFileWithDirectories;

public class Mapping {

    record ParcelGeometry(
            String geometryWKB,
            String geometryExtent,
            String teryt,
            String voivodeship,
            String county,
            String commune,
            String region
    ) {
    }

    static class CsvParser {
        private final ObjectReader reader;

        CsvParser() {
            var mapper = new CsvMapper();
            var schema = CsvSchema.builder()
                    .addColumn("geometryWKB")
                    .addColumn("geometryExtent")
                    .addColumn("teryt")
                    .addColumn("voivodeship")
                    .addColumn("county")
                    .addColumn("commune")
                    .addColumn("region")
                    .setColumnSeparator('|')
                    .build();
            reader = mapper.readerFor(ParcelGeometry.class).with(schema);
        }

        public List<ParcelGeometry> parseCSV(String csv) {
            try (var iterator = reader.<ParcelGeometry>readValues(csv)) {

                return iterator.readAll();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void map(Main.MapCommand mapCommand) {
        createFileWithDirectories("map/lock");
        var courtCode = new CourtCode(mapCommand.courtCode);
        var geometryDownloader = new GeometryDownloader(mapCommand.proxy);

        var csvParser = new CsvParser();
        var databaseWriter = new DatabaseWriter();
        var hexFormat = HexFormat.of();

        var terrytGuesser = new TerytGuesser(courtCode);
        var ownerTypeSelector = new OwnerTypeSelector();
        var cannotGuessCount = new AtomicInteger(0);

        Processing.forEachAnalysedKw(courtCode, (kwNumber, analysed, index, allCount) -> {
            if (index % 100 == 0) {
                System.out.println("Processed " + index + "/" + allCount);
            }

            analysed.parcels().forEach(parcel -> {
                if (parcel.parcelId() == null) {
                    return;
                }

                var owners = Stream.of(
                                analysed.owners().stream()
                                        .map(Mapping::ownerToString),
                                analysed.legalOwners().stream()
                                        .map(Mapping::legalOwnerToString),
                                analysed.treasuryOwners().stream()
                                        .map(Mapping::treasuryOwnerToString),
                                analysed.communeOwners().stream()
                                        .map(Mapping::communeOwnerToString)
                        )
                        .flatMap(Function.identity()) // stream concatenation
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.joining("\n"));

                var guessResult = terrytGuesser.guessTeryt(analysed, parcel);
                ParcelTeryt parcelTeryt;

                switch (guessResult) {
                    case ParcelFound parcelFound -> parcelTeryt = parcelFound.parcel();
                    case ParcelRemoved ignored -> {
                        return;
                    }
                    case ParcelNotFound ignored -> {
                        cannotGuessCount.incrementAndGet();
                        System.out.println("Cannot guess teryt for " + kwNumber);
                        //         + " region number:" + parcel.regionNumber().currentValue()
                        //         + " region:" + parcel.region().currentValue()
                        //         + " parcel number:" + parcel.parcelNumber().currentValue()
                        //         + " district:"+ analysed.locations().stream().filter(location -> location.district().currentValue() != null).findFirst().map(location -> location.district().currentValue()).orElse("null")
                        //         + " commune:" + analysed.locations().stream().filter(location -> location.commune().currentValue() != null).findFirst().map(location -> location.commune().currentValue()).orElse("null")
                        //         + " city:" + analysed.locations().stream().filter(location -> location.city().currentValue() != null).findFirst().map(location -> location.city().currentValue()).orElse("null"));
                        return;
                    }
                }

                try {
                    var geometryCSV = geometryDownloader.downloadGeometry(kwNumber, parcelTeryt);

                    if (geometryCSV == null) {
                        return;
                    }

                    var parcelGeometries = csvParser.parseCSV(geometryCSV);

                    var parcelGeometry = selectGeometry(kwNumber, analysed, parcelGeometries, parcelTeryt, parcel);
                    if (parcelGeometry == null) {
                        return;
                    }

                    //System.out.println("Storing geometry for " + parcelGeometry.voivodeship + " " + parcelGeometry.county + " " + parcelGeometry.commune + " " + parcelGeometry.region + " " + parcelGeometry.teryt);
                    var encodedWkb = parcelGeometry.geometryWKB();

                    var description = parcelGeometry.teryt + "\n" + kwNumber.toCode() + "\n" + owners;

                    var owner_type = ownerTypeSelector.selectOwnerType(kwNumber, analysed, parcel);

                    var wasWritten = databaseWriter.write(parcelGeometry.teryt, description, owner_type, hexFormat.parseHex(encodedWkb));
                    if (wasWritten) {
                        //var geometryTeryt = ParcelTeryt.fromParcelId(parcelGeometry.teryt);
                        //System.out.println("Written " + geometryTeryt + " from " + kwNumber + " color: " + color + " (" + index + "/" + allCount + ")");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        });
        System.out.println("Cannot guess teryt for " + cannotGuessCount.get() + " parcels");
    }

    private static ParcelGeometry selectGeometry(KWNumber kwNumber, AnalysedRegister analysed,
                                                 List<ParcelGeometry> parcelGeometries, ParcelTeryt parcelTeryt, Parcel parcel) {
        if (parcelGeometries.size() == 1) {
            return parcelGeometries.getFirst();
        }

        if (parcelGeometries.isEmpty()) {
            System.out.println("No geometries for " + parcelTeryt + ", kwNumber: " + kwNumber);
            return null;
        }

        var parcelId = parcelTeryt.toCode();
        var parcelGeometryById = parcelGeometries.stream()
                .filter(parcelGeometry -> parcelGeometry.teryt.equals(parcelId))
                .findFirst();
        if (parcelGeometryById.isPresent()) {
            return parcelGeometryById.get();
        }

        System.out.println("Multiple geometries for " + parcelTeryt + ": " + parcelGeometries.stream().map(g -> g.teryt).toList() + ", kwNumber: " + kwNumber);

        String migrationComment;
        if (analysed.migrationComment() != null) {
            migrationComment = analysed.migrationComment().comment().currentValue();
        } else {
            migrationComment = null;
        }


        // probably parcels with sheets
        var geometriesFiltered = parcelGeometries.stream()
                .filter(parcelGeometry -> {
                    var parcelWithSheetTeryt = ParcelTeryt.fromParcelId(parcelGeometry.teryt);
                    if (parcelWithSheetTeryt == null || parcelWithSheetTeryt.sheet() == null) {
                        return false;
                    }

                    if (migrationComment != null
                            && (migrationComment.contains("MAPA " + parcelWithSheetTeryt.sheet()) || migrationComment.contains("MAPA NR " + parcelWithSheetTeryt.sheet()))) {
                        System.out.println("    Maybe geometry with parcelId " + parcelGeometry.teryt + " based on migration comment: " + migrationComment + ", kwNumber: " + kwNumber);
                        return true;
                    }

                    var parcelNumber = parcel.parcelNumber().values().stream()
                            .filter(value -> value.removedIndex() == null)
                            .findFirst();
                    if (parcelNumber.isEmpty()) {
                        return false;
                    }
                    int addedIndex;
                    try {
                        addedIndex = Integer.parseInt(parcelNumber.get().addedIndex());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    var addedChange = analysed.changes().stream()
                            .filter(change -> change.number() == addedIndex)
                            .findFirst();
                    if (addedChange.isEmpty()) {
                        return false;
                    }
                    var addedChangeDescription = addedChange.get().description();

                    var contains = addedChangeDescription.contains(" " + parcelWithSheetTeryt.sheet());
                    if (contains) {
                        System.out.println("    Maybe geometry with parcelId " + parcelGeometry.teryt + " based on change description: " + addedChangeDescription + ", kwNumber: " + kwNumber);
                    }
                    return contains;
                })
                .toList();

        if (geometriesFiltered.size() == 1) {
            System.out.println("Selected geometry with parcelId " + geometriesFiltered.getFirst().teryt + ", kwNumber: " + kwNumber);
            return geometriesFiltered.getFirst();
        }

        return null;
    }

    private static String ownerToString(Owner owner) {
        var builder = new StringBuilder();

        if (owner.name().isCurrentNonEmpty()) {
            builder.append(owner.name().toStringOnlyCurrent());
        }

        if (owner.surname().isCurrentNonEmpty()) {
            builder.append(" ");
            builder.append(owner.surname().toStringOnlyCurrent());
        }

        // if (owner.fatherName().isCurrentNonEmpty() || owner.motherName().isCurrentNonEmpty()) {
        //     builder.append(" (");
        //     builder.append(owner.fatherName().toStringOnlyCurrent());
        //     builder.append(" ");
        //     builder.append(owner.motherName().toStringOnlyCurrent());
        //     builder.append(")");
        // }

        // if (owner.pesel().isCurrentNonEmpty()) {
        //     builder.append(" PESEL:");
        //     builder.append(owner.pesel().toStringOnlyCurrent());
        // }

        return builder.toString();
    }

    private static String legalOwnerToString(LegalOwner legalOwner) {
        var builder = new StringBuilder();

        if (legalOwner.name().isCurrentNonEmpty()) {
            builder.append(legalOwner.name().toStringOnlyCurrent());
        }

        if (legalOwner.place().isCurrentNonEmpty()) {
            builder.append(" ");
            builder.append(legalOwner.place().toStringOnlyCurrent());
        }

        // if (legalOwner.regon().isCurrentNonEmpty()) {
        //     builder.append(" REGON:");
        //     builder.append(legalOwner.regon().toStringOnlyCurrent());
        // }

        return builder.toString();
    }

    private static String treasuryOwnerToString(TreasuryOwner treasuryOwner) {
        var builder = new StringBuilder();

        if (treasuryOwner.name().isCurrentNonEmpty()) {
            builder.append(treasuryOwner.name().toStringOnlyCurrent());
        }

        if (treasuryOwner.place().isCurrentNonEmpty()) {
            builder.append(" ");
            builder.append(treasuryOwner.place().toStringOnlyCurrent());
        }

        // if (treasuryOwner.regon().isCurrentNonEmpty()) {
        //     builder.append(" REGON:");
        //     builder.append(treasuryOwner.regon().toStringOnlyCurrent());
        // }

        // if (treasuryOwner.role().isCurrentNonEmpty()) {
        //     builder.append(", rola: ");
        //     builder.append(treasuryOwner.role().toStringOnlyCurrent());
        // }

        return builder.toString();
    }

    private static String communeOwnerToString(CommuneOwner communeOwner) {
        var builder = new StringBuilder();

        if (communeOwner.name().isCurrentNonEmpty()) {
            builder.append(communeOwner.name().toStringOnlyCurrent());
        }

        if (communeOwner.place().isCurrentNonEmpty()) {
            builder.append(" ");
            builder.append(communeOwner.place().toStringOnlyCurrent());
        }

        // if (communeOwner.regon().isCurrentNonEmpty()) {
        //     builder.append(" REGON:");
        //     builder.append(communeOwner.regon().toStringOnlyCurrent());
        // }

        return builder.toString();
    }

}
