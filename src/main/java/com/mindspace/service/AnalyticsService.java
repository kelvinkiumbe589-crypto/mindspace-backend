package com.mindspace.service;

import com.mindspace.model.PageView;
import com.mindspace.repository.PageViewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Site analytics: records anonymous page views and rolls them up into the numbers the
 * admin dashboard shows (real-time traffic, top pages, audience trends). Every rollup
 * is computed in memory from a single bounded fetch of recent rows.
 */
@Service
public class AnalyticsService {

    private static final int WINDOW_DAYS = 30; // how far back we load for all rollups

    private final PageViewRepository repo;

    public AnalyticsService(PageViewRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(String path, String referrer, String sessionId) {
        String p = clean(path, 300);
        if (p == null || p.isBlank()) p = "/";
        String sid = clean(sessionId, 64);
        if (sid == null || sid.isBlank()) sid = "anon"; // still counts as a view
        repo.save(new PageView(p, clean(referrer, 300), sid));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        LocalDateTime now = LocalDateTime.now();
        List<PageView> recent = repo.findByCreatedAtAfter(now.minusDays(WINDOW_DAYS));

        LocalDateTime m5 = now.minusMinutes(5);
        LocalDateTime m30 = now.minusMinutes(30);
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime d7 = now.minusDays(7);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeNow", uniqueVisitors(recent, m5));         // distinct sessions in last 5 min
        out.put("viewsLast30Min", countSince(recent, m30));
        out.put("viewsToday", countSince(recent, startOfToday));
        out.put("visitorsToday", uniqueVisitors(recent, startOfToday));
        out.put("views7d", countSince(recent, d7));
        out.put("visitors7d", uniqueVisitors(recent, d7));
        out.put("views30d", recent.size());
        out.put("visitors30d", uniqueVisitors(recent, now.minusDays(WINDOW_DAYS)));
        out.put("totalViews", repo.count());
        out.put("viewsByDay", viewsByDay(recent, 14));
        out.put("topPages", topPages(recent, d7, 8));
        out.put("topReferrers", topReferrers(recent, d7, 6));
        return out;
    }

    // ── rollups ───────────────────────────────────────────────────
    private long countSince(List<PageView> rows, LocalDateTime since) {
        return rows.stream().filter(v -> after(v, since)).count();
    }

    private long uniqueVisitors(List<PageView> rows, LocalDateTime since) {
        Set<String> ids = new HashSet<>();
        for (PageView v : rows) if (after(v, since)) ids.add(v.getSessionId());
        return ids.size();
    }

    private List<Map<String, Object>> viewsByDay(List<PageView> rows, int days) {
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>(); // [views, — ] + a set for uniques
        Map<LocalDate, Set<String>> uniques = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            byDay.put(d, new long[]{0});
            uniques.put(d, new HashSet<>());
        }
        for (PageView v : rows) {
            if (v.getCreatedAt() == null) continue;
            LocalDate d = v.getCreatedAt().toLocalDate();
            if (!byDay.containsKey(d)) continue;
            byDay.get(d)[0]++;
            uniques.get(d).add(v.getSessionId());
        }
        List<Map<String, Object>> list = new ArrayList<>();
        byDay.forEach((day, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("views", v[0]);
            m.put("visitors", uniques.get(day).size());
            list.add(m);
        });
        return list;
    }

    private List<Map<String, Object>> topPages(List<PageView> rows, LocalDateTime since, int limit) {
        Map<String, long[]> views = new LinkedHashMap<>();
        Map<String, Set<String>> uniques = new LinkedHashMap<>();
        for (PageView v : rows) {
            if (!after(v, since)) continue;
            String path = v.getPath();
            views.computeIfAbsent(path, k -> new long[]{0})[0]++;
            uniques.computeIfAbsent(path, k -> new HashSet<>()).add(v.getSessionId());
        }
        return views.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> e) -> e.getValue()[0]).reversed())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("path", e.getKey());
                    m.put("views", e.getValue()[0]);
                    m.put("visitors", uniques.get(e.getKey()).size());
                    return m;
                })
                .toList();
    }

    private List<Map<String, Object>> topReferrers(List<PageView> rows, LocalDateTime since, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PageView v : rows) {
            if (!after(v, since)) continue;
            counts.merge(referrerLabel(v.getReferrer()), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Comparator.comparingLong(Map.Entry<String, Long>::getValue).reversed())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("referrer", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────
    private boolean after(PageView v, LocalDateTime since) {
        return v.getCreatedAt() != null && v.getCreatedAt().isAfter(since);
    }

    // Collapse a full referrer URL to just its host for a clean "where they came from"
    // breakdown; blank referrers are direct traffic.
    private String referrerLabel(String ref) {
        if (ref == null || ref.isBlank()) return "Direct / none";
        try {
            String host = java.net.URI.create(ref).getHost();
            return host == null || host.isBlank() ? "Direct / none" : host;
        } catch (Exception e) {
            return "Other";
        }
    }

    private String clean(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
