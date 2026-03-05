package com.awardtravelupdates.config;

import com.awardtravelupdates.model.SummaryUpdate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class SummaryUpdateListConverter implements AttributeConverter<List<SummaryUpdate>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<SummaryUpdate> updates) {
        try {
            return objectMapper.writeValueAsString(updates);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<SummaryUpdate> convertToEntityAttribute(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
