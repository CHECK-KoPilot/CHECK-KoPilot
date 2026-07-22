package com.koscom.kopilot.checkapi;

import java.time.LocalDate;

public record DailyQuote(LocalDate date, double open, double high, double low,
                         double close, long volume) {}
