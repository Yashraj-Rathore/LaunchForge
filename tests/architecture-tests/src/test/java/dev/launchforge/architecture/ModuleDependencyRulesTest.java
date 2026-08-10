package dev.launchforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.launchforge", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleDependencyRulesTest {
  @ArchTest
  static final ArchRule DOMAIN_HAS_NO_FRAMEWORK_DEPENDENCIES =
      noClasses()
          .that()
          .resideInAPackage("dev.launchforge.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "jakarta.servlet..",
              "org.apache.kafka..",
              "org.springframework.data.redis..");

  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage("dev.launchforge.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.launchforge.infrastructure..",
              "dev.launchforge.controlapi..",
              "dev.launchforge.configedge..",
              "dev.launchforge.eventworker..");

  @ArchTest
  static final ArchRule CONTRACTS_DO_NOT_DEPEND_ON_SERVER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("dev.launchforge.contracts..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.launchforge.domain..",
              "dev.launchforge.application..",
              "dev.launchforge.infrastructure..",
              "dev.launchforge.controlapi..",
              "dev.launchforge.configedge..",
              "dev.launchforge.eventworker..");

  @ArchTest
  static final ArchRule CONFIG_EDGE_DOES_NOT_DEPEND_ON_MANAGEMENT_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("dev.launchforge.configedge..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.launchforge.controlapi..",
              "dev.launchforge.application..",
              "dev.launchforge.domain..");

  @ArchTest
  static final ArchRule JAVA_SDK_IS_INDEPENDENT_OF_SERVER_AND_SPRING_CODE =
      noClasses()
          .that()
          .resideInAPackage("dev.launchforge.sdk..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "dev.launchforge.domain..",
              "dev.launchforge.application..",
              "dev.launchforge.contracts..",
              "dev.launchforge.infrastructure..",
              "dev.launchforge.controlapi..",
              "dev.launchforge.configedge..",
              "dev.launchforge.eventworker..",
              "org.springframework..");

  @ArchTest
  static final ArchRule SERVER_MODULES_DO_NOT_DEPEND_ON_JAVA_SDK =
      noClasses()
          .that()
          .resideInAnyPackage(
              "dev.launchforge.domain..",
              "dev.launchforge.application..",
              "dev.launchforge.contracts..",
              "dev.launchforge.infrastructure..",
              "dev.launchforge.controlapi..",
              "dev.launchforge.configedge..",
              "dev.launchforge.eventworker..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("dev.launchforge.sdk..");
}
