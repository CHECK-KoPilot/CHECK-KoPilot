package com.koscom.kopilot.guide;

import java.util.List;

public record ApiSpecEntry(String apiId, String name, String path, String summary,
                           List<Param> params, String docUrl, List<Field> fields) {

    public record Param(String name, boolean required) {}
    public record Field(String code, String label) {}
}
