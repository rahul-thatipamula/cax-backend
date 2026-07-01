package com.cax.cax_backend.common.config;

import com.cax.cax_backend.common.ratelimit.AdaptiveRateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdaptiveRateLimitInterceptor adaptiveRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adaptiveRateLimitInterceptor).addPathPatterns("/**");
    }
}
