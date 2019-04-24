package com.edulima01.azure.keyvault;

import java.util.Iterator;

import javax.sql.DataSource;

import com.microsoft.azure.keyvault.spring.KeyVaultPropertySource;
import com.microsoft.azure.spring.autoconfigure.sqlserver.KeyVaultProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
@SpringBootApplication
public class Application {

	@Value("${secrets.connectionstring}")
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

	@Autowired
    Environment env;

	@Bean
	public CommandLineRunner run(String ...vals) {
		return args -> {
			for(Iterator it = ((AbstractEnvironment)env).getPropertySources().iterator(); it.hasNext(); ) {
				PropertySource source = (PropertySource)it.next();
				if (source instanceof KeyVaultPropertySource) {
					KeyVaultPropertySource kvSource = (KeyVaultPropertySource)source;
					for (String prop : kvSource.getPropertyNames()) {
						System.out.println("PROPERTY FROM KEY VAULT: " + prop + "=" + kvSource.getProperty(prop));
					}
				}
			}
		};
	}
}
