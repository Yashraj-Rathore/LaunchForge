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

  FeatureResponse evaluate(String subject, String plan) {
    EvaluationContext context = EvaluationContext.builder(subject).attribute("plan", plan).build();
    EvaluationDetail<Boolean> checkout = client.boolVariationDetail("new-checkout", context, false);
    EvaluationDetail<String> theme =
        client.stringVariationDetail("checkout-theme", context, "classic");
    return new FeatureResponse(
        checkout.value(),
        theme.value(),
        checkout.reason().name(),
        theme.reason().name(),
        client.currentRevision().orElse(-1));
  }

  record FeatureResponse(
      boolean newCheckout,
      String checkoutTheme,
      String checkoutReason,
      String themeReason,
      long snapshotRevision) {}
}
