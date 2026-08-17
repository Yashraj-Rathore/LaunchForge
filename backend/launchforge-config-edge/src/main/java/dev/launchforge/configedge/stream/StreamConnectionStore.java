package dev.launchforge.configedge.stream;

import java.util.UUID;

interface StreamConnectionStore {
  Result acquire(UUID connectionId, UUID keyId);

  Result renew(UUID connectionId, UUID keyId);

  void release(UUID connectionId, UUID keyId);

  enum Result {
    ACQUIRED,
    REJECTED,
    UNAVAILABLE
  }
}
