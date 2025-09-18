package org.kwcrawler;


import org.kwcrawler.structure.Filenames;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CssCache {
    private static final Map<URI, Path> cssFiles = new HashMap<>();

    public static void clear() {
        cssFiles.clear();
    }

    public static Optional<Path> getCssFile(URI url) {
        return Optional.ofNullable(cssFiles.get(url));
    }

    // if filename exists reads its content
    // if content is the same as the new one, returns
    // if content is different, increments fileNumber and tries again
    // if filename does not exist, writes content to file and returns
    public static Path addCssFile(URI url, String content) throws IOException {
        if (cssFiles.containsKey(url)) {
            return cssFiles.get(url);
        }

        int fileNumber = 0;

        while (true) {
            var filename = Filenames.getCssFile(url, fileNumber);
            Files.createDirectories(filename.getParent());

            if (Files.exists(filename)) {
                var existingContent = Files.readString(filename);
                if (existingContent.equals(content)) {
                    cssFiles.put(url, filename);
                    return filename;
                } else {
                    fileNumber++;
                    continue;
                }
            }

            Files.writeString(filename, content);
            cssFiles.put(url, filename);
            return filename;
        }
    }
}
