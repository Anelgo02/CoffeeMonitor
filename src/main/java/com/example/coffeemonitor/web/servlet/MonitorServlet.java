package com.example.coffeemonitor.web.servlet;

import com.example.coffeemonitor.persistence.dao.MonitorDAO;
import com.example.coffeemonitor.persistence.util.DaoException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@WebServlet(urlPatterns = {
        "/api/monitor/distributors/create",
        "/api/monitor/distributors/delete",
        "/api/monitor/distributors/status",
        "/api/monitor/heartbeat",
        "/api/monitor/sync",
        "/api/monitor/map"
})
public class MonitorServlet extends HttpServlet {

    private final MonitorDAO dao = new MonitorDAO();

    private static volatile long lastFaultCalcMs = 0L;
    private static final long FAULT_RECALC_WINDOW_MS = 30_000;
    private static final int STALE_SECONDS = 180;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            resp.setStatus(200);
            return;
        }
        super.service(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setupJson(resp);

        if (req.getRequestURI().endsWith("/map")) {
            handleMap(resp);
            return;
        }

        resp.sendError(404);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        setupJson(resp);

        String uri = req.getRequestURI();

        if (uri.endsWith("/heartbeat")) { handleHeartbeat(req, resp); return; }
        if (uri.endsWith("/distributors/create")) { handleCreate(req, resp); return; }
        if (uri.endsWith("/distributors/delete")) { handleDelete(req, resp); return; }
        if (uri.endsWith("/distributors/status")) { handleStatus(req, resp); return; }
        if (uri.endsWith("/sync")) { handleSync(req, resp); return; }

        resp.sendError(404);
    }

    private void handleHeartbeat(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String code = trim(req.getParameter("code"));
        if (!MonitorDAO.looksLikeCode(code)) {
            write(resp, 400, "{\"ok\":false,\"message\":\"code obbligatorio\"}");
            return;
        }

        try {
            dao.touchHeartbeat(code);
            write(resp, 200, "{\"ok\":true}");
        } catch (DaoException ex) {
            ex.printStackTrace();
            write(resp, 500, "{\"ok\":false,\"message\":\"errore DB\"}");
        }
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String code = trim(req.getParameter("code"));
        String loc = trim(req.getParameter("location_name"));
        String status = MonitorDAO.normalizeStatusDb(trim(req.getParameter("status")));

        if (!MonitorDAO.looksLikeCode(code)) {
            write(resp, 400, "{\"ok\":false,\"message\":\"code obbligatorio\"}");
            return;
        }
        if (status == null) status = "ACTIVE";

        try {
            dao.upsertDistributor(code, loc, status);
            write(resp, 201, "{\"ok\":true}");
        } catch (DaoException ex) {
            ex.printStackTrace();
            write(resp, 500, "{\"ok\":false,\"message\":\"errore DB\"}");
        }
    }

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String code = trim(req.getParameter("code"));
        if (!MonitorDAO.looksLikeCode(code)) {
            write(resp, 400, "{\"ok\":false,\"message\":\"code obbligatorio\"}");
            return;
        }

        try {
            dao.deleteDistributor(code);
            write(resp, 200, "{\"ok\":true}");
        } catch (DaoException ex) {
            write(resp, 200, "{\"ok\":true}");
        }
    }

    private void handleStatus(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String code = trim(req.getParameter("code"));
        String status = MonitorDAO.normalizeStatusDb(trim(req.getParameter("status")));

        if (!MonitorDAO.looksLikeCode(code) || status == null) {
            write(resp, 400, "{\"ok\":false,\"message\":\"code e status obbligatori\"}");
            return;
        }

        try {
            dao.updateStatus(code, status);
            write(resp, 200, "{\"ok\":true}");
        } catch (DaoException ex) {
            ex.printStackTrace();
            write(resp, 500, "{\"ok\":false,\"message\":\"errore DB\"}");
        }
    }

    private void handleSync(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String body = req.getReader().lines().reduce("", (a,b) -> a + b);
        if (body == null || body.isBlank()) {
            write(resp, 400, "{\"ok\":false,\"message\":\"body obbligatorio\"}");
            return;
        }

        try {
            List<Item> items = SimpleJsonSyncParser.parse(body);

            for (Item it : items) {
                if (!MonitorDAO.looksLikeCode(it.code)) continue;
                String st = MonitorDAO.normalizeStatusDb(it.status);
                if (st == null) st = "ACTIVE";
                dao.upsertDistributor(it.code, it.locationName, st);
            }

            write(resp, 200, "{\"ok\":true,\"count\":" + items.size() + "}");

        } catch (Exception ex) {
            ex.printStackTrace();
            write(resp, 400, "{\"ok\":false,\"message\":\"json non valido\"}");
        }
    }

    private void handleMap(HttpServletResponse resp) throws IOException {
        long now = MonitorDAO.nowMs();
        if (now - lastFaultCalcMs > FAULT_RECALC_WINDOW_MS) {
            try {
                dao.markFaultIfStale(STALE_SECONDS);
                lastFaultCalcMs = now;
            } catch (DaoException ex) {
                ex.printStackTrace();
            }
        }

        try {
            var list = dao.listForMap();

            StringBuilder json = new StringBuilder();
            json.append("{\"ok\":true,\"items\":[");
            boolean first = true;

            for (var r : list) {
                if (!first) json.append(",");
                first = false;

                json.append("{")
                        .append("\"code\":\"").append(escJson(r.code)).append("\",")
                        .append("\"location_name\":\"").append(escJson(r.locationName)).append("\",")
                        .append("\"status\":\"").append(escJson(r.statusDb)).append("\",")
                        .append("\"last_seen\":\"").append(escJson(r.lastSeen == null ? "" : r.lastSeen.toString())).append("\"")
                        .append("}");
            }

            json.append("]}");
            write(resp, 200, json.toString());

        } catch (DaoException ex) {
            ex.printStackTrace();
            write(resp, 500, "{\"ok\":false,\"message\":\"errore DB\"}");
        }
    }

    public static class Item {
        public String code;
        public String locationName;
        public String status;
    }

    private void setupJson(HttpServletResponse resp) {
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/json");
        resp.setHeader("Cache-Control", "no-store");
    }

    private void write(HttpServletResponse resp, int status, String payload) throws IOException {
        resp.setStatus(status);
        resp.getWriter().write(payload);
    }

    private String trim(String s) { return s == null ? null : s.trim(); }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}