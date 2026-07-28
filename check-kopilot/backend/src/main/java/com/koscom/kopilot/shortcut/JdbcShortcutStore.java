package com.koscom.kopilot.shortcut;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcShortcutStore {

    private static final RowMapper<Shortcut> MAPPER = (rs, rowNum) -> new Shortcut(
            rs.getString("id"), rs.getString("device_id"), rs.getString("key_combo"),
            rs.getString("tool_name"), rs.getString("targets"), rs.getString("period"),
            rs.getString("prompt"));

    private final JdbcTemplate jdbc;

    public JdbcShortcutStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 만든 순서대로. 목록이 눌린 순서와 무관하게 고정돼야 사용자가 위치를 외운다. */
    public List<Shortcut> findByDevice(String deviceId) {
        return jdbc.query("""
                SELECT * FROM shortcut WHERE device_id = ? ORDER BY created_at, id
                """, MAPPER, deviceId);
    }

    /** 키 중복은 유니크 제약이 막는다 — DuplicateKeyException이 그대로 올라간다. */
    public void insert(Shortcut s) {
        jdbc.update("""
                INSERT INTO shortcut(id, device_id, key_combo, tool_name, targets, period, prompt)
                VALUES (?,?,?,?,?,?,?)
                """, s.id(), s.deviceId(), s.keyCombo(), s.toolName(), s.targets(), s.period(), s.prompt());
    }

    /** device_id를 조건에 넣어 남의 프리셋은 아예 만나지 않게 한다. 반환값 0 = 없거나 남의 것. */
    public int update(Shortcut s) {
        return jdbc.update("""
                UPDATE shortcut SET key_combo = ?, tool_name = ?, targets = ?, period = ?, prompt = ?
                WHERE id = ? AND device_id = ?
                """, s.keyCombo(), s.toolName(), s.targets(), s.period(), s.prompt(), s.id(), s.deviceId());
    }

    public int delete(String id, String deviceId) {
        return jdbc.update("DELETE FROM shortcut WHERE id = ? AND device_id = ?", id, deviceId);
    }
}
