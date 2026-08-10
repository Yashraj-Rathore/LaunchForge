package dev.launchforge.application.organization;

import java.util.function.Supplier;

public interface UnitOfWork {
  <T> T required(Supplier<T> work);
}
