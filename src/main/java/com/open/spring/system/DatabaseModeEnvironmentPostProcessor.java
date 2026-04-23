package com.open.spring.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.HashMap;
import java.util.Map;

public class DatabaseModeEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE_NAME = "databaseModeOverrides";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbUrl = environment.getProperty("DB_URL", "").trim();
        boolean mysqlMode = dbUrl.startsWith("jdbc:mysql:");

        Map<String, Object> overrides = new HashMap<>();
        if (mysqlMode) {
            overrides.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
            overrides.put("spring.jpa.database-platform", "org.hibernate.dialect.MySQLDialect");
        } else {
            overrides.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
            overrides.put("spring.jpa.database-platform", "org.hibernate.community.dialect.SQLiteDialect");
        }

        MutablePropertySources sources = environment.getPropertySources();
        if (sources.contains(SOURCE_NAME)) {
            sources.remove(SOURCE_NAME);
        }
        sources.addFirst(new MapPropertySource(SOURCE_NAME, overrides));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}