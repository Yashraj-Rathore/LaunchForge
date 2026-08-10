package dev.launchforge.application.controlplane;

@FunctionalInterface
public interface RolloutSaltGenerator {
  String generate();
}
