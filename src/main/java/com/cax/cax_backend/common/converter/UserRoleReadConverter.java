package com.cax.cax_backend.common.converter;

import com.cax.cax_backend.common.enums.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

/**
 * Converts the lowercase string stored in MongoDB (e.g. "superstudent")
 * to the UserRole enum using the custom fromValue() lookup
 * instead of the default Enum.valueOf() which requires exact constant names.
 */
@ReadingConverter
public class UserRoleReadConverter implements Converter<String, UserRole> {
    @Override
    public UserRole convert(String source) {
        return UserRole.fromValue(source);
    }
}
