package org.kwcrawler;

import org.kwcrawler.structure.Chapter;
import org.kwcrawler.structure.Filenames;
import org.jetbrains.annotations.Nullable;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class Downloader implements AutoCloseable {

    private WebDriver driver = null;
    private int count = 0;
    private final boolean headless;
    private final String proxyServer;
    private final boolean firefox;
    private final String profile;
    private final int reuseCount;
    private Instant lastDownloadTime = Instant.ofEpochSecond(0);
    private boolean additionalSleep = false;

    public Downloader(boolean headless, String proxyServer, boolean firefox, String profile, int reuseCount) {
        this.headless = headless;
        this.proxyServer = proxyServer;
        this.firefox = firefox;
        this.profile = profile;
        this.reuseCount = reuseCount;
    }

    public static DownloadStatus getDownloadStatus(KWNumber kwNumber) {
        {
            var summaryPageContent = readPageFromFile(kwNumber, Chapter.SUMMARY);

            if (summaryPageContent.isEmpty()) {
                return DownloadStatus.NOT_DOWNLOADED;
            }

            if (contentForNotExistingKW(summaryPageContent.get())) {
                return DownloadStatus.DOWNLOADED_NOT_FOUND;
            }
        }
        for (var chapter : Chapter.all()) {
            var filePath = Filenames.getFilename(kwNumber, chapter);
            if (!Files.exists(filePath)) {
                return DownloadStatus.NOT_DOWNLOADED;
            }

            // check if file contains
            // <input value="Powrót" name="Wykaz" class="text1" type="submit">

            try {
                var content = Files.readString(filePath);
                var broken = verifyContent(chapter, content);
                if (broken != null) {
                    System.out.println("Broken file: " + filePath);
                    return broken;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return DownloadStatus.DOWNLOADED_FOUND;
    }

    @Nullable
    private static DownloadStatus verifyContent(Chapter chapter, String content) {
        if (!content.contains("<input value=\"Powrót\"")) {
            System.out.println("Broken file, missing back link.");
            return DownloadStatus.BROKEN;
        }
        if (!content.contains(chapter.getFullName())) {
            System.out.println("Broken file, missing chapter: " + chapter.getTabName());
            return DownloadStatus.BROKEN;
        }
        return null;
    }

    public static boolean doesExist(KWNumber kwNumber) {
        var summaryPageContent = readPageFromFile(kwNumber, Chapter.SUMMARY);
        return !contentForNotExistingKW(summaryPageContent.get());
    }

    private void ensureTimePassedFromLastDownload() {
        var now = Instant.now();

        var timeout = additionalSleep ? 32 : 22;
        additionalSleep = false;

        var sleepTimeMillis = timeout * 1000 - Duration.between(lastDownloadTime, now).toMillis();
        if (sleepTimeMillis < 0) {
            sleepTimeMillis = 0;
        }
        sleepTimeMillis += ThreadLocalRandom.current().nextLong(0, 1000);
        System.out.println("Sleeping for " + sleepTimeMillis + "ms");

        try {
            Thread.sleep(Duration.ofMillis(sleepTimeMillis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lastDownloadTime = now;
    }

    public static boolean contentForNotExistingKW(String summaryPageContent) {
        return summaryPageContent.contains("nie została odnaleziona.");
    }

    /// Downloads KW data from the website
    /// @return true if the KW was found and downloaded, false otherwise
    public synchronized boolean download(KWNumber kwNumber) {
        setupDriverIfNecessary();

        ensureTimePassedFromLastDownload();

        System.out.println("Downloading " + kwNumber);
        count++;
        driver.get("https://przegladarka-ekw.ms.gov.pl/eukw_prz/KsiegiWieczyste/wyszukiwanieKW");

        sleep();

        // Ensure the page has loaded by waiting for a specific element to be present
        var wait = new WebDriverWait(driver, Duration.ofSeconds(30), Duration.ofMillis(100));
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(By.id("kodWydzialuInput")));
        } catch (TimeoutException e) {
            var timeoutContent = getPageContent(kwNumber);
            System.out.println("Timeout: " + timeoutContent);
            throw e;
        }

        driver.findElement(By.id("kodWydzialuInput")).sendKeys(kwNumber.getCourtCode().getCode());
        driver.findElement(By.id("numerKsiegiWieczystej")).sendKeys(kwNumber.getLedgerNumber());
        driver.findElement(By.id("cyfraKontrolna")).sendKeys(kwNumber.getControlDigit());
        driver.findElement(By.id("wyszukaj")).click();

        sleep();

        var summaryPageContent = getPageContent(kwNumber);

        if (contentForNotExistingKW(summaryPageContent)) {
            savePageToFile(kwNumber, summaryPageContent, Chapter.SUMMARY);

            additionalSleep = true;
            return false;
        }

        driver.findElement(By.name("przyciskWydrukZupelny")).sendKeys(Keys.RETURN);

        sleep();

        wait.until(ExpectedConditions.presenceOfElementLocated(By.className("tbOdpis")));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[value='Powrót']")));
        var mainPageContent = getPageContent(kwNumber);

        var broken = verifyContent(Chapter.COVER, mainPageContent);
        if (broken != null) {
            throw new RuntimeException("Broken main page content");
        }

        savePageToFile(kwNumber, summaryPageContent, Chapter.SUMMARY);
        savePageToFile(kwNumber, mainPageContent, Chapter.COVER);

        for (var chapter : Chapter.allWithoutCover()) {
            driver.findElement(By.cssSelector("input[value='" + chapter.getTabName() + "']")).click();

            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[value='Powrót']")));
            sleep();

            var pageContent = getPageContent(kwNumber);

            broken = verifyContent(chapter, pageContent);
            if (broken != null) {
                throw new RuntimeException("Broken page content");
            }
            savePageToFile(kwNumber, pageContent, chapter);
        }

        if (count >= reuseCount) {
            driver.quit();
            driver = null;
            count = 0;
        }

        return true;
    }

    private void setupDriverIfNecessary() {
        if (driver == null) {
            if (firefox) {
                driver = WebDriverConstructor.setupFirefoxDriver(headless, proxyServer, profile);
            } else {
                driver = WebDriverConstructor.setupChromeDriver(headless, proxyServer);
            }

            driver.get("https://api.ipify.org");

            // print body
            System.out.println("Public IP is: " + driver.findElement(By.tagName("body")).getText());
        }
    }

    private static void veryLongSleep() {
        try {
            Thread.sleep(1000 * 60 * 10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Optional<String> readPageFromFile(KWNumber kwNumber, Chapter chapter) {
        var filePath = Filenames.getFilename(kwNumber, chapter);

        try {
            return Optional.of(Files.readString(filePath));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void savePageToFile(KWNumber kwNumber, String pageContent, Chapter chapter) {
        if (pageContent == null) {
            throw new RuntimeException("No page content");
        }

        // Generate filename from kwNumber and store DOM to file
        var filePath = Filenames.getFilename(kwNumber, chapter);

        // store dom to file
        try {
            Files.write(filePath, pageContent.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getPageContent(KWNumber kwNumber) {
        var jsExecutor = (JavascriptExecutor) driver;
        var pageContent = (String) jsExecutor.executeScript("return document.documentElement.outerHTML;");

        if (pageContent == null) {
            throw new RuntimeException("No page content");
        }

        if (pageContent.contains("The requested URL was rejected")) {
            System.out.println("We are blocked. Sleeping for 10 minutes");
            veryLongSleep();
            throw new RuntimeException("The requested URL was rejected");
        }

        pageContent = fixCssLinks(jsExecutor, pageContent);
        pageContent = fixTopBar(kwNumber, pageContent);

        return pageContent;
    }

    private static String fixTopBar(KWNumber kwNumber, String pageContent) {
        for (var chapter : Chapter.all()) {
            try {
                pageContent = pageContent.replace("<input value=\"" + chapter.getTabName() + "\" type=\"submit\">",
                        "<a href=\"" + Filenames.getFilename(kwNumber, chapter).getFileName() + "\">" + chapter.getTabName() + "</a>");
            } catch (NoSuchElementException ignored) {
            }
        }
        return pageContent;
    }

    private String fixCssLinks(JavascriptExecutor jsExecutor, String pageContent) {
        // find all css links
        var cssLinks = driver.findElements(By.cssSelector("link[rel='stylesheet']"));

        // download css files and replace links with local paths
        for (var cssLink : cssLinks) {
            var cssHref = cssLink.getAttribute("href");
            if (cssHref == null) {
                continue;
            }
            try {
                var cssUrl = new URI(cssHref);

                var cssFileOption = CssCache.getCssFile(cssUrl);

                var cssFile = cssFileOption.orElseGet(() -> {
                    try {
                        var cssContent = (String) jsExecutor.executeScript(
                                "var xhr = new XMLHttpRequest(); xhr.open('GET', arguments[0], false); xhr.send(null); return xhr.responseText;",
                                cssHref);
                        sleep();
                        if (cssContent == null) {
                            throw new RuntimeException("No css content");
                        }
                        if (cssContent.contains("Request Rejected")) {
                            System.out.println("We are blocked while reading CSS. Sleeping for 10 minutes");
                            veryLongSleep();
                            throw new RuntimeException("Request Rejected");
                        }
                        return CssCache.addCssFile(cssUrl, cssContent);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                var originalCssHref = cssLink.getDomAttribute("href"); // return original value from HTML
                if (originalCssHref == null) {
                    continue;
                }
                var replacementCssHref = cssFile.toString().replace("downloads", "..");
                pageContent = pageContent.replace(originalCssHref, replacementCssHref);
            } catch (URISyntaxException ignored) {
            }
        }
        return pageContent;
    }

    private static void sleep() {
        try {
            long sleepTime = ThreadLocalRandom.current().nextLong(1900, 2101);
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
