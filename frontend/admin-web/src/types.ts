export type Role = 'OWNER' | 'ADMIN' | 'DEVELOPER' | 'VIEWER';
export type FlagType = 'BOOLEAN' | 'STRING' | 'NUMBER' | 'JSON';
export type AttributeType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'SEMVER';
export type Operator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'STARTS_WITH'
  | 'ENDS_WITH'
  | 'CONTAINS'
  | 'EQ'
  | 'NE'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'BETWEEN_INCLUSIVE'
  | 'IS_TRUE'
  | 'IS_FALSE'
  | 'SEMVER_EQ'
  | 'SEMVER_GT'
  | 'SEMVER_GTE'
  | 'SEMVER_LT'
  | 'SEMVER_LTE'
  | 'EXISTS'
  | 'NOT_EXISTS';

export interface Organization {
  readonly id: string;
  readonly slug: string;
  readonly name: string;
  readonly role: Role;
}

export interface Session {
  readonly subject: string;
  readonly displayName: string;
  readonly organizations: readonly Organization[];
}

export interface Project {
  readonly id: string;
  readonly organizationId: string;
  readonly key: string;
  readonly name: string;
  readonly description: string | null;
  readonly status: 'ACTIVE' | 'ARCHIVED';
  readonly version: number;
}

export interface Environment {
  readonly id: string;
  readonly projectId: string;
  readonly key: string;
  readonly name: string;
  readonly kind: 'DEVELOPMENT' | 'STAGING' | 'PRODUCTION' | 'CUSTOM';
  readonly status: 'ACTIVE' | 'ARCHIVED';
  readonly currentRevision: number;
  readonly version: number;
}

export interface Variation {
  readonly id: string;
  readonly key: string;
  readonly name: string;
  readonly value: unknown;
}

export interface Flag {
  readonly id: string;
  readonly projectId: string;
  readonly key: string;
  readonly name: string;
  readonly type: FlagType;
  readonly clientVisible: boolean;
  readonly status: 'ACTIVE' | 'ARCHIVED';
  readonly version: number;
  readonly variations: readonly Variation[];
}

export interface Condition {
  readonly attribute: string;
  readonly attributeType: AttributeType;
  readonly operator: Operator;
  readonly values: readonly string[];
}

export interface Rule {
  readonly id: string;
  readonly name: string;
  readonly conditions: readonly Condition[];
  readonly variationId: string;
}

export interface Allocation {
  readonly variationId: string;
  readonly weight: number;
}

export interface Draft {
  readonly flagId: string;
  readonly environmentId: string;
  readonly enabled: boolean;
  readonly fallthroughVariationId: string;
  readonly offVariationId: string;
  readonly rolloutSalt: string;
  readonly rules: readonly Rule[];
  readonly rollout: {
    readonly subjectAttribute: string;
    readonly allocations: readonly Allocation[];
  } | null;
  readonly changeSummary: string;
  readonly version: number;
}

export interface Revision {
  readonly environmentId: string;
  readonly revision: number;
  readonly sourceRevision: number | null;
  readonly checksum: string;
  readonly snapshot: string | null;
  readonly reason: string | null;
  readonly actorSubject: string;
  readonly createdAt: string;
}

export interface RevisionDiff {
  readonly addedFlagKeys: readonly string[];
  readonly removedFlagKeys: readonly string[];
  readonly changedFlagKeys: readonly string[];
}

export interface ServerKey {
  readonly id: string;
  readonly environmentId: string;
  readonly name: string;
  readonly fingerprint: string;
  readonly status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
  readonly expiresAt: string | null;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
  readonly revokedAt: string | null;
  readonly rotatedFromId: string | null;
}

export interface BrowserKey {
  readonly id: string;
  readonly environmentId: string;
  readonly name: string;
  readonly clientKey: string;
  readonly fingerprint: string;
  readonly allowedOrigins: readonly string[];
  readonly status: 'ACTIVE' | 'DISABLED' | 'REVOKED';
  readonly expiresAt: string | null;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
  readonly revokedAt: string | null;
}

export interface IssuedServerKey {
  readonly key: ServerKey;
  readonly secret: string;
}

export interface AuditEvent {
  readonly id: string;
  readonly projectId: string | null;
  readonly environmentId: string | null;
  readonly actor: string;
  readonly action: string;
  readonly targetType: string;
  readonly targetId: string;
  readonly summary: string | null;
  readonly reason: string | null;
  readonly fromRevision: number | null;
  readonly toRevision: number | null;
  readonly correlationId: string;
  readonly createdAt: string;
}

export interface SimulationResult {
  readonly flagKey: string;
  readonly value: unknown;
  readonly variationId: string | null;
  readonly reason: string;
  readonly matchedRuleId: string | null;
  readonly rolloutBucket: number | null;
  readonly errorKind: string | null;
  readonly currentPublishedRevision: number;
  readonly candidateRevision: number;
  readonly configuration: 'DRAFT';
}

export interface AnalyticsBucket {
  readonly bucketStart: string;
  readonly flagKey: string;
  readonly variationId: string | null;
  readonly evaluations: number;
}

export interface AnalyticsResponse {
  readonly from: string;
  readonly to: string;
  readonly bucket: 'HOUR' | 'DAY';
  readonly interpretation: string;
  readonly rows: readonly AnalyticsBucket[];
}

export interface Versioned<T> {
  readonly value: T;
  readonly etag: string;
}

export interface ProblemDetails {
  readonly detail?: string;
  readonly code?: string;
  readonly correlationId?: string;
}
