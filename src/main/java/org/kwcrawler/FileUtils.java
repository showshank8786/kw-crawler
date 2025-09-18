package org.kwcrawler;


import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {
    public static void createFileWithDirectories(String filename) {
        var path = Paths.get(filename);

        try {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
        } catch (FileAlreadyExistsException e) {
            // ok, ignore
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
