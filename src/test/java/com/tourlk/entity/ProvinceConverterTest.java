package com.tourlk.entity;

import com.tourlk.enums.Province;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProvinceConverterTest {

    private final ProvinceConverter converter = new ProvinceConverter();

    @Test
    void writesTheConstantName() {
        assertThat(converter.convertToDatabaseColumn(Province.NORTH_WESTERN)).isEqualTo("NORTH_WESTERN");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void readsConstantNamesAndLegacySpellings() {
        assertThat(converter.convertToEntityAttribute("SOUTHERN")).isEqualTo(Province.SOUTHERN);
        assertThat(converter.convertToEntityAttribute("Southern")).isEqualTo(Province.SOUTHERN);
        assertThat(converter.convertToEntityAttribute("Southern Province")).isEqualTo(Province.SOUTHERN);
        assertThat(converter.convertToEntityAttribute("North Western")).isEqualTo(Province.NORTH_WESTERN);
        assertThat(converter.convertToEntityAttribute("NORTH_CENTRAL")).isEqualTo(Province.NORTH_CENTRAL);
    }

    @Test
    void unrecognisedOrMissingValuesReadAsNull() {
        assertThat(converter.convertToEntityAttribute("Central Highlands")).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
