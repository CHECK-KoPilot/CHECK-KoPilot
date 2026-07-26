package com.koscom.kopilot.demand;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class CatalogRequestController {

    private final DemandRecorder demand;

    public CatalogRequestController(DemandRecorder demand) { this.demand = demand; }

    /** 가이드 카드의 "카탈로그 추가 요청" 버튼 — 기록만 한다(스펙 6절). */
    @PostMapping("/api/catalog-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public void request(@RequestBody Map<String, String> body) {
        demand.record(body.get("sessionId"), body.get("topic"),
                body.get("matchedApiIds"), DemandRecorder.EXPLICIT);
    }
}
