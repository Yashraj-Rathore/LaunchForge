import type { BrowserClient, BrowserClientOptions } from '@launchforge/js-browser';
import { LaunchForgeBrowserClient } from '@launchforge/js-browser';
import type { EvaluationContext } from '@launchforge/js-core';
import { createContext, useContext, useEffect, useRef, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';

const ClientContext = createContext<BrowserClient | null>(null);

export interface LaunchForgeProviderProps {
  readonly options?: BrowserClientOptions;
  readonly client?: BrowserClient;
  readonly context?: EvaluationContext;
  readonly children: ReactNode;
}

export function LaunchForgeProvider({
  options,
  client: providedClient,
  context,
  children,
}: LaunchForgeProviderProps) {
  const owned = useRef<BrowserClient | null>(null);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  if (owned.current === null && providedClient === undefined) {
    if (options === undefined) {
      throw new Error('LaunchForgeProvider requires options or a client');
    }
    owned.current = new LaunchForgeBrowserClient(options);
  }
  const client = providedClient ?? owned.current;
  if (client === null) {
    throw new Error('LaunchForgeProvider could not create a client');
  }

  useEffect(() => {
    if (context !== undefined) {
      client.setContext(context);
    }
  }, [client, context]);

  useEffect(() => {
    if (closeTimer.current !== null) {
      clearTimeout(closeTimer.current);
      closeTimer.current = null;
    }
    void client.start().catch(() => undefined);
    return () => {
      if (owned.current === client) {
        closeTimer.current = setTimeout(() => {
          if (owned.current === client) {
            client.close();
            owned.current = null;
          }
        }, 0);
      }
    };
  }, [client]);

  return <ClientContext.Provider value={client}>{children}</ClientContext.Provider>;
}

export function useLaunchForgeClient(): BrowserClient {
  const client = useContext(ClientContext);
  if (client === null) {
    throw new Error('LaunchForge hooks require LaunchForgeProvider');
  }
  return client;
}

export function useLaunchForgeVersion(): number {
  const client = useLaunchForgeClient();
  return useSyncExternalStore(client.subscribe, client.getVersion, client.getVersion);
}
