package org.kwcrawler.teryt;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Teryt {

    public record TerytRecord(
            @JsonProperty("WOJ") String voivodeshipCode,
            @JsonProperty("POW") String districtCode,
            @JsonProperty("GMI") String commune,
            @JsonProperty("RODZ") String communeType,
            @JsonProperty("NAZWA") String name,
            @JsonProperty("NAZWA_DOD") String typeName,
            @JsonProperty("STAN_NA") String date) {
    }

    public record Voivodeship(
            String name,
            String code,
            Map<String, District> districtsByName,
            Map<String, District> districtsByCode
    ) {}

    public record District(
            String name,
            String code,
            Map<String, Commune> communesByName,
            Map<String, Commune> communesByCode
    ) {}

    public enum CommuneType {
        CITY_COMMUNE("1", "gmina miejska"),
        RURAL_COMMUNE("2", "gmina wiejska"),
        URBAN_RURAL_COMMUNE("3", "gmina miejsko-wiejska"),
        CITY("4", "miasto"), // miasto w gminie miejsko-wiejskiej
        RURAL("5", "obszar wiejski"), // obszar wiejski w gminie miejsko-wiejskiej
        CITY_DISTRICT("8", "dzielnica"), // dzielnice w miastach na prawach powiatu
        CITY_DELEGATION("9", "delegatura"); // delegatury w miastach: Kraków, Łódź, Poznań i Wrocław

        private final String code;
        private final String communeName;

        CommuneType(String code, String communeName) {
            this.code = code;
            this.communeName = communeName;
        }

        public String code() {
            return code;
        }

        public String communeName() {
            return communeName;
        }
    }

    public record Commune(
            String name,
            String code
    ){}

    private final Map<String, Voivodeship> voivodeshipsByName = new HashMap<>();
    private final Map<String, Voivodeship> voivodeshipsByCode = new HashMap<>();


    public Teryt() {
        var terytRecords = parseTerytCsv("TERC_Urzedowy_2025-03-20.csv");

        for (var record : terytRecords) {
            if (record.voivodeshipCode.isEmpty()) {
                throw new RuntimeException("Invalid record: " + record);
            }

            if (record.districtCode.isEmpty()) {
                addVoivodeship(record);
            } else if (record.commune.isEmpty()) {
                addDistrict(record);
            } else {
                addCommune(record);
            }
        }

    }

    private void addVoivodeship(TerytRecord record) {
        if (!record.districtCode.isEmpty()
                || !record.commune.isEmpty()
                || !record.communeType.isEmpty()) {
            throw new RuntimeException("Invalid record: " + record);
        }

        var voivodeship = new Voivodeship(record.name, record.voivodeshipCode, new HashMap<>(), new HashMap<>());
        voivodeshipsByName.put(record.name(), voivodeship);
        voivodeshipsByCode.put(record.voivodeshipCode, voivodeship);
    }

    private void addDistrict(TerytRecord record) {
        if (!record.commune.isEmpty()
                || !record.communeType.isEmpty()) {
            throw new RuntimeException("Invalid record: " + record);
        }

        var voivodeship = voivodeshipsByCode.get(record.voivodeshipCode);
        if (voivodeship == null) {
            throw new RuntimeException("Voivodeship not found: " + record.voivodeshipCode);
        }

        if (voivodeship.districtsByName.containsKey(record.name())) {
            throw new RuntimeException("Duplicate region: " + record.name());
        }

        var district = new District(record.name, record.districtCode, new HashMap<>(), new HashMap<>());

        voivodeship.districtsByName.put(record.name(), district);
        voivodeship.districtsByCode.put(record.districtCode, district);
    }

    private void addCommune(TerytRecord record) {
        var voivodeship = voivodeshipsByCode.get(record.voivodeshipCode);
        if (voivodeship == null) {
            throw new RuntimeException("Voivodeship not found: " + record.voivodeshipCode);
        }

        var district = voivodeship.districtsByCode.get(record.districtCode);
        if (district == null) {
            throw new RuntimeException("District not found: " + record.districtCode);
        }

        if (district.communesByName.containsKey(record.name())) {
            // TODO
            throw new RuntimeException("Duplicate commune: " + record.name());
        }

        var commune = new Commune(record.name, record.commune);
        district.communesByName.put(record.name(), commune);
        district.communesByCode.put(record.commune, commune);
    }

    public static List<TerytRecord> parseTerytCsv(String resourcePath) {
        try {
            var mapper = new CsvMapper();
            var schema = CsvSchema.emptySchema().withHeader().withColumnSeparator(';');
            var inputStream = Teryt.class.getClassLoader().getResourceAsStream(resourcePath);

            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            MappingIterator<TerytRecord> iterator = mapper.readerFor(TerytRecord.class).with(schema).readValues(inputStream);

            return iterator.readAll();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read TERYT file", e);
        }
    }
}
