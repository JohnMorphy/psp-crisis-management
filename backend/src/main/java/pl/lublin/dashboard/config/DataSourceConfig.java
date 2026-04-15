package pl.lublin.dashboard.config;

import org.springframework.context.annotation.Configuration;

/**
 * Konfiguracja źródła danych.
 * HikariCP jest auto-konfigurowany przez Spring Boot na podstawie application.yml.
 * Ustawienia połączenia: spring.datasource.*
 * Ustawienia puli: spring.datasource.hikari.*
 */
@Configuration
public class DataSourceConfig {
    // Rozszerz tę klasę przy potrzebie niestandardowej konfiguracji puli połączeń,
    // np. różnych DataSource dla trybu read-only i read-write.
}
