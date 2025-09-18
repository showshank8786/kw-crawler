package org.kwcrawler.teryt;


import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.kwcrawler.NumberUtils.parseIntOrNull;
import static org.kwcrawler.NumberUtils.parseRomanOrArabic;

public record RegionTeryt(
        String voivodeshipCode,
        String districtCode,
        String communeCode,
        String communeType,
        Integer region,
        AtomicInteger verificationCount
) {
    @Override
    public String toString() {
        return toCode();
    }

    public String toCode() {
        return voivodeshipCode + districtCode + communeCode + "_" + communeType + "." + String.format("%04d", region);
    }

    public void verify() {
        verificationCount.incrementAndGet();
    }

    public int getVerificationCount() {
        return verificationCount.get();
    }

    public static RegionTeryt fromCode(String terytId) {
        // WWPPGG_R.XXXX
        // WW — kod województwa
        // PP — kod powiatu
        // GG — kod gminy
        // R — rodzaj jednostki
        // cyfry XXXX — numer ewidencyjny obrębu

        var regex = "^(\\d{2})(\\d{2})(\\d{2})_(\\d)\\.(\\d{4})$";

        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(terytId);

        if (!matcher.matches()) {
            throw new RuntimeException("Invalid TERYT code: " + terytId);
        }

        var region = parseIntOrNull(matcher.group(5));
        if (region == null) {
            return null;
        }

        return new RegionTeryt(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3),
                matcher.group(4),
                region,
                new AtomicInteger(1)
        );
    }

    public boolean equals(RegionTeryt other) {
        return voivodeshipCode.equals(other.voivodeshipCode)
                && districtCode.equals(other.districtCode)
                && communeCode.equals(other.communeCode)
                && communeType.equals(other.communeType)
                && region.equals(other.region);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voivodeshipCode, districtCode, communeCode, communeType);
    }

    public CommuneTeryt toCommune() {
        return new CommuneTeryt(voivodeshipCode, districtCode, communeCode, communeType, new AtomicInteger(1));
    }

    public static RegionTeryt fromCommune(CommuneTeryt commune, String region) {
        var regionNumber = parseRomanOrArabic(region);
        if (regionNumber == null) {
            return null;
        }
        if (regionNumber < 1 || regionNumber > 9999) {
            System.out.println("Invalid region number: " + region);
            return null;
        }
        return new RegionTeryt(commune.voivodeshipCode(), commune.districtCode(), commune.communeCode(),
                commune.communeType(), regionNumber, new AtomicInteger(1));
    }
}
