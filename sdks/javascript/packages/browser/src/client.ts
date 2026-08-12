import {
  createEvaluationContext,
  evaluateBoolean,
  evaluateJson,
  evaluateNumber,
  evaluateString,
  parseSnapshot,
} from '@launchforge/js-core';
import type {
  CompiledSnapshot,
  EvaluationContext,
  EvaluationDetail,
  JsonValue,
} from '@launchforge/js-core';

const CLIENT_KEY = /^lf_client_[A-Za-z0-9_-]{32}$/u;
const DEFAULT_POLL_INTERVAL_MS = 30_000;
const DEFAULT_BOOTSTRAP_TIMEOUT_MS = 5_000;
const DEFAULT_MAXIMUM_SNAPSHOT_BYTES = 1024 * 1024;

export interface BrowserClientOptions {
  readonly baseUrl: string;
  readonly clientKey: string;
  readonly initialContext: EvaluationContext;
  readonly pollIntervalMs?: number;
  readonly bootstrapTimeoutMs?: number;
  readonly maximumSnapshotBytes?: number;
  readonly streaming?: boolean;
  readonly fetcher?: typeof fetch;
  readonly random?: () => number;
}

export interface BrowserClient {
  start(): Promise<void>;
  refresh(): Promise<boolean>;
  close(): void;
  setContext(context: EvaluationContext): void;
  getContext(): EvaluationContext;
  getSnapshotRevision(): number | null;
  getVersion(): number;
  subscribe(listener: () => void): () => void;
  boolVariation(flagKey: string, defaultValue: boolean): boolean;
  boolVariationDetail(flagKey: string, defaultValue: boolean): EvaluationDetail<boolean>;
  stringVariation(flagKey: string, defaultValue: string): string;
  stringVariationDetail(flagKey: string, defaultValue: string): EvaluationDetail<string>;
  numberVariation(flagKey: string, defaultValue: number): number;
  numberVariationDetail(flagKey: string, defaultValue: number): EvaluationDetail<number>;
  jsonVariation<T extends JsonValue>(flagKey: string, defaultValue: T): JsonValue | T;
  jsonVariationDetail<T extends JsonValue>(
    flagKey: string,
    defaultValue: T,
  ): EvaluationDetail<JsonValue | T>;
}

export class LaunchForgeBrowserClient implements BrowserClient {
  private readonly baseUrl: string;
  private readonly clientKey: string;
  private readonly pollIntervalMs: number;
  private readonly bootstrapTimeoutMs: number;
  private readonly maximumSnapshotBytes: number;
  private readonly streaming: boolean;
  private readonly fetcher: typeof fetch;
  private readonly random: () => number;
  private readonly listeners = new Set<() => void>();
  private context: EvaluationContext;
  private snapshot: CompiledSnapshot | null = null;
  private etag: string | null = null;
  private version = 0;
  private closed = false;
  private started = false;
  private refreshPromise: Promise<boolean> | null = null;
  private pollTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectResolver: (() => void) | null = null;
  private streamAbort: AbortController | null = null;

  constructor(options: BrowserClientOptions) {
    this.baseUrl = requireBaseUrl(options.baseUrl);
    if (!CLIENT_KEY.test(options.clientKey)) {
      throw new Error('clientKey is not a LaunchForge browser client key');
    }
    this.clientKey = options.clientKey;
    this.context = options.initialContext;
    this.pollIntervalMs = boundedInteger(
      options.pollIntervalMs ?? DEFAULT_POLL_INTERVAL_MS,
      100,
      24 * 60 * 60 * 1000,
      'pollIntervalMs',
    );
    this.bootstrapTimeoutMs = boundedInteger(
      options.bootstrapTimeoutMs ?? DEFAULT_BOOTSTRAP_TIMEOUT_MS,
      100,
      60_000,
      'bootstrapTimeoutMs',
    );
    this.maximumSnapshotBytes = boundedInteger(
      options.maximumSnapshotBytes ?? DEFAULT_MAXIMUM_SNAPSHOT_BYTES,
      1024,
      5 * 1024 * 1024,
      'maximumSnapshotBytes',
    );
    this.streaming = options.streaming ?? true;
    this.fetcher = options.fetcher ?? globalThis.fetch.bind(globalThis);
    this.random = options.random ?? Math.random;
  }

  async start(): Promise<void> {
    if (this.started) {
      return;
    }
    if (this.closed) {
      throw new Error('LaunchForge client is closed');
    }
    this.started = true;
    try {
      await this.refresh();
    } finally {
      if (!this.closed) {
        this.schedulePoll();
        if (this.streaming) {
          void this.streamLoop();
        }
      }
    }
  }

