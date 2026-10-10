package com.medichain.forecast.config;

import com.medichain.forecast.engine.AnalysisEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {
    @Bean
    public AnalysisEngine analysisEngine() {
        return new AnalysisEngine();
    }
}
