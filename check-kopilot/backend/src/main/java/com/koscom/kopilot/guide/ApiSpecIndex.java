package com.koscom.kopilot.guide;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/** CHECK API 776건의 경로·파라미터·반환 F코드 목록. 필드 설명은 FieldDictionary가 갖는다. */
public class ApiSpecIndex {

    /** 원본 레코드 — 반환 필드는 코드 목록만 보관한다 */
    public record Raw(String path, String title, List<String> codes, List<ApiSpecEntry.Param> params) {}

    private final Map<String, Raw> byPath;      // path -> raw
    private final Map<String, String> idToPath; // apiId(별칭 + 파생 id) -> path

    public ApiSpecIndex(Map<String, Raw> byPath, Map<String, String> idToPath) {
        this.byPath = byPath;
        this.idToPath = idToPath;
    }

    /** `/stock/m001/hist_info` → `stock-m001-hist_info` */
    public static String derivedId(String path) {
        return path.replaceFirst("^/", "").replace('/', '-');
    }

    /** 문서 페이지 URL. 개별 페이지 매핑이 없으므로 카테고리 목록 페이지로 보낸다(스펙 13절 "명세 링크 부재" 대응). */
    public static String docUrlOf(String path) {
        String category = path.replaceFirst("^/", "").split("/")[0];
        return "https://checkapi.koscom.co.kr/#/" + category;
    }

    @SuppressWarnings("unchecked")
    public static ApiSpecIndex loadFromClasspath() {
        try (var apisIn = new ClassPathResource("check-api/apis.json").getInputStream();
             var aliasIn = new ClassPathResource("check-api/api-aliases.yaml").getInputStream()) {

            Map<String, Map<String, Object>> raw = new ObjectMapper()
                    .readValue(apisIn, new TypeReference<Map<String, Map<String, Object>>>() {});

            Map<String, Raw> byPath = new LinkedHashMap<>();
            Map<String, String> idToPath = new LinkedHashMap<>();
            for (var e : raw.entrySet()) {
                String path = e.getKey();
                List<String> codes = (List<String>) e.getValue().getOrDefault("res", List.of());
                List<List<String>> ps = (List<List<String>>) e.getValue().getOrDefault("params", List.of());
                List<ApiSpecEntry.Param> params = ps.stream()
                        .map(p -> new ApiSpecEntry.Param(p.get(0), "O".equals(p.get(1)))).toList();
                byPath.put(path, new Raw(path, String.valueOf(e.getValue().getOrDefault("title", "")), codes, params));
                idToPath.put(derivedId(path), path);
            }

            Map<String, Object> aliasRoot = new Yaml().load(aliasIn);
            Map<String, String> aliases =
                    (Map<String, String>) aliasRoot.getOrDefault("aliases", Map.of());
            aliases.forEach((alias, path) -> {
                if (!byPath.containsKey(path)) {
                    throw new IllegalStateException("api-aliases.yaml의 경로가 apis.json에 없다: " + path);
                }
                idToPath.put(alias, path);
            });

            return new ApiSpecIndex(byPath, idToPath);
        } catch (Exception e) {
            throw new IllegalStateException("API 인덱스 로드 실패", e);
        }
    }

    public Collection<Raw> raws() { return byPath.values(); }

    public List<ApiSpecEntry> all() {
        return byPath.values().stream().map(r -> toEntry(r, List.of(), null)).toList();
    }

    public Optional<ApiSpecEntry> byId(String apiId) {
        String path = idToPath.get(apiId);
        if (path == null) path = byPath.containsKey(apiId) ? apiId : null;   // 경로 자체도 허용
        return Optional.ofNullable(path).map(byPath::get).map(r -> toEntry(r, List.of(), null));
    }

    public Optional<Raw> rawById(String apiId) {
        String path = idToPath.getOrDefault(apiId, byPath.containsKey(apiId) ? apiId : null);
        return Optional.ofNullable(path).map(byPath::get);
    }

    public String docUrl(String apiId) {
        return rawById(apiId).map(r -> docUrlOf(r.path())).orElse("");
    }

    /** 별칭이 있으면 별칭을 apiId로 노출한다(레시피 가독성) */
    private String preferredId(String path) {
        return idToPath.entrySet().stream()
                .filter(e -> e.getValue().equals(path) && !e.getKey().equals(derivedId(path)))
                .map(Map.Entry::getKey).findFirst().orElse(derivedId(path));
    }

    ApiSpecEntry toEntry(Raw r, List<String> showCodes, FieldDictionary dict) {
        List<ApiSpecEntry.Field> fields = showCodes.stream()
                .map(c -> new ApiSpecEntry.Field(c, dict == null ? c : dict.label(c))).toList();
        return new ApiSpecEntry(preferredId(r.path()), r.title(), r.path(),
                r.title() + " — 반환 필드 " + r.codes().size() + "개",
                r.params(), docUrlOf(r.path()), fields);
    }
}
