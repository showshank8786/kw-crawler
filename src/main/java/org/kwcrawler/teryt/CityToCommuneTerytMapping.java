package org.kwcrawler.teryt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

public record CityToCommuneTerytMapping(
        @JsonProperty("city") String city,
        @JsonProperty("teryt") String teryt
) {
    public static CsvSchema schema = CsvSchema.builder()
            .addColumn("city")
            .addColumn("teryt")
            .setColumnSeparator('|')
            .build();
}
