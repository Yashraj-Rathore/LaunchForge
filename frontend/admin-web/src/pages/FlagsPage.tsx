import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useOutletContext, useParams } from 'react-router-dom';
import { api, ApiError } from '../api';
import { canEdit, canPublish, MutationError, queryClient } from '../App';
import {
  createFlagPayload,
  editableDraft,
  editableVariations,
  moveItem,
  operatorsByType,
  serializeDraft,
  updateFlagPayload,
} from '../forms';
import type { EditableDraft, EditableVariation } from '../forms';
import type {
  AttributeType,
  Condition,
  FlagType,
  Operator,
  Rule,
  SimulationResult,
} from '../types';
import type { WorkspaceContext } from '../workspace';

const EMPTY_VARIATIONS: readonly EditableVariation[] = [
  { key: 'off', name: 'Off', rawValue: 'false' },
  { key: 'on', name: 'On', rawValue: 'true' },
];

export function FlagsPage() {
  const workspace = useOutletContext<WorkspaceContext>();
  const flags = useQuery({
    queryKey: ['flags', workspace.project.id],
    queryFn: () => api.flags(workspace.project.id),
  });
  const [creating, setCreating] = useState(false);
  if (flags.isPending)
    return (
      <PageState title="Loading flags" detail="Reading the server-scoped project configuration…" />
    );
  if (flags.isError)
    return <PageError title="Flags unavailable" error={flags.error} retry={flags.refetch} />;
  const active = flags.data.filter((flag) => flag.status === 'ACTIVE');
  return (
    <>
      <PageHeading
        eyebrow="Configuration"
        title="Feature flags"
        detail="Draft changes stay private until you publish an immutable environment revision."
        action={
          canEdit(workspace.organization.role) ? (
            <button className="button primary" onClick={() => setCreating((value) => !value)}>
              {creating ? 'Close editor' : 'Create flag'}
            </button>
          ) : undefined
        }
      />
      {creating && <CreateFlagPanel workspace={workspace} onCreated={() => setCreating(false)} />}
      {active.length === 0 ? (
        <PageState
          title="No active flags"
          detail={
            canEdit(workspace.organization.role)
              ? 'Create a typed flag to start a safe draft.'
              : 'This project has no active flags. Your role is read-only.'
          }
        />
      ) : (
        <div className="flag-grid">
          {active.map((flag) => (
            <Link className="flag-card" key={flag.id} to={flag.id}>
              <div>
                <code>{flag.key}</code>
                <span className="type-chip">{flag.type}</span>
              </div>
              <h2>{flag.name}</h2>
              <p>
                {flag.variations.length} typed variations ·{' '}
                {flag.clientVisible ? 'Browser visible' : 'Server only'}
              </p>
              <span className="card-link">Open {workspace.environment.name} draft →</span>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}

function CreateFlagPanel({
  workspace,
  onCreated,
}: {
  readonly workspace: WorkspaceContext;
  readonly onCreated: () => void;
}) {
  const [key, setKey] = useState('new-feature');
  const [name, setName] = useState('New feature');
  const [type, setType] = useState<FlagType>('BOOLEAN');
  const [clientVisible, setClientVisible] = useState(false);
  const [variations, setVariations] = useState<readonly EditableVariation[]>(EMPTY_VARIATIONS);
  const [validation, setValidation] = useState<string | null>(null);
  const mutation = useMutation({
    mutationFn: () =>
      api.createFlag(
        workspace.project.id,
        createFlagPayload({ key, name, type, clientVisible, variations }),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['flags', workspace.project.id] });
      onCreated();
    },
  });
  function submit() {
    setValidation(null);
    try {
      createFlagPayload({ key, name, type, clientVisible, variations });
      mutation.mutate();
    } catch (error) {
      setValidation(error instanceof Error ? error.message : 'Flag input is invalid.');
    }
  }
  return (
    <section className="panel editor-panel" aria-labelledby="create-flag-title">
      <div className="panel-heading">
        <div>
          <span className="section-kicker">New typed flag</span>
          <h2 id="create-flag-title">Identity and variations</h2>
        </div>
        <span className="draft-chip">DRAFT ONLY</span>
      </div>
      <div className="form-grid columns-2">
        <label>
          Flag key
          <input value={key} onChange={(event) => setKey(event.target.value)} />
        </label>
        <label>
          Display name
          <input value={name} onChange={(event) => setName(event.target.value)} />
        </label>
        <label>
          Type
          <select
            value={type}
            onChange={(event) => {
              const next = event.target.value as FlagType;
              setType(next);
              setVariations(defaultVariations(next));
            }}
          >
            <option>BOOLEAN</option>
            <option>STRING</option>
            <option>NUMBER</option>
            <option>JSON</option>
          </select>
        </label>
        <label className="check-row">
          <input
            checked={clientVisible}
            onChange={(event) => setClientVisible(event.target.checked)}
            type="checkbox"
          />
          Browser-visible projection
        </label>
      </div>
      <VariationFields type={type} values={variations} onChange={setVariations} allowKeys />
      <div className="button-row">
        <button
          className="button primary"
          disabled={mutation.isPending}
          onClick={submit}
          type="button"
        >
          Create safe draft
        </button>
      </div>
      {validation && (
        <p className="inline-error" role="alert">
          {validation}
        </p>
      )}
      <MutationError error={mutation.error} />
    </section>
  );
}

export function FlagEditorPage() {
  const workspace = useOutletContext<WorkspaceContext>();
  const { flagId = '' } = useParams();
  const flagQuery = useQuery({ queryKey: ['flag', flagId], queryFn: () => api.flag(flagId) });
  const draftQuery = useQuery({
    queryKey: ['draft', flagId, workspace.environment.id],
    queryFn: () => api.draft(flagId, workspace.environment.id),
  });
  if (flagQuery.isPending || draftQuery.isPending)
    return (
      <PageState
        title="Loading flag draft"
        detail="Reading metadata and the selected environment draft…"
      />
    );
  const error = flagQuery.error ?? draftQuery.error;
  if (error !== null)
    return (
      <PageError
        title="Flag draft unavailable"
        error={error}
        retry={() => {
          void flagQuery.refetch();
          void draftQuery.refetch();
        }}
      />
    );
  if (flagQuery.data === undefined || draftQuery.data === undefined) {
    return <PageState title="Loading flag draft" detail="Waiting for complete server data…" />;
  }
  return (
    <FlagEditor
      key={`${flagQuery.data.value.version}:${draftQuery.data.value.version}`}
      workspace={workspace}
      flagVersion={flagQuery.data}
      draftVersion={draftQuery.data}
    />
  );
}

function FlagEditor({
  workspace,
  flagVersion,
  draftVersion,
}: {
  readonly workspace: WorkspaceContext;
  readonly flagVersion: Awaited<ReturnType<typeof api.flag>>;
  readonly draftVersion: Awaited<ReturnType<typeof api.draft>>;
}) {
  const flag = flagVersion.value;
  const draft = draftVersion.value;
  const editable = canEdit(workspace.organization.role);
  const [name, setName] = useState(flag.name);
  const [variations, setVariations] = useState<readonly EditableVariation[]>(
    editableVariations(flag.variations),
  );
  const [draftState, setDraftState] = useState<EditableDraft>(() => editableDraft(draft));
  const [validation, setValidation] = useState<string | null>(null);
  const [publishOpen, setPublishOpen] = useState(false);
  const metadataMutation = useMutation({
    mutationFn: () =>
      api.updateFlag(
        flag.id,
        flagVersion.etag,
        updateFlagPayload(name, flag.status, flag.type, variations),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['flag', flag.id] });
      await queryClient.invalidateQueries({ queryKey: ['flags', workspace.project.id] });
    },
  });
  const draftMutation = useMutation({
    mutationFn: () =>
      api.updateDraft(
        flag.id,
        workspace.environment.id,
        draftVersion.etag,
        serializeDraft(draftState),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ['draft', flag.id, workspace.environment.id],
      });
    },
  });
  function saveMetadata() {
    setValidation(null);
    try {
      updateFlagPayload(name, flag.status, flag.type, variations);
      metadataMutation.mutate();
    } catch (error) {
      setValidation(message(error));
    }
  }
  function saveDraft() {
    setValidation(null);
    try {
      serializeDraft(draftState);
      draftMutation.mutate();
    } catch (error) {
      setValidation(message(error));
    }
  }
  return (
    <>
      <PageHeading
        eyebrow={`${workspace.project.key} / ${workspace.environment.key}`}
        title={flag.name}
        detail={`Editing ${flag.key}. Runtime SDKs still see published revision ${workspace.environment.currentRevision}.`}
        action={
          <Link className="button ghost" to="..">
            Back to flags
          </Link>
        }
      />
      <div className="draft-published-strip">
        <div>
          <span>Working state</span>
          <strong>Unpublished draft v{draft.version}</strong>
        </div>
        <div>
          <span>Runtime state</span>
          <strong>Published revision {workspace.environment.currentRevision}</strong>
        </div>
      </div>
      {!editable && (
        <div className="notice denied" role="status">
          <strong>Read-only role</strong>
          <span>
            You can inspect configuration and simulate subjects, but mutations remain server-denied.
          </span>
        </div>
      )}
      <section className="panel editor-panel">
        <div className="panel-heading">
          <div>
            <span className="section-kicker">Flag definition</span>
            <h2>Metadata and typed variations</h2>
          </div>
          <span className="type-chip">{flag.type}</span>
        </div>
        <label>
          Display name
          <input
            disabled={!editable}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        <VariationFields
          editable={editable}
          type={flag.type}
          values={variations}
          onChange={setVariations}
        />
        {editable && (
          <button
            className="button secondary"
            disabled={metadataMutation.isPending}
            onClick={saveMetadata}
            type="button"
          >
            Save metadata
          </button>
        )}
        <MutationError error={metadataMutation.error} />
      </section>
      <section className="panel editor-panel">
        <div className="panel-heading">
          <div>
            <span className="section-kicker">Environment behavior</span>
            <h2>State and fallthrough</h2>
          </div>
          <span className="draft-chip">DRAFT</span>
        </div>
        <div className="form-grid columns-3">
          <label className="check-row">
            <input
              disabled={!editable}
              checked={draftState.enabled}
              onChange={(event) => setDraftState({ ...draftState, enabled: event.target.checked })}
              type="checkbox"
            />
            Flag enabled
          </label>
          <label>
            Default variation
            <select
              disabled={!editable}
              value={draftState.fallthroughVariationId}
              onChange={(event) =>
                setDraftState({ ...draftState, fallthroughVariationId: event.target.value })
              }
            >
              {variationOptions(flag.variations)}
            </select>
          </label>
          <label>
            Off variation
            <select
              disabled={!editable}
              value={draftState.offVariationId}
              onChange={(event) =>
                setDraftState({ ...draftState, offVariationId: event.target.value })
              }
            >
              {variationOptions(flag.variations)}
            </select>
          </label>
        </div>
        <label>
          Change summary
          <textarea
            disabled={!editable}
            maxLength={500}
            value={draftState.changeSummary}
            onChange={(event) =>
              setDraftState({ ...draftState, changeSummary: event.target.value })
            }
          />
        </label>
      </section>
      <RuleBuilder
        editable={editable}
        rules={draftState.rules}
        variations={flag.variations}
        onChange={(rules) => setDraftState({ ...draftState, rules })}
      />
      <RolloutEditor
        editable={editable}
        salt={draft.rolloutSalt}
        state={draftState}
        variations={flag.variations}
        onChange={setDraftState}
        flagId={flag.id}
        environmentId={workspace.environment.id}
        etag={draftVersion.etag}
      />
      <Simulator
        environmentId={workspace.environment.id}
        flagKey={flag.key}
        type={flag.type}
        defaultValue={
          flag.variations.find((variation) => variation.id === draftState.fallthroughVariationId)
            ?.value ?? false
        }
      />
      {validation && (
        <p className="inline-error" role="alert">
          {validation}
        </p>
      )}
      <MutationError error={draftMutation.error} />
      {editable && (
        <div className="sticky-actions">
          <button
            className="button secondary"
            disabled={draftMutation.isPending}
            onClick={saveDraft}
            type="button"
          >
            Save draft
          </button>
          <button className="button primary" onClick={() => setPublishOpen(true)} type="button">
            Review and publish
          </button>
        </div>
      )}
      {publishOpen && (
        <PublishReview workspace={workspace} flagCount={1} onClose={() => setPublishOpen(false)} />
      )}
    </>
  );
}

