package com.koscom.kopilot.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExportController {

    private final CardStore cardStore;
    private final XlsxExportService xlsx;

    public ExportController(CardStore cardStore, XlsxExportService xlsx) {
        this.cardStore = cardStore;
        this.xlsx = xlsx;
    }

    @GetMapping("/api/cards/{cardId}/xlsx")
    public ResponseEntity<byte[]> download(@PathVariable String cardId) {
        return cardStore.find(cardId)
                .map(card -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=kopilot-" + cardId.substring(0, 8) + ".xlsx")
                        .body(xlsx.toXlsx(card)))
                .orElse(ResponseEntity.notFound().build());
    }
}
