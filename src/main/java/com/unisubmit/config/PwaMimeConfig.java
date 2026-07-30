package com.unisubmit.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serves the PWA manifest with its proper media type.
 * <p>
 * Tomcat's default mime mappings don't know {@code .webmanifest}, so
 * {@code /manifest.webmanifest} would go out as {@code application/octet-stream}
 * — browsers still install it, but Lighthouse flags it and strict setups may
 * refuse it. One mapping fixes that.
 */
@Configuration
public class PwaMimeConfig {

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webManifestMimeMapping() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
