package dev.launchforge.sdk;

/** Bounded SDK analytics diagnostics. No tenant, flag, or context values are used as labels. */
public record AnalyticsStatistics(long queued, long sent, long dropped, long failedBatches) {}
