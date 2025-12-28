package com.example.coffeemonitor.persistence.util;

/**
 * Configurazione DB monitor.
 * Puoi sovrascrivere con variabili d'ambiente:
 * MONITOR_DB_URL, MONITOR_DB_USER, MONITOR_DB_PASSWORD
 */
public class DbConfig {

    public static final String JDBC_URL =
            env("MONITOR_DB_URL", "jdbc:mysql://localhost:3306/coffee_monitor?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true");

    public static final String JDBC_USER =
            env("MONITOR_DB_USER", "monitor_user");

    public static final String JDBC_PASSWORD =
            env("MONITOR_DB_PASSWORD", "MonitorPass123!");

    private static String env(String k, String def) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? def : v;
    }
}
