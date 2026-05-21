package com.example.moderationapp.logic.moderation.strategy;

public class ReportStrategyFactory {
    public static ReportStrategy create(String strategy) {
        if ("MOST".equals(strategy)) return new MostStrategy();
        if ("OLDEST".equals(strategy)) return new OldestStrategy();
        if ("PRIORITY".equals(strategy)) return new PriorityStrategy();
        throw new IllegalArgumentException("Unknown strategy: " + strategy);
    }
}
