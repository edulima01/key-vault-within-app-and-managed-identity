package com.edulima01.azure.keyvault;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class Application {

	@Value("${Secrets--ConnectionString--JDBC}")
    private String connectionString;

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource)
	{
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public DataSource dataSource()
	{
		return DataSourceBuilder.create().url(connectionString).build();
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
