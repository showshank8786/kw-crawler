package org.kwcrawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class CssCacheTest {

    private URI testUri;
    private String testContent;

    @BeforeEach
    public void setUp() {
        testUri = URI.create("http://example.com/test.css");
        testContent = "body { background-color: #fff; }";
        CssCache.clear();
    }

    @Test
    public void testAddCssFile() throws IOException {
        Path path = CssCache.addCssFile(testUri, testContent);
        assertTrue(Files.exists(path));
        assertEquals(testContent, Files.readString(path));

        Files.delete(path);
    }

    @Test
    public void testGetCssFile() throws IOException {
        CssCache.addCssFile(testUri, testContent);
        Optional<Path> path = CssCache.getCssFile(testUri);
        assertTrue(path.isPresent());
        assertEquals(testContent, Files.readString(path.get()));

        Files.delete(path.get());
    }

    @Test
    public void testAddCssFileWithDifferentContent() throws IOException {
        var oldPath = CssCache.addCssFile(testUri, testContent);
        var newContent = "body { background-color: #000; }";
        var test2Uri = URI.create("http://example.com/a/test.css");
        var newPath = CssCache.addCssFile(test2Uri, newContent);
        assertNotEquals(newPath, CssCache.getCssFile(testUri).get());
        assertEquals(newContent, Files.readString(newPath));

        Files.delete(oldPath);
        Files.delete(newPath);
    }
}