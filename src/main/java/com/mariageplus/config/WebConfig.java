package com.mariageplus.config;

import com.mariageplus.security.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final WeddingApiDeprecationInterceptor weddingApiDeprecationInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    /**
     * Marque toutes les routes legacy /api/weddings/** comme dépréciées
     * (headers Deprecation / Sunset / Link + log d'avertissement).
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(weddingApiDeprecationInterceptor).addPathPatterns("/api/weddings/**");
    }
}
