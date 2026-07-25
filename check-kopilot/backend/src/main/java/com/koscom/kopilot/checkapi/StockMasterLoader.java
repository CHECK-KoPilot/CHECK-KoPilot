package com.koscom.kopilot.checkapi;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class StockMasterLoader implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    public StockMasterLoader(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(String... args) throws Exception {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM stock_master", Integer.class);
        if (count != null && count > 0) return;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new ClassPathResource("stock-master.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split(",");
                jdbc.update("INSERT IGNORE INTO stock_master(code,name,market,type) VALUES (?,?,?,?)",
                        f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim());
            }
        }
    }
}
