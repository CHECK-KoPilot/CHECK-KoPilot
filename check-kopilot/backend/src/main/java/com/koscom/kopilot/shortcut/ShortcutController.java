package com.koscom.kopilot.shortcut;

import com.koscom.kopilot.catalog.CatalogService;
import com.koscom.kopilot.catalog.MetricExecutor;
import com.koscom.kopilot.catalog.PresetSpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 단축키 프리셋 CRUD. 소유자는 로그인이 아니라 브라우저가 발급한 X-Device-Id다.
 *
 * 계산이 없는 API지만 검증은 조인다 — 잘못 저장된 프리셋은 사용자가 키를 누를 때마다
 * 되묻기로 새고, 그 시점엔 왜 그런지 알기 어렵다.
 */
@RestController
@RequestMapping("/api/shortcuts")
public class ShortcutController {

    private static final Pattern KEY_COMBO = Pattern.compile("^ctrl\\+shift\\+[a-z0-9]$");
    private static final int MAX_PROMPT_LENGTH = 300;
    private static final int MAX_DEVICE_ID_LENGTH = 64;
    /** shortcut.targets 컬럼 폭. 넘치면 INSERT가 터지므로 저장 전에 400으로 막는다. */
    private static final int MAX_TARGETS_LENGTH = 255;
    private static final Set<String> PERIODS = Set.of("1M", "3M", "6M", "1Y");

    public record ShortcutRequest(String keyCombo, String toolName, List<String> targets,
                                  String period, String prompt) {}

    public record ShortcutView(String id, String keyCombo, String toolName, List<String> targets,
                               String period, String prompt) {}

    /** 상태코드와 코드값을 함께 나르는 예외. 프론트가 code로 분기한다. */
    static class ShortcutException extends RuntimeException {
        final HttpStatus status;
        final String code;
        ShortcutException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    private final JdbcShortcutStore store;
    private final CatalogService catalog;

    public ShortcutController(JdbcShortcutStore store, CatalogService catalog) {
        this.store = store;
        this.catalog = catalog;
    }

    @GetMapping
    public List<ShortcutView> list(@RequestHeader("X-Device-Id") String deviceId) {
        return store.findByDevice(requireDeviceId(deviceId)).stream().map(ShortcutController::toView).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShortcutView create(@RequestHeader("X-Device-Id") String deviceId,
                               @RequestBody ShortcutRequest body) {
        Shortcut shortcut = validated(UUID.randomUUID().toString(), requireDeviceId(deviceId), body);
        try {
            store.insert(shortcut);
        } catch (DuplicateKeyException e) {
            throw keyTaken(shortcut.keyCombo());
        }
        return toView(shortcut);
    }

    @PutMapping("/{id}")
    public ShortcutView update(@RequestHeader("X-Device-Id") String deviceId,
                               @PathVariable String id,
                               @RequestBody ShortcutRequest body) {
        Shortcut shortcut = validated(id, requireDeviceId(deviceId), body);
        int changed;
        try {
            changed = store.update(shortcut);
        } catch (DuplicateKeyException e) {
            throw keyTaken(shortcut.keyCombo());
        }
        if (changed == 0) throw notFound();
        return toView(shortcut);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("X-Device-Id") String deviceId, @PathVariable String id) {
        if (store.delete(id, requireDeviceId(deviceId)) == 0) throw notFound();
    }

    @ExceptionHandler(ShortcutException.class)
    ResponseEntity<Map<String, String>> handle(ShortcutException e) {
        return ResponseEntity.status(e.status).body(Map.of("code", e.code, "message", e.getMessage()));
    }

    private static ShortcutException keyTaken(String combo) {
        return new ShortcutException(HttpStatus.CONFLICT, "KEY_TAKEN",
                "이미 사용 중인 키 조합입니다: " + combo);
    }

    private static ShortcutException notFound() {
        // 남의 프리셋인지 없는 프리셋인지 구분해 주지 않는다
        return new ShortcutException(HttpStatus.NOT_FOUND, "NOT_FOUND", "단축키를 찾을 수 없습니다");
    }

    private static String requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || deviceId.length() > MAX_DEVICE_ID_LENGTH) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "DEVICE_ID_INVALID",
                    "X-Device-Id 헤더가 필요합니다");
        }
        return deviceId;
    }

    private Shortcut validated(String id, String deviceId, ShortcutRequest body) {
        if (body == null || body.keyCombo() == null || !KEY_COMBO.matcher(body.keyCombo()).matches()) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "KEY_COMBO_INVALID",
                    "키 조합은 ctrl+shift+<숫자·영문> 형식이어야 합니다");
        }

        MetricExecutor executor;
        try {
            executor = catalog.byName(body.toolName());
        } catch (IllegalArgumentException e) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "TOOL_UNKNOWN",
                    "알 수 없는 지표입니다: " + body.toolName());
        }
        PresetSpec preset = executor.presetSpec();
        if (preset == null) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "TOOL_UNKNOWN",
                    "단축키로 만들 수 없는 지표입니다: " + body.toolName());
        }

        List<String> targets = body.targets() == null ? List.of()
                : body.targets().stream().filter(t -> t != null && !t.isBlank()).map(String::trim).toList();
        if (targets.size() < preset.minTargets() || targets.size() > preset.maxTargets()) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "TARGET_COUNT_INVALID",
                    "%s은(는) 종목 %d~%d개가 필요합니다"
                            .formatted(preset.label(), preset.minTargets(), preset.maxTargets()));
        }

        String prompt = body.prompt() == null ? "" : body.prompt().trim();
        if (prompt.isEmpty() || prompt.length() > MAX_PROMPT_LENGTH) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "PROMPT_INVALID",
                    "프롬프트는 1~%d자여야 합니다".formatted(MAX_PROMPT_LENGTH));
        }

        // 종목명이 긴 ETN·ETF를 최대 개수만큼 담으면 255자를 넘긴다 — 컬럼이 터지기 전에 되돌려준다
        String joined = String.join(",", targets);
        if (joined.length() > MAX_TARGETS_LENGTH) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "TARGETS_TOO_LONG",
                    "종목 이름이 너무 깁니다. 종목 수를 줄여주세요");
        }

        String period = body.period() == null || body.period().isBlank() ? null : body.period().trim();
        if (period != null && !PERIODS.contains(period)) {
            throw new ShortcutException(HttpStatus.BAD_REQUEST, "PERIOD_INVALID",
                    "기간은 1M · 3M · 6M · 1Y 중 하나여야 합니다");
        }
        return new Shortcut(id, deviceId, body.keyCombo(), body.toolName(), joined, period, prompt);
    }

    private static ShortcutView toView(Shortcut s) {
        List<String> targets = s.targets() == null || s.targets().isBlank()
                ? List.of() : Arrays.stream(s.targets().split(",")).toList();
        return new ShortcutView(s.id(), s.keyCombo(), s.toolName(), targets, s.period(), s.prompt());
    }
}
