package org.kwcrawler.analyser;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

public class AnalysedRegisterSerialized {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysedRegisterSerialized(boolean skipEmpty) {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if (skipEmpty) {
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        }
    }

    public String serialize(AnalysedRegister parsed) {
        // convert page to json using Jackson
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public AnalysedRegister deserialize(String json) {
        try {
            return objectMapper.readValue(json, AnalysedRegister.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
