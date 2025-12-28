package com.example.coffeemonitor.persistence.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnectionManager {

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // forza il load del driver
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver MySQL non trovato nel classpath!", e);
        }

        return DriverManager.getConnection(
                DbConfig.JDBC_URL,
                DbConfig.JDBC_USER,
                DbConfig.JDBC_PASSWORD
        );
    }
}
