package com.koscom.kopilot.catalog;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CatalogService {

    private final List<MetricExecutor> executors;

    public CatalogService(List<MetricExecutor> executors) {
        this.executors = executors;
    }

    public List<MetricExecutor> all() {
        return executors;
    }

    public MetricExecutor byName(String toolName) {
        return executors.stream()
                .filter(executor -> executor.toolName().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 지표 tool: " + toolName));
    }
}
