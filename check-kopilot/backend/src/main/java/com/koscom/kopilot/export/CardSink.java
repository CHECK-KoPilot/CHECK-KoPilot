package com.koscom.kopilot.export;

import com.koscom.kopilot.domain.MetricResult;

public interface CardSink {
    void save(String sessionId, MetricResult r);
}