function VariationFields({
  type,
  values,
  onChange,
  allowKeys = false,
  editable = true,
}: {
  readonly type: FlagType;
  readonly values: readonly EditableVariation[];
  readonly onChange: (values: readonly EditableVariation[]) => void;
  readonly allowKeys?: boolean;
  readonly editable?: boolean;
}) {
  return (
    <div className="variation-list">
      <h3>Variations</h3>
      {values.map((variation, index) => (
        <div className="variation-row" key={variation.id ?? `${variation.key}-${index}`}>
          <label>
            Key
            <input
              disabled={!editable || !allowKeys}
              value={variation.key}
              onChange={(event) =>
                onChange(replace(values, index, { ...variation, key: event.target.value }))
              }
            />
          </label>
          <label>
            Label
            <input
              disabled={!editable}
              value={variation.name}
              onChange={(event) =>
                onChange(replace(values, index, { ...variation, name: event.target.value }))
              }
            />
          </label>
          <label>
            Typed value
            {type === 'BOOLEAN' ? (
              <select
                disabled={!editable}
                value={variation.rawValue}
                onChange={(event) =>
                  onChange(replace(values, index, { ...variation, rawValue: event.target.value }))
                }
              >
                <option value="false">false</option>
                <option value="true">true</option>
              </select>
            ) : (
              <input
                disabled={!editable}
                value={variation.rawValue}
                onChange={(event) =>
                  onChange(replace(values, index, { ...variation, rawValue: event.target.value }))
                }
              />
            )}
          </label>
        </div>
      ))}
    </div>
  );
}

