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

    private static final String DOC_BASE = "https://checkapi.koscom.co.kr/";

    /** 개별 문서 페이지를 갖지 않는 경로(776건 중 62건)는 카테고리 목록으로 보낸다 */
    private static final Set<String> RPATHS = loadRpaths();

    /**
     * 근거 패널에 노출하는 CHECK API 명세 페이지 URL.
     * 형식은 {@code <base>/<카테고리>/<rpath>}이며, rpath는 호출 경로에서 파생된다 —
     * 호출 경로 {@code /stock/m001/hist_info}의 문서는 {@code /stock/m001hist}로
     * 모듈과 오퍼레이션이 붙고 {@code _info}와 밑줄이 빠진다(실측 확인, 776건 중 714건 적중).
     * 파생 rpath가 실제 문서에 없으면 카테고리 목록 페이지로 폴백한다(스펙 13절 "명세 링크 부재" 대응).
     */
    public static String docUrlOf(String path) {
        String[] parts = path.replaceFirst("^/", "").split("/");
        String category = parts[0];
        if (parts.length >= 3) {
            String operation = String.join("", Arrays.copyOfRange(parts, 2, parts.length));
            String rpath = parts[1] + operation.replace("_info", "").replace("_", "");
            if (RPATHS.contains(rpath)) {
                return DOC_BASE + category + "/" + rpath;
            }
        }
        return DOC_BASE + "#/" + category;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> loadRpaths() {
        try (var in = new ClassPathResource("check-api/menu.json").getInputStream()) {
            List<Map<String, Object>> menu = new ObjectMapper()
                    .readValue(in, new TypeReference<List<Map<String, Object>>>() {});
            Set<String> rpaths = new HashSet<>();
            Deque<Map<String, Object>> queue = new ArrayDeque<>(menu);
            while (!queue.isEmpty()) {
                Map<String, Object> node = queue.pop();
                Object rpath = node.get("rpath");
                if (rpath instanceof String s) {
                    rpaths.add(s);
                }
                for (String child : List.of("d1", "d2", "d3")) {
                    if (node.get(child) instanceof List<?> children) {
                        children.forEach(c -> queue.push((Map<String, Object>) c));
                    }
                }
            }
            return Set.copyOf(rpaths);
        } catch (Exception e) {
            throw new IllegalStateException("check-api/menu.json 로드 실패", e);
        }
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
