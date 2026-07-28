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

    /** 단축키 폼에 노출할 메타. null이면 폼에 나오지 않는다(새 실행기가 잊어도 컴파일은 깨지지 않는다). */
    default PresetSpec presetSpec() { return null; }
}
