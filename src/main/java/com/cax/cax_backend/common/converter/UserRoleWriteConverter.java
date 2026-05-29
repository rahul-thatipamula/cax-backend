package com.cax.cax_backend.common.converter;

import com.cax.cax_backend.common.enums.UserRole;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

/**
 * Converts UserRole enum to its lowercase string value for MongoDB storage.
 */
@WritingConverter
public class UserRoleWriteConverter implements Converter<UserRole, String> {
    @Override
    public String convert(UserRole source) {
        return source.getValue();
    }
}
