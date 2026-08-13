package dev.launchforge.eventworker.analytics;

import java.util.List;

public interface AnalyticsStore {
  void insert(List<AnalyticsEventRow> rows);
}
