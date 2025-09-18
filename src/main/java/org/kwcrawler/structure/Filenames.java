package org.kwcrawler.structure;


import org.kwcrawler.CourtCode;
import org.kwcrawler.KWNumber;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Filenames {
    public static final String KW_DIR = "downloads";
    public static final String CSS_DIR = "downloads/css";
    public static final String DATA_DIR = "data";
    public static final String PARSED_DIR = "parsed";
    public static final String ANALYSED_DIR = "analysed";

    public static Path getCourtDir(CourtCode courtCode) {
        return Paths.get(KW_DIR + "/" + courtCode);
    }

    public static Path getFilename(KWNumber kwNumber, Chapter chapter) {
        return getFilename(kwNumber, chapter.getTabName());
    }

    public static Path getFilename(KWNumber kwNumber, String suffix) {
        return getCourtDir(kwNumber.getCourtCode()).resolve(getJsonFilename(kwNumber, suffix));
    }

    private static String getJsonFilename(KWNumber kwNumber, String suffix) {
        return kwNumber.toCode().replace("/", "_") + "-" + suffix + ".html";
    }

    public static Path getCssFile(URI url, int fileNumber) {
        // get just filename from url
        var path = url.getPath();

        var fileName = path.substring(path.lastIndexOf('/') + 1);
        // if .css extension then remove .css extension
        if (fileName.endsWith(".css")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        return Paths.get(CSS_DIR, fileName + "-" + String.format("%04d", fileNumber) + ".css");
    }

    public static KWNumber getKwNumber(Path path) {
        // example path: downloads/WL1W/WL1W_00055509_3-Summary.html

        var filename = path.getFileName().toString();
        var parts = filename.split("_");

        var courtCode = new CourtCode(parts[0]);
        var ledgerNumber = Integer.parseInt(parts[1]);
        var controlDigit = Byte.parseByte(parts[2].split("-")[0]);

        return new KWNumber(courtCode, ledgerNumber, controlDigit);
    }

    public static Path getDataDir(CourtCode courtCode) {
        return Paths.get(DATA_DIR + "/" + courtCode);
    }

    public static Path getParsedDir(CourtCode courtCode) {
        return Paths.get(PARSED_DIR + "/" + courtCode);
    }

    public static Path getAnalysedDir(CourtCode courtCode) {
        return Paths.get(ANALYSED_DIR + "/" + courtCode);
    }

    public static Path getParsedFileName(KWNumber kwNumber) {
        return getParsedDir(kwNumber.getCourtCode()).resolve(getJsonFilename(kwNumber));
    }

    private static String getJsonFilename(KWNumber kwNumber) {
        return kwNumber.toCode().replace("/", "_") + ".json";
    }

    public static Path getAnalysedFilename(KWNumber kwNumber) {
        return getAnalysedDir(kwNumber.getCourtCode()).resolve(getJsonFilename(kwNumber));
    }
}
