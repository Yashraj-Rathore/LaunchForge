import type { EvaluationDetail, JsonValue } from '@launchforge/js-core';
import { useLaunchForgeClient, useLaunchForgeVersion } from './provider.js';

export function useBooleanFlag(flagKey: string, defaultValue: boolean): boolean {
  return useBooleanFlagDetail(flagKey, defaultValue).value;
}

export function useBooleanFlagDetail(
  flagKey: string,
  defaultValue: boolean,
): EvaluationDetail<boolean> {
  const client = useSubscribedClient();
  return client.boolVariationDetail(flagKey, defaultValue);
}

export function useStringFlag(flagKey: string, defaultValue: string): string {
  return useStringFlagDetail(flagKey, defaultValue).value;
}

export function useStringFlagDetail(
  flagKey: string,
  defaultValue: string,
): EvaluationDetail<string> {
  const client = useSubscribedClient();
  return client.stringVariationDetail(flagKey, defaultValue);
}

export function useNumberFlag(flagKey: string, defaultValue: number): number {
  return useNumberFlagDetail(flagKey, defaultValue).value;
}

export function useNumberFlagDetail(
  flagKey: string,
  defaultValue: number,
): EvaluationDetail<number> {
  const client = useSubscribedClient();
  return client.numberVariationDetail(flagKey, defaultValue);
}

export function useJsonFlag<T extends JsonValue>(flagKey: string, defaultValue: T): JsonValue | T {
  return useJsonFlagDetail(flagKey, defaultValue).value;
}

export function useJsonFlagDetail<T extends JsonValue>(
  flagKey: string,
  defaultValue: T,
): EvaluationDetail<JsonValue | T> {
  const client = useSubscribedClient();
  return client.jsonVariationDetail(flagKey, defaultValue);
}

function useSubscribedClient() {
  const client = useLaunchForgeClient();
  useLaunchForgeVersion();
  return client;
}