  refresh(): Promise<boolean> {
    if (this.closed) {
      return Promise.resolve(false);
    }
    this.refreshPromise ??= this.fetchSnapshot().finally(() => {
      this.refreshPromise = null;
    });
    return this.refreshPromise;
  }

  close(): void {
    if (this.closed) {
      return;
    }
    this.closed = true;
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
      this.reconnectResolver?.();
      this.reconnectResolver = null;
    }
    this.streamAbort?.abort();
    this.streamAbort = null;
    this.listeners.clear();
  }

  setContext(context: EvaluationContext): void {
    if (this.context === context) {
      return;
    }
    this.context = context;
    this.activateChange();
  }

  getContext(): EvaluationContext {
    return this.context;
  }

  getSnapshotRevision(): number | null {
    return this.snapshot?.revision ?? null;
  }

  getVersion = (): number => this.version;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  boolVariation(flagKey: string, defaultValue: boolean): boolean {
    return this.boolVariationDetail(flagKey, defaultValue).value;
  }

  boolVariationDetail(flagKey: string, defaultValue: boolean): EvaluationDetail<boolean> {
    return evaluateBoolean(this.snapshot, flagKey, this.context, defaultValue);
  }

  stringVariation(flagKey: string, defaultValue: string): string {
    return this.stringVariationDetail(flagKey, defaultValue).value;
  }

  stringVariationDetail(flagKey: string, defaultValue: string): EvaluationDetail<string> {
    return evaluateString(this.snapshot, flagKey, this.context, defaultValue);
  }

  numberVariation(flagKey: string, defaultValue: number): number {
    return this.numberVariationDetail(flagKey, defaultValue).value;
  }

  numberVariationDetail(flagKey: string, defaultValue: number): EvaluationDetail<number> {
    return evaluateNumber(this.snapshot, flagKey, this.context, defaultValue);
  }

  jsonVariation<T extends JsonValue>(flagKey: string, defaultValue: T): JsonValue | T {
    return this.jsonVariationDetail(flagKey, defaultValue).value;
  }

  jsonVariationDetail<T extends JsonValue>(
    flagKey: string,
    defaultValue: T,
  ): EvaluationDetail<JsonValue | T> {
    return evaluateJson(this.snapshot, flagKey, this.context, defaultValue);
  }

  private async fetchSnapshot(): Promise<boolean> {
    const abort = new AbortController();
    const timeout = setTimeout(() => abort.abort(), this.bootstrapTimeoutMs);
    try {
      const headers = new Headers({ Accept: 'application/json' });
      if (this.etag !== null) {
        headers.set('If-None-Match', this.etag);
      }
      const response = await this.fetcher(this.snapshotUrl(), {
        method: 'GET',
        headers,
        credentials: 'omit',
        redirect: 'error',
        signal: abort.signal,
      });
      if (response.status === 304) {
        return false;
      }
      if (response.status !== 200) {
        throw new Error(`Snapshot request failed with status ${response.status}`);
      }
      const contentLength = Number(response.headers.get('content-length'));
      if (Number.isFinite(contentLength) && contentLength > this.maximumSnapshotBytes) {
        throw new Error('Snapshot response exceeds the configured byte limit');
      }
      const json = await response.text();
      if (new TextEncoder().encode(json).length > this.maximumSnapshotBytes) {
        throw new Error('Snapshot response exceeds the configured byte limit');
      }
      const candidate = parseSnapshot(json);
      requireMatchingHeader(response, 'X-LaunchForge-Revision', String(candidate.revision));
      requireMatchingHeader(response, 'X-LaunchForge-Checksum', candidate.checksum);
      requireMatchingHeader(
        response,
        'X-LaunchForge-Schema-Version',
        String(candidate.schemaVersion),
      );
      if (this.snapshot !== null && candidate.revision < this.snapshot.revision) {
        return false;
      }
      if (
        this.snapshot !== null &&
        candidate.revision === this.snapshot.revision &&
        candidate.checksum !== this.snapshot.checksum
      ) {
        throw new Error('Snapshot reused a revision with different content');
      }
      if (this.snapshot?.checksum === candidate.checksum) {
        this.etag = response.headers.get('etag') ?? this.etag;
        return false;
      }
      this.snapshot = candidate;
      this.etag = response.headers.get('etag');
      this.activateChange();
      return true;
    } finally {
      clearTimeout(timeout);
    }
  }

  private schedulePoll(): void {
    if (this.closed) {
      return;
    }
    const jitter = 0.8 + this.random() * 0.4;
    this.pollTimer = setTimeout(
      () => {
        this.pollTimer = null;
        void this.refresh()
          .catch(() => undefined)
          .finally(() => this.schedulePoll());
      },
      Math.round(this.pollIntervalMs * jitter),
    );
  }

  private async streamLoop(): Promise<void> {
    let failures = 0;
    while (!this.closed) {
      try {
        await this.consumeStream();
        failures = 0;
      } catch (error) {
        if (this.closed || (error instanceof DOMException && error.name === 'AbortError')) {
          return;
        }
        failures = Math.min(failures + 1, 6);
      }
      if (!this.closed) {
        const base = Math.min(30_000, 1000 * 2 ** failures);
        await this.waitForReconnect(Math.round(base * (0.8 + this.random() * 0.4)));
      }
    }
  }

  private async consumeStream(): Promise<void> {
    const abort = new AbortController();
    this.streamAbort = abort;
    const headers = new Headers({ Accept: 'text/event-stream' });
    const revision = this.getSnapshotRevision();
    if (revision !== null) {
      headers.set('Last-Event-ID', String(revision));
    }
    try {
      const response = await this.fetcher(this.streamUrl(), {
        method: 'GET',
        headers,
        credentials: 'omit',
        redirect: 'error',
        signal: abort.signal,
      });
      if (response.status !== 200 || response.body === null) {
        throw new Error(`Stream request failed with status ${response.status}`);
      }
      await consumeRevisionStream(response.body, async (revisionHint) => {
        if ((this.getSnapshotRevision() ?? 0) < revisionHint) {
          await this.refresh();
        }
      });
    } finally {
      if (this.streamAbort === abort) {
        this.streamAbort = null;
      }
    }
  }

  private waitForReconnect(milliseconds: number): Promise<void> {
    return new Promise((resolve) => {
      this.reconnectResolver = resolve;
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null;
        this.reconnectResolver = null;
        resolve();
      }, milliseconds);
    });
  }

  private activateChange(): void {
    this.version += 1;
    for (const listener of this.listeners) {
      listener();
    }
  }

  private snapshotUrl(): string {
    return `${this.baseUrl}/sdk/v1/client/${this.clientKey}/snapshot`;
  }

  private streamUrl(): string {
    return `${this.baseUrl}/sdk/v1/client/${this.clientKey}/stream`;
  }
}

