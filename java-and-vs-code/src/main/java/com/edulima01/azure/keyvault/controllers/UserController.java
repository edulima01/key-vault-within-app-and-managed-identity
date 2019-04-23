package com.edulima01.azure.keyvault.controllers;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class UserController {
    
    @Value("${Secrets--ConnectionString--JDBC}")
    private String connectionString;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @RequestMapping("/api/User")
    public Result index() {
        String userName = (String)jdbcTemplate.queryForObject("SELECT SYSTEM_USER", String.class);
        Result result = new Result();
        result.setConnectionString(connectionString);
        result.setResult(userName);
        return result;
    }
    
    private class Result {
        private String connectionString;
        private String result;

        /**
         * @return the connectionString
         */
        public String getConnectionString() {
            return connectionString;
        }

        /**
         * @return the userName
         */
        public String getResult() {
            return result;
        }

        /**
         * @param userName the userName to set
         */
        public void setResult(String result) {
            this.result = result;
        }

        /**
         * @param connectionString the connectionString to set
         */
        public void setConnectionString(String connectionString) {
            this.connectionString = connectionString;
        }
    }
}