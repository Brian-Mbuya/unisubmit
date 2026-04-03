package com.chuka.irir.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for the IRIR application.
 *
 * Configures:
 * <ul>
 *   <li>Static resource handlers (CSS, JS, images)</li>
 *   <li>Simple view controllers for pages without custom logic</li>
 * </ul>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Registers static resource handlers for serving CSS, JavaScript, and images.
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Serve static files from classpath:/static/
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
    }

    /**
     * Registers simple view controllers — pages that only need to render
     * a template without any custom controller logic.
     */
    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // Root URL redirects to the login page
        registry.addRedirectViewController("/", "/login");
    }
}
