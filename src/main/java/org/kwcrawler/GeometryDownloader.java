package org.kwcrawler;


import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.kwcrawler.teryt.ParcelTeryt;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GeometryDownloader {
    private final OkHttpClient httpClient;

    public GeometryDownloader(String proxy) {
        var builder = new OkHttpClient.Builder();
        builder.followRedirects(false);
        builder.followSslRedirects(false);
        if (proxy != null) {
            var parsedProxy = parseProxy(proxy);
            builder.proxy(parsedProxy);
        }
        httpClient = builder.build();

        System.out.println("Using proxy: " + httpClient.proxy());
    }

    @NotNull
    private static Proxy parseProxy(String proxy) {
        if (!proxy.startsWith("socks5://")) {
            throw new IllegalArgumentException("Invalid proxy format. Expected format: socks5://host:port");
        }
        String[] proxyParts = proxy.substring(9).split(":");
        if (proxyParts.length != 2) {
            throw new IllegalArgumentException("Invalid proxy format. Expected format: socks5://host:port");
        }
        String proxyHost = proxyParts[0];
        int proxyPort;
        try {
            proxyPort = Integer.parseInt(proxyParts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid proxy port. Expected a number.");
        }
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
    }

    public synchronized String downloadGeometry(KWNumber kwNumber, ParcelTeryt parcelTeryt) throws InterruptedException {
        var geometryFile = Paths.get("map/" + kwNumber.getCourtCode().getCode() + "/" + parcelTeryt.toFileEscaped() + ".csv");
        if (Files.exists(geometryFile)) {
            return readGeometryFromFile(geometryFile);
        }

        System.out.println("Downloading geometry for " + parcelTeryt + " kwNumber: " + kwNumber);
        Thread.sleep(2000);

        var url = "https://uldk.gugik.gov.pl/?request=GetParcelById&result=geom_wkb,geom_extent,teryt,voivodeship,county,commune,region&id=" + parcelTeryt.toUrlEscaped();
        var request = new Request.Builder()
                .url(url)
                .build();
        try (var response = httpClient.newCall(request).execute()) {
            return handleHttpResponse(kwNumber, parcelTeryt, response, url, geometryFile);
        } catch (SocketException | SocketTimeoutException e) {
            System.out.println("Socket exception, sleeping for 5 minutes");
            Thread.sleep(1000 * 60 * 5);
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String handleHttpResponse(KWNumber kwNumber, ParcelTeryt parcelTeryt, Response response, String url, Path geometryFile) throws InterruptedException, IOException {
        if (response.code() == 302) {
            System.out.println("Redirected to " + response.header("Location") + ", sleeping for 30 minutes");
            Thread.sleep(1000 * 50 * 30);
            return null;
        }

        var responseBody = response.body() == null ? null : response.body().string();
        if (response.code() != 200) {
            throw new RuntimeException("Error downloading geometry for " + parcelTeryt + " from " + url + ", status: "
                    + response.code() + ", body: " + responseBody);
        }

        Files.createDirectories(geometryFile.getParent());

        if (responseBody == null) {
            System.out.println("Error downloading geometry for " + parcelTeryt + " from " + url + ", empty body");
            return null;
        }

        if (responseBody.contains("Blad zapytania")) {
            System.out.println("Error downloading geometry for " + parcelTeryt + " from " + url + ", body: " + responseBody);
            return null;
        }

        Files.write(geometryFile, responseBody.getBytes());

        return handleNewLineAndErrors(kwNumber, parcelTeryt, responseBody);
    }

    private String handleNewLineAndErrors(KWNumber kwNumber, ParcelTeryt parcelTeryt, String responseBody) throws InterruptedException {
        var newLineIndex = responseBody.indexOf("\n");
        if (newLineIndex < 0) {
            System.out.println("No new line in geometry content ''" + responseBody + "' for '" + parcelTeryt + "'");
            Thread.sleep(5000);
            return null;
        }
        var firstLine = responseBody.substring(0, newLineIndex);
        if (firstLine.equals("-1 brak wyników")) {
            System.out.println("No geometry for " + parcelTeryt + " error: " + responseBody + " kwNumber: " + kwNumber);
            return null;
        }

        if (!firstLine.equals("0")) {
            throw new RuntimeException("Unexpected first line: " + firstLine);
        }

        return responseBody.substring(newLineIndex + 1);
    }

    private String readGeometryFromFile(Path geometryFile) {
        try {
            var result = Files.readString(geometryFile);

            var newLineIndex = result.indexOf("\n");
            if (newLineIndex < 0) {
                return null;
            }

            var firstLine = result.substring(0, newLineIndex);
            if (firstLine.equals("-1 brak wyników")) {
                return null;
            }

            if (!firstLine.equals("0")) {
                throw new RuntimeException("Unexpected first line: " + firstLine);
            }

            // ignore first line
            return result.substring(newLineIndex + 1);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
