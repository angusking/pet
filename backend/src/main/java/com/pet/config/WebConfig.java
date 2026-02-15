package com.pet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final String baseDir;

  public WebConfig(@Value("${app.upload.base-dir}") String baseDir) {
    this.baseDir = baseDir;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    String location = "file:" + baseDir.replace("\\", "/") + "/";
    registry.addResourceHandler("/uploads/**").addResourceLocations(location);
  }
}
