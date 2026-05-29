package com.cax.cax_backend.common.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import com.cax.cax_backend.common.converter.CarouselTypeReadConverter;
import com.cax.cax_backend.common.converter.CarouselTypeWriteConverter;
import com.cax.cax_backend.common.converter.UserRoleReadConverter;
import com.cax.cax_backend.common.converter.UserRoleWriteConverter;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(
                new UserRoleReadConverter(),
                new UserRoleWriteConverter(),
                new CarouselTypeReadConverter(),
                new CarouselTypeWriteConverter()
        ));
    }
}
