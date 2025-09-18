package org.kwcrawler.teryt;


import org.kwcrawler.NumberUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public record ParcelTeryt(
        String voivodeshipCode,
        String districtCode,
        String communeCode,
        String communeType,
        Integer region,
        String sheet,
        String parcel
) {
    public static ParcelTeryt fromRegionAndParcelNumber(RegionTeryt region, String parcelNumber) {
        return new ParcelTeryt(
                region.voivodeshipCode(),
                region.districtCode(),
                region.communeCode(),
                region.communeType(),
                region.region(),
                null,
                parcelNumber
        );
    }

    public String toCode() {
        return voivodeshipCode + districtCode + communeCode + "_" + communeType + "." + String.format("%04d", region)
                + (sheet == null ? "" : ".AR_" + sheet)
                + "." + parcel;
    }

    public String toCodeWithoutSheet() {
        return voivodeshipCode + districtCode + communeCode + "_" + communeType + "." + String.format("%04d", region)
                + "." + parcel;
    }

    public String toString() {
        var value = toCode();
        return "\u001B]8;;https://mapy.geoportal.gov.pl/imap/?identifyParcel=" + value + "\u001B\\" + value + "\u001B]8;;\u001B\\";
    }

    public String toFileEscaped() {
        return toCodeWithoutSheet().replace("/", "_");
    }

    public String toUrlEscaped() {
        return URLEncoder.encode(toCodeWithoutSheet(), StandardCharsets.UTF_8);
    }

    public static ParcelTeryt fromParcelId(String terytId) {
        // WWPPGG_R.XXXX.NDZ
        // 040701_1.0007.AR_791.5/22
        // WW — kod województwa
        // PP — kod powiatu
        // GG — kod gminy
        // R — rodzaj jednostki
        // cyfry XXXX — numer ewidencyjny obrębu
        // NDZ — numer działki ewidencyjnej.

        var regex = "^(\\d{2})(\\d{2})(\\d{2})_(\\d)\\.(\\d{4})(?:\\.AR_(\\d+))?\\.(\\d+(?:/\\d+)?)$";

        var pattern = Pattern.compile(regex);
        var matcher = pattern.matcher(terytId);

        if (!matcher.matches()) {
            return null;
        }


        var region = NumberUtils.parseIntOrNull(matcher.group(5));
        if (region == null) {
            return null;
        }

        return new ParcelTeryt(
                matcher.group(1),
                matcher.group(2),
                matcher.group(3),
                matcher.group(4),
                region,
                matcher.group(6),
                matcher.group(7)
        );
    }

    public RegionTeryt toRegion() {
        return new RegionTeryt(voivodeshipCode, districtCode, communeCode, communeType, region, new AtomicInteger(1));
    }

    public CommuneTeryt toCommune() {
        return toRegion().toCommune();
    }
}
