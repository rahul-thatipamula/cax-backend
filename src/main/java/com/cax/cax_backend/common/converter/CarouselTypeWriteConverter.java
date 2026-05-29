package com.cax.cax_backend.common.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import com.cax.cax_backend.common.enums.CarouselEnums.CarouselType;

/**
 * Converts the CarouselType enum to its lowercase string value
 * for storage in MongoDB.
 */
@WritingConverter
public class CarouselTypeWriteConverter implements Converter<CarouselType, String> {
    @Override
    public String convert(CarouselType source) {
        return source != null ? source.getValue() : null;
    }
}