function RuleBuilder({
  editable,
  rules,
  variations,
  onChange,
}: {
  readonly editable: boolean;
  readonly rules: readonly Rule[];
  readonly variations: readonly { id: string; name: string }[];
  readonly onChange: (rules: readonly Rule[]) => void;
}) {
  function addRule() {
    onChange([
      ...rules,
      {
        id: crypto.randomUUID(),
        name: `Rule ${rules.length + 1}`,
        conditions: [
          { attribute: 'country', attributeType: 'STRING', operator: 'EQUALS', values: ['CA'] },
        ],
        variationId: variations[0]?.id ?? '',
      },
    ]);
  }
  return (
    <section className="panel editor-panel">
      <div className="panel-heading">
        <div>
          <span className="section-kicker">First match wins</span>
          <h2>Ordered targeting rules</h2>
        </div>
        {editable && (
          <button className="button ghost" onClick={addRule} type="button">
            Add rule
          </button>
        )}
      </div>
      <p className="help">
        Conditions within a rule are ANDed. Reorder controls are keyboard-accessible and
        serialization preserves this exact order.
      </p>
      {rules.length === 0 ? (
        <div className="empty-inline">
          No targeting rules. Evaluation continues to rollout or fallthrough.
        </div>
      ) : (
        <div className="rule-list">
          {rules.map((rule, index) => (
            <article className="rule-card" key={rule.id}>
              <div className="rule-header">
                <span className="rule-order">{String(index + 1).padStart(2, '0')}</span>
                <label>
                  Rule name
                  <input
                    disabled={!editable}
                    value={rule.name}
                    onChange={(event) =>
                      onChange(replace(rules, index, { ...rule, name: event.target.value }))
                    }
                  />
                </label>
                <div className="icon-actions">
                  <button
                    aria-label={`Move ${rule.name} up`}
                    disabled={!editable || index === 0}
                    onClick={() => onChange(moveItem(rules, index, -1))}
                    type="button"
                  >
                    ↑
                  </button>
                  <button
                    aria-label={`Move ${rule.name} down`}
                    disabled={!editable || index === rules.length - 1}
                    onClick={() => onChange(moveItem(rules, index, 1))}
                    type="button"
                  >
                    ↓
                  </button>
                  <button
                    aria-label={`Remove ${rule.name}`}
                    disabled={!editable}
                    onClick={() => onChange(rules.filter((_, candidate) => candidate !== index))}
                    type="button"
                  >
                    ×
                  </button>
                </div>
              </div>
              <ConditionEditor
                editable={editable}
                conditions={rule.conditions}
                onChange={(conditions) => onChange(replace(rules, index, { ...rule, conditions }))}
              />
              <label>
                Serve variation
                <select
                  disabled={!editable}
                  value={rule.variationId}
                  onChange={(event) =>
                    onChange(replace(rules, index, { ...rule, variationId: event.target.value }))
                  }
                >
                  {variationOptions(variations)}
                </select>
              </label>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function ConditionEditor({
  editable,
  conditions,
  onChange,
}: {
  readonly editable: boolean;
  readonly conditions: readonly Condition[];
  readonly onChange: (conditions: readonly Condition[]) => void;
}) {
  function addCondition() {
    onChange([
      ...conditions,
      { attribute: 'plan', attributeType: 'STRING', operator: 'EQUALS', values: ['pro'] },
    ]);
  }
  return (
    <div className="condition-list">
      {conditions.map((condition, index) => {
        const zero = ['EXISTS', 'NOT_EXISTS', 'IS_TRUE', 'IS_FALSE'].includes(condition.operator);
        return (
          <div className="condition-row" key={`${condition.attribute}-${index}`}>
            <span className="and-chip">{index === 0 ? 'WHEN' : 'AND'}</span>
            <label>
              Attribute
              <input
                disabled={!editable}
                value={condition.attribute}
                onChange={(event) =>
                  onChange(
                    replace(conditions, index, { ...condition, attribute: event.target.value }),
                  )
                }
              />
            </label>
            <label>
              Type
              <select
                disabled={!editable}
                value={condition.attributeType}
                onChange={(event) => {
                  const attributeType = event.target.value as AttributeType;
                  const operator = operatorsByType[attributeType][0] ?? 'EXISTS';
                  onChange(
                    replace(conditions, index, {
                      ...condition,
                      attributeType,
                      operator,
                      values: defaultConditionValues(operator),
                    }),
                  );
                }}
              >
                <option>STRING</option>
                <option>NUMBER</option>
                <option>BOOLEAN</option>
                <option>SEMVER</option>
              </select>
            </label>
            <label>
              Operator
              <select
                disabled={!editable}
                value={condition.operator}
                onChange={(event) => {
                  const operator = event.target.value as Operator;
                  onChange(
                    replace(conditions, index, {
                      ...condition,
                      operator,
                      values: defaultConditionValues(operator, condition.values),
                    }),
                  );
                }}
              >
                {operatorsByType[condition.attributeType].map((operator) => (
                  <option key={operator}>{operator}</option>
                ))}
              </select>
            </label>
            {!zero && (
              <label>
                Value
                <input
                  disabled={!editable}
                  value={condition.values.join(', ')}
                  onChange={(event) =>
                    onChange(
                      replace(conditions, index, {
                        ...condition,
                        values: ['IN', 'NOT_IN'].includes(condition.operator)
                          ? event.target.value
                              .split(',')
                              .map((value) => value.trim())
                              .filter(Boolean)
                          : condition.operator === 'BETWEEN_INCLUSIVE'
                            ? event.target.value
                                .split(',')
                                .map((value) => value.trim())
                                .slice(0, 2)
                            : [event.target.value],
                      }),
                    )
                  }
                />
              </label>
            )}
            <div className="icon-actions">
              <button
                aria-label={`Move condition ${index + 1} up`}
                disabled={!editable || index === 0}
                onClick={() => onChange(moveItem(conditions, index, -1))}
                type="button"
              >
                ↑
              </button>
              <button
                aria-label={`Move condition ${index + 1} down`}
                disabled={!editable || index === conditions.length - 1}
                onClick={() => onChange(moveItem(conditions, index, 1))}
                type="button"
              >
                ↓
              </button>
              <button
                aria-label={`Remove condition ${index + 1}`}
                disabled={!editable || conditions.length === 1}
                onClick={() => onChange(conditions.filter((_, candidate) => candidate !== index))}
                type="button"
              >
                ×
              </button>
            </div>
          </div>
        );
      })}
      {editable && (
        <button className="text-button" onClick={addCondition} type="button">
          + Add AND condition
        </button>
      )}
    </div>
  );
}

function RolloutEditor({
  editable,
  salt,
  state,
  variations,
  onChange,
  flagId,
  environmentId,
  etag,
}: {
  readonly editable: boolean;
  readonly salt: string;
  readonly state: EditableDraft;
  readonly variations: readonly { id: string; name: string }[];
  readonly onChange: (state: EditableDraft) => void;
  readonly flagId: string;
  readonly environmentId: string;
  readonly etag: string;
}) {
  const total = state.allocations.reduce((sum, allocation) => sum + allocation.weight, 0);
  const [reason, setReason] = useState('Deliberately reshuffle the rollout cohort');
  const reseed = useMutation({
    mutationFn: () => api.reseed(flagId, environmentId, etag, reason),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['draft', flagId, environmentId] });
    },
  });
  useEffect(() => {
    if (state.rolloutEnabled && state.allocations.length === 0) {
      const each = Math.floor(100_000 / variations.length);
      onChange({
        ...state,
        allocations: variations.map((variation, index) => ({
          variationId: variation.id,
          weight: index === variations.length - 1 ? 100_000 - each * index : each,
        })),
      });
    }
  }, [state, variations, onChange]);
  return (
    <section className="panel editor-panel">
      <div className="panel-heading">
        <div>
          <span className="section-kicker">Stable 100,000 buckets</span>
          <h2>Percentage rollout</h2>
        </div>
        <label className="switch-label">
          <input
            disabled={!editable}
            checked={state.rolloutEnabled}
            onChange={(event) => onChange({ ...state, rolloutEnabled: event.target.checked })}
            type="checkbox"
          />
          Enable rollout
        </label>
      </div>
      <div className="salt-line">
        <span>Stable salt</span>
        <code>{salt}</code>
      </div>
      {state.rolloutEnabled && (
        <>
          <label>
            Subject attribute
            <input
              disabled={!editable}
              value={state.subjectAttribute}
              onChange={(event) => onChange({ ...state, subjectAttribute: event.target.value })}
            />
          </label>
          <div className="allocation-list">
            {state.allocations.map((allocation, index) => (
              <label key={allocation.variationId}>
                {variations.find((variation) => variation.id === allocation.variationId)?.name ??
                  allocation.variationId}
                <div className="percentage-input">
                  <input
                    disabled={!editable}
                    min={1}
                    max={100000}
                    type="number"
                    value={allocation.weight}
                    onChange={(event) =>
                      onChange({
                        ...state,
                        allocations: replace(state.allocations, index, {
                          ...allocation,
                          weight: Number(event.target.value),
                        }),
                      })
                    }
                  />
                  <span>{(allocation.weight / 1000).toFixed(3)}%</span>
                </div>
              </label>
            ))}
          </div>
          <div
            className={`allocation-total ${total === 100_000 ? 'valid' : 'invalid'}`}
            role="status"
          >
            Allocation total <strong>{(total / 1000).toFixed(3)}%</strong> / 100.000%
          </div>
        </>
      )}
      <details className="danger-details">
        <summary>Re-randomize cohort</summary>
        <p>
          Changing the salt reshuffles every subject. This action is separate, audited, and requires
          a reason.
        </p>
        <label>
          Reason
          <input
            disabled={!editable}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>
        <button
          className="button danger"
          disabled={!editable || reseed.isPending}
          onClick={() => reseed.mutate()}
          type="button"
        >
          Generate new salt
        </button>
        <MutationError error={reseed.error} />
      </details>
    </section>
  );
}

function Simulator({
  environmentId,
  flagKey,
  type,
  defaultValue,
}: {
  readonly environmentId: string;
  readonly flagKey: string;
  readonly type: FlagType;
  readonly defaultValue: unknown;
}) {
  const [key, setKey] = useState('canada-pro-user');
  const [attributes, setAttributes] = useState('{"country":"CA","plan":"pro"}');
  const [validation, setValidation] = useState<string | null>(null);
  const [result, setResult] = useState<SimulationResult | null>(null);
  const mutation = useMutation({
    mutationFn: (context: Record<string, unknown>) =>
      api.simulate(environmentId, {
        flagKey,
        type,
        defaultValue,
        context: { key, attributes: context },
      }),
    onSuccess: setResult,
  });
  function run() {
    setValidation(null);
    try {
      const parsed = JSON.parse(attributes) as unknown;
      if (parsed === null || Array.isArray(parsed) || typeof parsed !== 'object')
        throw new Error('Attributes must be a JSON object.');
      mutation.mutate(parsed as Record<string, unknown>);
    } catch (error) {
      setValidation(message(error));
    }
  }
  return (
    <section className="panel simulator">
      <div>
        <span className="section-kicker">Server-backed deterministic preview</span>
        <h2>Evaluate this draft</h2>
        <p>
          The simulator invokes the same pure Java evaluator and does not persist or log this
          context.
        </p>
        <label>
          Context key
          <input value={key} onChange={(event) => setKey(event.target.value)} />
        </label>
        <label>
          Scalar attributes (JSON object)
          <textarea value={attributes} onChange={(event) => setAttributes(event.target.value)} />
        </label>
        <button
          className="button secondary"
          disabled={mutation.isPending}
          onClick={run}
          type="button"
        >
          Run deterministic evaluation
        </button>
        {validation && (
          <p className="inline-error" role="alert">
            {validation}
          </p>
        )}
        <MutationError error={mutation.error} />
      </div>
      {result ? (
        <dl className="result-card" aria-live="polite">
          <div>
            <dt>Variation</dt>
            <dd>{result.variationId ?? 'caller default'}</dd>
          </div>
          <div>
            <dt>Reason</dt>
            <dd>{result.reason}</dd>
          </div>
          <div>
            <dt>Value</dt>
            <dd>
              <code>{JSON.stringify(result.value)}</code>
            </dd>
          </div>
          <div>
            <dt>Bucket</dt>
            <dd>{result.rolloutBucket ?? 'not used'}</dd>
          </div>
          <div>
            <dt>Configuration</dt>
            <dd>Draft candidate {result.candidateRevision}</dd>
          </div>
          <p>
            This is a stable result for this exact context—not a prediction of future random
            outcomes.
          </p>
        </dl>
      ) : (
        <div className="result-placeholder">
          Enter a fictional subject to inspect the exact draft outcome.
        </div>
      )}
    </section>
  );
}

function PublishReview({
  workspace,
  flagCount,
  onClose,
}: {
  readonly workspace: WorkspaceContext;
  readonly flagCount: number;
  readonly onClose: () => void;
}) {
  const production = workspace.environment.kind === 'PRODUCTION';
  const authorized = canPublish(workspace.organization.role, workspace.environment);
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [published, setPublished] = useState<number | null>(null);
  const mutation = useMutation({
    mutationFn: () =>
      api.publish(
        workspace.environment.id,
        String(workspace.environment.version),
        reason.trim() || null,
      ),
    onSuccess: async (revision) => {
      setPublished(revision.revision);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['environments', workspace.project.id] }),
        queryClient.invalidateQueries({ queryKey: ['revisions', workspace.environment.id] }),
      ]);
    },
    onError: async () => {
      await queryClient.invalidateQueries({ queryKey: ['environments', workspace.project.id] });
    },
  });
  return (
    <div className="modal-backdrop">
      <section aria-labelledby="publish-title" aria-modal="true" className="modal" role="dialog">
        <div className="panel-heading">
          <div>
            <span className="section-kicker">Review changes</span>
            <h2 id="publish-title">Publish to {workspace.environment.name}</h2>
          </div>
          <button aria-label="Close publish review" onClick={onClose} type="button">
            ×
          </button>
        </div>
        <div className={`impact-card ${production ? 'production' : ''}`}>
          <strong>
            {workspace.project.name} / {workspace.environment.name}
          </strong>
          <span>{flagCount} flag draft reviewed</span>
          <span>Creates immutable revision {workspace.environment.currentRevision + 1}</span>
        </div>
        <label>
          Change reason / ticket reference
          <textarea
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Why is this change safe to release?"
          />
        </label>
        {production && (
          <label className="check-row confirmation">
            <input
              checked={confirmed}
              onChange={(event) => setConfirmed(event.target.checked)}
              type="checkbox"
            />
            I confirm this publishes {flagCount} changed flag to Production:{' '}
            {workspace.environment.name}
          </label>
        )}
        {!authorized && (
          <p className="inline-error" role="alert">
            Your role cannot publish this environment.
          </p>
        )}
        <MutationError error={mutation.error} />
        {mutation.error && (
          <p className="help">
            The environment was reconciled from the server after this ambiguous failure. Review its
            current revision before retrying.
          </p>
        )}
        {published !== null && (
          <p className="publish-success" aria-live="assertive">
            Revision {published} is durable. Distribution remains eventually consistent.
          </p>
        )}
        <div className="button-row">
          <button className="button ghost" onClick={onClose} type="button">
            Cancel
          </button>
          <button
            className="button primary"
            disabled={
              !authorized ||
              mutation.isPending ||
              (production && (!confirmed || reason.trim().length === 0))
            }
            onClick={() => mutation.mutate()}
            type="button"
          >
            Publish immutable revision
          </button>
        </div>
      </section>
    </div>
  );
}

