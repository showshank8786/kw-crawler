package org.kwcrawler;


import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;

import java.io.File;

public class WebDriverConstructor {

    static WebDriver setupChromeDriver(boolean headless, String proxyServer) {
        var service = new ChromeDriverService.Builder()
                .usingDriverExecutable(new File("chromedriver"))
                .usingAnyFreePort()
                .build();
        var options = new ChromeOptions();
        options.addArguments("--disable-search-engine-choice-screen");

        // set proxy
        if (proxyServer != null) {
            options.addArguments("--proxy=" + proxyServer, "--new-instance");
            if (proxyServer.startsWith("socks5://")) {
                var proxy = new Proxy().setSocksProxy(proxyServer.substring(9)).setSocksVersion(5);
                options.setProxy(proxy);
            } else {
                throw new IllegalArgumentException("Unknown proxy type");
            }
        }

        if (headless) {
            options.addArguments("--headless=new");
        }
        return new ChromeDriver(service, options);
    }

    static WebDriver setupFirefoxDriver(boolean headless, String proxy, String profile) {
        var service = new GeckoDriverService.Builder()
                .build();
        var options = new FirefoxOptions();
        //options.setBinary(Path.of("/usr/bin/firefox"));
        options.addArguments("--new-instance");
        if (profile != null) {
            options.addArguments("--profile", profile);
        }
        // set socks proxy
        if (proxy != null) {
            // ensure proxy is in format 'socks5://<host>:<port>'
            if (!proxy.startsWith("socks5://")) {
                throw new IllegalArgumentException("Unknown proxy type, only socks5 is supported");
            }
            // remove 'socks5://' prefix
            var proxyServer = proxy.substring(9);

            options.addPreference("network.proxy.type", 1);
            options.addPreference("network.proxy.socks", proxyServer.split(":")[0]);
            options.addPreference("network.proxy.socks_port", Integer.parseInt(proxyServer.split(":")[1]));
        }

        if (headless) {
            options.addArguments("--headless");
        }

        return new FirefoxDriver(service, options);
    }
}
