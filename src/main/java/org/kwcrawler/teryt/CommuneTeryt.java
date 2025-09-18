package org.kwcrawler.teryt;


import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public record CommuneTeryt(
        String voivodeshipCode,
        String districtCode,
        String communeCode,
        String communeType,
        AtomicInteger verificationCount

){

    @Override
    public String toString() {
        return voivodeshipCode + districtCode + communeCode + "_" + communeType;
    }

    public void verify() {
        verificationCount.incrementAndGet();
    }

    public int getVerificationCount() {
        return verificationCount.get();
    }
    public boolean equals(CommuneTeryt other) {
        return voivodeshipCode.equals(other.voivodeshipCode)
                && districtCode.equals(other.districtCode)
                && communeCode.equals(other.communeCode)
                && communeType.equals(other.communeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voivodeshipCode, districtCode, communeCode, communeType);
    }

    public static CommuneTeryt fromCode(String terytId) {
        // WWPPGG_R
        // WW — kod województwa
        // PP — kod powiatu
        // GG — kod gminy
        // R — rodzaj jednostki

        var regex = "^(\\d{2})(\\d{2})(\\d{2})_(\\d)$";

        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(terytId);

        if (!matcher.matches()) {
            throw new RuntimeException("Invalid TERYT code: " + terytId);
        }

        return new CommuneTeryt(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3),
                matcher.group(4),
                new AtomicInteger(1)
        );
    }
}
