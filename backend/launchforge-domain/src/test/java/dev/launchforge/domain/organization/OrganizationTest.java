package dev.launchforge.domain.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrganizationTest {
  private static final Instant CREATED_AT = Instant.parse("2026-08-10T12:00:00Z");

  @Test
  void rejectsNonCanonicalOrganizationSlug() {
    assertThrows(DomainRuleViolationException.class, () -> new OrganizationSlug("North Star"));
    assertThrows(DomainRuleViolationException.class, () -> new OrganizationSlug("northstar-"));
  }

  @Test
  void preservesStableIdentityAndIncrementsVersionOnRename() {
    Organization original = organization();

    Organization renamed = original.rename("Northstar Labs", 0, CREATED_AT.plusSeconds(10));

    assertEquals(original.id(), renamed.id());
    assertEquals(original.slug(), renamed.slug());
    assertEquals("Northstar Labs", renamed.name());
    assertEquals(1, renamed.version());
  }

  @Test
  void closedOrganizationCannotBeReopened() {
    Organization closed =
        organization().transitionTo(OrganizationStatus.CLOSED, 0, CREATED_AT.plusSeconds(10));

    assertThrows(
        DomainRuleViolationException.class,
        () -> closed.transitionTo(OrganizationStatus.ACTIVE, 1, CREATED_AT.plusSeconds(20)));
  }

  private static Organization organization() {
    return new Organization(
        OrganizationId.random(),
        new OrganizationSlug("northstar"),
        "Northstar",
        OrganizationStatus.TRIAL,
        0,
        CREATED_AT,
        CREATED_AT);
  }
}
