package com.example.coffeemonitor.web.servlet;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser minimale per body:
 * {"items":[{"code":"D-001","location_name":"Edificio 1","status":"ACTIVE"}, ...]}
 * NON è un parser JSON generico: è intenzionalmente minimale (stile progetto principale).
 */
public class SimpleJsonSyncParser {

    public static List<MonitorServlet.Item> parse(String body) {
        String s = body.trim();
        if (!s.startsWith("{") || !s.contains("\"items\"")) {
            throw new IllegalArgumentException("missing items");
        }

        int arrStart = s.indexOf('[');
        int arrEnd = s.lastIndexOf(']');
        if (arrStart < 0 || arrEnd < 0 || arrEnd <= arrStart) {
            throw new IllegalArgumentException("bad array");
        }

        String arr = s.substring(arrStart + 1, arrEnd).trim();
        List<MonitorServlet.Item> out = new ArrayList<>();
        if (arr.isBlank()) return out;

        // split grezzo su "},{" (coerente con formato previsto)
        String[] objs = arr.split("\\},\\s*\\{");
        for (String o : objs) {
            String obj = o.trim();
            if (!obj.startsWith("{")) obj = "{" + obj;
            if (!obj.endsWith("}")) obj = obj + "}";

            MonitorServlet.Item it = new MonitorServlet.Item();
            it.code = readString(obj, "code");
            it.locationName = readString(obj, "location_name");
            it.status = readString(obj, "status");
            out.add(it);
        }

        return out;
    }

    private static String readString(String obj, String key) {
        String k = "\"" + key + "\"";
        int i = obj.indexOf(k);
        if (i < 0) return null;

        int colon = obj.indexOf(':', i + k.length());
        if (colon < 0) return null;

        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) return null;

        int q2 = obj.indexOf('"', q1 + 1);
        if (q2 < 0) return null;

        return obj.substring(q1 + 1, q2);
    }
}
