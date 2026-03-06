package com.awardtravelupdates.converter;

import com.awardtravelupdates.model.FlightDeal;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class FlightDealListConverter implements AttributeConverter<List<FlightDeal>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<FlightDeal> deals) {
        try {
            return objectMapper.writeValueAsString(deals);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Override
    public List<FlightDeal> convertToEntityAttribute(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
