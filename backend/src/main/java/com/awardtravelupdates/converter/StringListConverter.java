package com.awardtravelupdates.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if (attribute == null) return null;
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting list to JSON", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null) return List.of();
        try {
            return MAPPER.readValue(dbData, List.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting JSON to list", e);
        }
    }
}
