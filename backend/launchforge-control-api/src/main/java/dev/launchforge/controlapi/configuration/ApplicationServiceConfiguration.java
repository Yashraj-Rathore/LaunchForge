package dev.launchforge.controlapi.configuration;

import dev.launchforge.application.controlplane.ControlPlaneRepository;
import dev.launchforge.application.controlplane.ControlPlaneService;
import dev.launchforge.application.controlplane.RolloutSaltGenerator;
import dev.launchforge.application.controlplane.SnapshotCodec;
import dev.launchforge.application.organization.AuditWriter;
import dev.launchforge.application.organization.MembershipAdministrationService;
import dev.launchforge.application.organization.MembershipRepository;
import dev.launchforge.application.organization.OrganizationAccessRepository;
import dev.launchforge.application.organization.OrganizationQueryService;
import dev.launchforge.application.organization.UnitOfWork;
import dev.launchforge.domain.organization.MemberManagementPolicy;
import dev.launchforge.infrastructure.controlplane.JacksonSnapshotCodec;
import dev.launchforge.infrastructure.controlplane.SecureRolloutSaltGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ApplicationServiceConfiguration {
  @Bean
  Clock systemClock() {
    return Clock.systemUTC();
  }

  @Bean
  MemberManagementPolicy memberManagementPolicy() {
    return new MemberManagementPolicy();
  }

  @Bean
  OrganizationQueryService organizationQueryService(
      OrganizationAccessRepository accessRepository, MembershipRepository membershipRepository) {
    return new OrganizationQueryService(accessRepository, membershipRepository);
  }

  @Bean
  MembershipAdministrationService membershipAdministrationService(
      OrganizationAccessRepository accessRepository,
      MembershipRepository membershipRepository,
      AuditWriter auditWriter,
      UnitOfWork unitOfWork,
      MemberManagementPolicy policy,
      Clock clock) {
    return new MembershipAdministrationService(
        accessRepository, membershipRepository, auditWriter, unitOfWork, policy, clock);
  }

  @Bean
  RolloutSaltGenerator rolloutSaltGenerator() {
    return new SecureRolloutSaltGenerator();
  }

  @Bean
  SnapshotCodec snapshotCodec(ObjectMapper objectMapper) {
    return new JacksonSnapshotCodec(objectMapper);
  }

  @Bean
  ControlPlaneService controlPlaneService(
      OrganizationAccessRepository accessRepository,
      ControlPlaneRepository repository,
      UnitOfWork unitOfWork,
      SnapshotCodec snapshotCodec,
      RolloutSaltGenerator saltGenerator,
      Clock clock) {
    return new ControlPlaneService(
        accessRepository, repository, unitOfWork, snapshotCodec, saltGenerator, clock);
  }
}
