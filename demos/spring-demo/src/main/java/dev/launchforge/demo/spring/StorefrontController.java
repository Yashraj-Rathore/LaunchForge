package dev.launchforge.demo.spring;

import dev.launchforge.demo.spring.StorefrontFeatures.FeatureResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo")
final class StorefrontController {
  private final StorefrontFeatures features;

  StorefrontController(StorefrontFeatures features) {
    this.features = features;
  }

  @GetMapping("/{subject}")
  FeatureResponse features(
      @PathVariable String subject, @RequestParam(defaultValue = "free") String plan) {
    return features.evaluate(subject, plan);
  }
}
