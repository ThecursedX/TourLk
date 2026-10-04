package com.tourlk.entity;

import com.tourlk.enums.Province;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores a {@link Province} as its constant name ("NORTH_WESTERN") but reads
 * leniently: databases created by earlier versions of the backend may hold
 * "Southern", "Southern Province" and similar, which a plain
 * {@code @Enumerated(STRING)} mapping would reject with an exception (and turn
 * every destination query into a 500). Unrecognised values read as null.
 */
@Converter
public class ProvinceConverter implements AttributeConverter<Province, String> {

    @Override
    public String convertToDatabaseColumn(Province province) {
        return province == null ? null : province.name();
    }

    @Override
    public Province convertToEntityAttribute(String value) {
        return value == null ? null : Province.fromLabel(value);
    }

}
