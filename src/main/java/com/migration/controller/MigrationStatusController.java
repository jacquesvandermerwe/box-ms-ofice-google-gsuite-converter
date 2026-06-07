package com.migration.controller;

import com.migration.model.MigrationRecord;
import com.migration.model.MigrationStatus;
import com.migration.repository.MigrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class MigrationStatusController {

    private final MigrationRepository repository;

    public MigrationStatusController(MigrationRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/status")
    public Map<String, Object> getStatus() {
        Map<MigrationStatus, Long> rawStats = repository.getStatistics();
        List<MigrationRecord> recentRecords = repository.getRecentRecords(15);

        long total = rawStats.values().stream().mapToLong(Long::longValue).sum();
        long completed = rawStats.getOrDefault(MigrationStatus.COMPLETED, 0L);
        long failed = rawStats.getOrDefault(MigrationStatus.FAILED, 0L);
        long pending = rawStats.getOrDefault(MigrationStatus.PENDING, 0L);
        
        long inProgress = rawStats.getOrDefault(MigrationStatus.IN_PROGRESS, 0L);
        long downloading = rawStats.getOrDefault(MigrationStatus.DOWNLOADING, 0L);
        long converting = rawStats.getOrDefault(MigrationStatus.CONVERTING, 0L);
        long exporting = rawStats.getOrDefault(MigrationStatus.EXPORTING, 0L);
        long uploading = rawStats.getOrDefault(MigrationStatus.UPLOADING, 0L);
        
        long active = inProgress + downloading + converting + exporting + uploading;

        double successRate = total == 0 ? 0.0 : (completed * 100.0) / total;
        double progress = total == 0 ? 0.0 : ((completed + failed) * 100.0) / total;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("pending", pending);
        stats.put("active", active);
        stats.put("successRate", Math.round(successRate * 10.0) / 10.0);
        stats.put("progress", Math.round(progress * 10.0) / 10.0);

        Map<String, Object> response = new HashMap<>();
        response.put("stats", stats);
        response.put("recent", recentRecords);

        return response;
    }
}
