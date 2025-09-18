package org.kwcrawler.teryt;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

public record RegionTerytMapping(
        @JsonProperty("region") String region,
        @JsonProperty("teryt") String teryt
) {
    public static CsvSchema schema = CsvSchema.builder()
            .addColumn("region")
            .addColumn("teryt")
            .setColumnSeparator('|')
            .build();
}
