package io.github.harshjoeyit.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.File;

@Slf4j
@Configuration
public class DatabaseConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.sqlite.JDBC")
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.driver-class-name}") String driverClassName,
            @Value("${spring.datasource.hikari.maximum-pool-size:1}") int maxPoolSize) {

        ensureDirectoryExists(url);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maxPoolSize);
        return new HikariDataSource(config);
    }

    private void ensureDirectoryExists(String url) {
        if (url != null && url.startsWith("jdbc:sqlite:")) {
            String pathPart = url.substring("jdbc:sqlite:".length());
            int queryIndex = pathPart.indexOf('?');
            String filePath = queryIndex != -1 ? pathPart.substring(0, queryIndex) : pathPart;
            if (!":memory:".equals(filePath) && !filePath.isBlank()) {
                File dbFile = new File(filePath);
                File parentDir = dbFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created && !parentDir.exists()) {
                        throw new IllegalStateException("Failed to create SQLite database directory: " + parentDir.getAbsolutePath());
                    }
                    log.info("Created SQLite database directory: {}", parentDir.getAbsolutePath());
                }
            }
        }
    }
}
