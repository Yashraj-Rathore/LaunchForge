package dev.launchforge.demo.spring;

import dev.launchforge.sdk.EvaluationContext;
import dev.launchforge.sdk.EvaluationDetail;
import dev.launchforge.sdk.LaunchForgeClient;
import org.springframework.stereotype.Service;

@Service
final class StorefrontFeatures {
  private final LaunchForgeClient client;

  StorefrontFeatures(LaunchForgeClient client) {
    this.client = client;
  }

  FeatureResponse evaluate(String subject, String country, String plan, String userId) {
    EvaluationContext context =
        EvaluationContext.builder(subject)
            .attribute("country", country)
            .attribute("plan", plan)
            .attribute("userId", userId)
            .build();
    EvaluationDetail<Boolean> checkout = client.boolVariationDetail("new-checkout", context, false);
    EvaluationDetail<String> ranking =
        client.stringVariationDetail("search-ranking", context, "lexical-v1");
    return new FeatureResponse(
        checkout.value(),
        ranking.value(),
        checkout.reason().name(),
        ranking.reason().name(),
        client.currentRevision().orElse(-1));
  }

  record FeatureResponse(
      boolean newCheckout,
      String searchRanking,
      String checkoutReason,
      String rankingReason,
      long snapshotRevision) {}
}
