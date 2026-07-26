package com.koscom.kopilot.demand;

public record DemandSummary(String topic, long requestCount, long explicitCount,
                            long sessionCount, String matchedApiIds, String lastAt) {}