export function PageHeading({
  eyebrow,
  title,
  detail,
  action,
}: {
  readonly eyebrow: string;
  readonly title: string;
  readonly detail: string;
  readonly action?: React.ReactNode;
}) {
  return (
    <header className="page-heading">
      <div>
        <span className="section-kicker">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{detail}</p>
      </div>
      {action}
    </header>
  );
}
export function PageState({ title, detail }: { readonly title: string; readonly detail: string }) {
  return (
    <section className="page-state" role="status">
      <span className="section-kicker">Workspace</span>
      <h2>{title}</h2>
      <p>{detail}</p>
    </section>
  );
}
export function PageError({
  title,
  error,
  retry,
}: {
  readonly title: string;
  readonly error: Error;
  readonly retry: () => unknown;
}) {
  return (
    <section className="page-state error" role="alert">
      <span className="section-kicker">Request failed</span>
      <h2>{error instanceof ApiError && error.status === 403 ? 'Access denied' : title}</h2>
      <p>{error.message}</p>
      <button className="button secondary" onClick={() => void retry()}>
        Retry
      </button>
    </section>
  );
}

function replace<T>(values: readonly T[], index: number, value: T): T[] {
  return values.map((candidate, candidateIndex) => (candidateIndex === index ? value : candidate));
}
function variationOptions(values: readonly { id: string; name: string }[]) {
  return values.map((variation) => (
    <option key={variation.id} value={variation.id}>
      {variation.name}
    </option>
  ));
}
function defaultVariations(type: FlagType): readonly EditableVariation[] {
  const raw =
    type === 'BOOLEAN'
      ? ['false', 'true']
      : type === 'NUMBER'
        ? ['0', '1']
        : type === 'JSON'
          ? ['{}', '{"enabled":true}']
          : ['control', 'treatment'];
  return [
    { key: 'off', name: 'Off', rawValue: raw[0] ?? '' },
    { key: 'on', name: 'On', rawValue: raw[1] ?? '' },
  ];
}
function defaultConditionValues(
  operator: Operator,
  existing: readonly string[] = [''],
): readonly string[] {
  if (['EXISTS', 'NOT_EXISTS', 'IS_TRUE', 'IS_FALSE'].includes(operator)) return [];
  if (operator === 'BETWEEN_INCLUSIVE') return existing.length === 2 ? existing : ['0', '1'];
  return existing.length > 0 ? existing : [''];
}
function message(error: unknown): string {
  return error instanceof Error ? error.message : 'The form is invalid.';
}
