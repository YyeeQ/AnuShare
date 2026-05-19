package com.example.moderationapp.logic.moderation.strategy;

public class ReportStrategyFactory {
    public static ReportStrategy create(String strategy) {
        if ("MOST".equalsIgnoreCase(strategy)) return new MostStrategy();
        if ("OLDEST".equalsIgnoreCase(strategy)) return new OldestStrategy();
        throw new IllegalArgumentException("Unknown strategy: " + strategy);
    }
}
