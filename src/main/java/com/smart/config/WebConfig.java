package com.smart.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/image/**")
                .addResourceLocations("file:src/main/resources/static/image/");
        
//        registry.addResourceHandler("/image/**")
//        .addResourceLocations("file:/C:/Users/SUMAN SAHOO/Desktop/SCM images/");  // ✅ updated folder name

    }
}
