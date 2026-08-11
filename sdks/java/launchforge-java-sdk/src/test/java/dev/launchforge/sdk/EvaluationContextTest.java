package dev.launchforge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EvaluationContextTest {
  @Test
  void validatesReservedNamesScalarTypesUnicodeAndBounds() {
    assertThrows(
        IllegalArgumentException.class,
        () -> EvaluationContext.builder("subject").attribute("key", "override"));
    assertThrows(
        IllegalArgumentException.class,
        () -> EvaluationContext.builder("subject").attribute("score", Double.NaN));
    assertThrows(
        IllegalArgumentException.class,
        () -> EvaluationContext.builder("subject").attribute("bad name", "value"));
    assertThrows(
        IllegalArgumentException.class, () -> EvaluationContext.builder("bad\ud800").build());
    assertThrows(IllegalArgumentException.class, () -> EvaluationContext.builder("").build());

    EvaluationContext.Builder tooLarge = EvaluationContext.builder("subject");
    for (int index = 0; index < 17; index++) {
      tooLarge.attribute("attribute" + index, "x".repeat(1_024));
    }
    assertThrows(IllegalArgumentException.class, tooLarge::build);

    EvaluationContext.Builder escapedSize = EvaluationContext.builder("subject");
    escapedSize.attribute("first", "\u0001".repeat(1_024));
    escapedSize.attribute("second", "\u0001".repeat(1_024));
    escapedSize.attribute("third", "\u0001".repeat(1_024));
    assertThrows(IllegalArgumentException.class, escapedSize::build);
  }

  @Test
  void builderCopiesValuesAndNullStringRemovesAnAttribute() {
    EvaluationContext context =
        EvaluationContext.builder("subject")
            .attribute("plan", "pro")
            .attribute("score", -0.0d)
            .attribute("active", true)
            .attribute("empty", "")
            .attribute("removed", "value")
            .attribute("removed", (String) null)
            .build();

    assertEquals("subject", context.key());
    assertEquals(4, context.attributes().size());
    assertEquals(0.0d, context.attributes().get("score"));
    assertThrows(
        UnsupportedOperationException.class, () -> context.attributes().put("new", "value"));
  }
}
