package com.koscom.kopilot.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 단축키 폼이 지표 목록을 그릴 때 쓴다. 카탈로그의 단일 출처는 실행기다. */
@RestController
public class CatalogController {

    public record CatalogItem(String toolName, String label, String description,
                              String promptTemplate, int minTargets, int maxTargets) {}

    private final CatalogService catalog;

    public CatalogController(CatalogService catalog) { this.catalog = catalog; }

    @GetMapping("/api/catalog")
    public List<CatalogItem> list() {
        return catalog.all().stream()
                .filter(executor -> executor.presetSpec() != null)
                .map(executor -> {
                    PresetSpec preset = executor.presetSpec();
                    return new CatalogItem(executor.toolName(), preset.label(), executor.description(),
                            preset.promptTemplate(), preset.minTargets(), preset.maxTargets());
                })
                .toList();
    }
}
