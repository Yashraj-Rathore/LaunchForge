package dev.launchforge.infrastructure.organization;

import dev.launchforge.application.organization.UnitOfWork;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public final class SpringTransactionUnitOfWork implements UnitOfWork {
  private final TransactionTemplate transactionTemplate;

  public SpringTransactionUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public <T> T required(Supplier<T> work) {
    Objects.requireNonNull(work, "work");
    return transactionTemplate.execute(status -> work.get());
  }
}
