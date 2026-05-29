package com.cax.cax_backend.common.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

import com.cax.cax_backend.common.enums.CarouselEnums.CarouselType;

/**
 * Converts the lowercase string stored in MongoDB (e.g. "announcement")
 * to the CarouselType enum using the custom fromValue() lookup
 * instead of the default Enum.valueOf() which requires exact constant names.
 */
@ReadingConverter
public class CarouselTypeReadConverter implements Converter<String, CarouselType> {
    @Override
    public CarouselType convert(String source) {
        return CarouselType.fromValue(source);
    }
}
