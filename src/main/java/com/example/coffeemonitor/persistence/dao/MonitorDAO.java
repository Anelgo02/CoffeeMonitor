package com.example.coffeemonitor.persistence.dao;

import com.example.coffeemonitor.persistence.util.DaoException;
import com.example.coffeemonitor.persistence.util.DbConnectionManager;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MonitorDAO {

    public static class MonitorDistributorRow {
        public String code;
        public String locationName;
        public String statusDb; // ACTIVE/MAINTENANCE/FAULT
        public Timestamp lastSeen; // può essere null
    }

    public void upsertDistributor(String code, String locationName, String statusDb) {
        String sql =
                "INSERT INTO monitor_distributors(code, location_name, status) " +
                        "VALUES(?,?,?) " +
                        "ON DUPLICATE KEY UPDATE location_name=VALUES(location_name), status=VALUES(status), updated_at=CURRENT_TIMESTAMP";

        try (Connection conn = DbConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, code);
            ps.setString(2, locationName);
            ps.setString(3, statusDb);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DaoException("Errore MonitorDAO.upsertDistributor()", e);
        }
    }

    public void deleteDistributor(String code) {
        String sql = "DELETE FROM monitor_distributors WHERE code = ?";

        try (Connection conn = DbConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, code);
            int upd = ps.executeUpdate();
            if (upd != 1) throw new DaoException("Distributore non trovato (code=" + code + ")");

        } catch (SQLException e) {
            throw new DaoException("Errore MonitorDAO.deleteDistributor()", e);
        }
    }

    public void updateStatus(String code, String statusDb) {
        String sql = "UPDATE monitor_distributors SET status = ? WHERE code = ?";

        try (Connection conn = DbConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, statusDb);
            ps.setString(2, code);

            int upd = ps.executeUpdate();
            if (upd != 1) throw new DaoException("Distributore non trovato (code=" + code + ")");

        } catch (SQLException e) {
            throw new DaoException("Errore MonitorDAO.updateStatus()", e);
        }
    }

    public void touchHeartbeat(String code) {
        try (Connection conn = DbConnectionManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                ensureDistributorExists(conn, code);

                String upsert =
                        "INSERT INTO distributor_heartbeats(distributor_code, last_seen) " +
                                "VALUES(?, CURRENT_TIMESTAMP) " +
                                "ON DUPLICATE KEY UPDATE last_seen=CURRENT_TIMESTAMP";

                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    ps.setString(1, code);
                    ps.executeUpdate();
                }

                // RECOVERY: se era FAULT e torna l'heartbeat, torna ACTIVE (a meno che non sia in MAINTENANCE)
                String recover =
                        "UPDATE monitor_distributors " +
                                "SET status='ACTIVE' " +
                                "WHERE code=? AND status='FAULT'";
                try (PreparedStatement ps = conn.prepareStatement(recover)) {
                    ps.setString(1, code);
                    ps.executeUpdate();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            throw new DaoException("Errore MonitorDAO.touchHeartbeat()", e);
        }
    }

    private void ensureDistributorExists(Connection conn, String code) throws SQLException {
        String sel = "SELECT 1 FROM monitor_distributors WHERE code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sel)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }

        String ins = "INSERT INTO monitor_distributors(code, location_name, status) VALUES(?, NULL, 'ACTIVE')";
        try (PreparedStatement ps = conn.prepareStatement(ins)) {
            ps.setString(1, code);
            ps.executeUpdate();
        }
    }

    public void markFaultIfStale(int staleSeconds) {
        String sql =
                "UPDATE monitor_distributors d " +
                        "LEFT JOIN distributor_heartbeats h ON h.distributor_code = d.code " +
                        "SET d.status = 'FAULT' " +
                        "WHERE d.status <> 'MAINTENANCE' " +
                        "AND (h.last_seen IS NULL OR h.last_seen < (CURRENT_TIMESTAMP - INTERVAL ? SECOND))";

        try (Connection conn = DbConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, staleSeconds);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DaoException("Errore MonitorDAO.markFaultIfStale()", e);
        }
    }

    public List<MonitorDistributorRow> listForMap() {
        String sql =
                "SELECT d.code, d.location_name, d.status, h.last_seen " +
                        "FROM monitor_distributors d " +
                        "LEFT JOIN distributor_heartbeats h ON h.distributor_code = d.code " +
                        "ORDER BY d.code";

        List<MonitorDistributorRow> out = new ArrayList<>();

        try (Connection conn = DbConnectionManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                MonitorDistributorRow r = new MonitorDistributorRow();
                r.code = rs.getString("code");
                r.locationName = rs.getString("location_name");
                r.statusDb = rs.getString("status");
                r.lastSeen = rs.getTimestamp("last_seen");
                out.add(r);
            }

            return out;

        } catch (SQLException e) {
            throw new DaoException("Errore MonitorDAO.listForMap()", e);
        }
    }

    public static String normalizeStatusDb(String s) {
        if (s == null) return null;
        String x = s.trim().toUpperCase();
        if ("ACTIVE".equals(x)) return "ACTIVE";
        if ("MAINTENANCE".equals(x)) return "MAINTENANCE";
        if ("FAULT".equals(x)) return "FAULT";
        return null;
    }

    public static boolean looksLikeCode(String code) {
        return code != null && !code.trim().isEmpty() && code.trim().length() <= 50;
    }

    public static long nowMs() {
        return Instant.now().toEpochMilli();
    }
}