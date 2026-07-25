package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.domain.MetricResult;

import java.util.List;
import java.util.Map;

/** 지표 1종은 구현 클래스 1개로 추가한다. */
public interface MetricExecutor {

    String toolName();

    String description();

    Map<String, Object> inputSchemaProperties();

    List<String> requiredParams();

    MetricResult execute(JsonNode args);
}