export function defaultEvaluationContext(key: string): EvaluationContext {
  return createEvaluationContext(key);
}

async function consumeRevisionStream(
  stream: ReadableStream<Uint8Array>,
  onRevision: (revision: number) => Promise<void>,
): Promise<void> {
  const reader = stream.getReader();
  const decoder = new TextDecoder('utf-8', { fatal: true });
  let buffer = '';
  while (true) {
    const result = await reader.read();
    if (result.done) {
      buffer += decoder.decode();
      if (buffer.trim().length > 0) {
        await dispatchSse(buffer, onRevision);
      }
      return;
    }
    buffer += decoder.decode(result.value, { stream: true }).replaceAll('\r\n', '\n');
    if (buffer.length > 16 * 1024) {
      throw new Error('SSE event buffer exceeds 16 KiB');
    }
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const event = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      await dispatchSse(event, onRevision);
      boundary = buffer.indexOf('\n\n');
    }
  }
}

async function dispatchSse(
  eventText: string,
  onRevision: (revision: number) => Promise<void>,
): Promise<void> {
  let event = 'message';
  const data: string[] = [];
  for (const line of eventText.split('\n')) {
    if (line.startsWith(':')) {
      continue;
    }
    const separator = line.indexOf(':');
    const field = separator < 0 ? line : line.slice(0, separator);
    const value = separator < 0 ? '' : line.slice(separator + 1).replace(/^ /u, '');
    if (field === 'event') {
      event = value;
    } else if (field === 'data') {
      data.push(value);
    }
  }
  if (event !== 'revision' || data.length === 0) {
    return;
  }
  const parsed: unknown = JSON.parse(data.join('\n'));
  if (
    parsed === null ||
    typeof parsed !== 'object' ||
    !Object.hasOwn(parsed, 'revision') ||
    !Number.isSafeInteger((parsed as { readonly revision?: unknown }).revision) ||
    ((parsed as { readonly revision: number }).revision ?? 0) <= 0
  ) {
    throw new Error('Revision SSE data is invalid');
  }
  await onRevision((parsed as { readonly revision: number }).revision);
}

function requireBaseUrl(value: string): string {
  const url = new URL(value);
  if (
    !['http:', 'https:'].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash
  ) {
    throw new Error('baseUrl must be an HTTP(S) origin or path base without credentials');
  }
  return value.replace(/\/+$/u, '');
}

function boundedInteger(value: number, minimum: number, maximum: number, label: string): number {
  if (!Number.isInteger(value) || value < minimum || value > maximum) {
    throw new Error(`${label} must be between ${minimum} and ${maximum}`);
  }
  return value;
}

function requireMatchingHeader(response: Response, name: string, expected: string): void {
  const value = response.headers.get(name);
  if (value !== null && value !== expected) {
    throw new Error(`${name} does not match the snapshot body`);
  }
}
