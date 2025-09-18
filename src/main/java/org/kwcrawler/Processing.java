package org.kwcrawler;


import org.kwcrawler.analyser.AnalysedRegister;
import org.kwcrawler.analyser.RegisterAnalyser;
import org.kwcrawler.parser.ParsedRegister;
import org.kwcrawler.parser.RegisterParser;
import org.kwcrawler.structure.Filenames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class Processing {
    public interface KwProcessor {
        void process(KWNumber kwNumber, long index, long allCount);
    }

    public interface KwProcessedProcessor {
        void process(KWNumber kwNumber, ParsedRegister parsed, long index, long allCount);
    }

    public interface KwAnalysisProcessor {
        void process(KWNumber kwNumber, AnalysedRegister analysed, long index, long allCount);
    }

    public static int forEachKw(CourtCode courtCode, KwProcessor processor) {
        var searchDirectory = courtCode == null ? Paths.get(Filenames.KW_DIR) : Filenames.getCourtDir(courtCode);

        try {
            System.out.println("Counting...");
            var allCount = Files.walk(searchDirectory)
                    .filter(path -> path.getFileName().toString().endsWith("-Summary.html"))
                    .count();
            System.out.println("Processing " + allCount + " registers...");
            var count = new AtomicInteger();
            Files.walk(searchDirectory)
                    .parallel()
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("-Summary.html"))
                    .forEach(path -> {
                        var currentCount = count.incrementAndGet();

                        var kwNumber = Filenames.getKwNumber(path);

                        var downloadStatus = Downloader.getDownloadStatus(kwNumber);
                        if (downloadStatus.notDownloadedOrBroken() || downloadStatus.notFound()) {
                            return;
                        }

                        try {
                            processor.process(kwNumber, currentCount, allCount);
                        } catch (Exception e) {
                            throw new RuntimeException("Error processing " + kwNumber, e);
                        }
                    });
            return count.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static int forEachProcessedKw(CourtCode courtCode, KwProcessedProcessor processor) {
        var registerParser = new RegisterParser(courtCode);

        return forEachKw(courtCode, (kwNumber, index, allCount) -> {
            ParsedRegister parsed;
            try {
                parsed = registerParser.parse(kwNumber);
            } catch (ParseException e) {
                System.out.println("Skipping " + kwNumber + " due to parse error: " + e.getMessage());
                return;
            }
            processor.process(kwNumber, parsed, index, allCount);
        });
    }

    public static int forEachAnalysedKw(CourtCode courtCode, KwAnalysisProcessor processor) {
        var registerParser = new RegisterParser(courtCode);
        var registerAnalyser = new RegisterAnalyser(courtCode);

        return forEachKw(courtCode, (kwNumber, index, allCount) -> {
            var cached = registerAnalyser.getCached(kwNumber);

            if (cached != null) {
                processor.process(kwNumber, cached, index, allCount);
                return;
            }

            ParsedRegister parsed;
            try {
                parsed = registerParser.parse(kwNumber);
            } catch (ParseException e) {
                System.out.println("Skipping " + kwNumber + " due to parse error: " + e.getMessage());
                return;
            }

            var analysed = registerAnalyser.analyse(kwNumber, parsed);

            processor.process(kwNumber, analysed, index, allCount);
        });
    }

}
