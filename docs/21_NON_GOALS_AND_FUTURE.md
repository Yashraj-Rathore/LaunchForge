# 21 - Non-Goals and Future Options

## 1. Why non-goals matter

A strong project has boundaries. LaunchForge should demonstrate judgment, not attempt to reproduce every established feature-management product.

## 2. Explicit MVP non-goals

Not required before the flagship portfolio release:

- multi-region active-active control plane;
- globally distributed database;
- arbitrary scripting in targeting rules;
- regex targeting;
- full statistical experimentation engine;
- data warehouse integrations;
- SAML/enterprise SCIM;
- mobile native SDKs;
- dozens of language SDKs;
- edge compute/WASM evaluator;
- service mesh;
- event sourcing as primary persistence;
- custom Kafka replacement;
- custom authentication provider;
- secrets management;
- billing/subscription engine;
- AI-generated feature rules;
- automatic code changes;
- Terraform cloud platform duplication.

## 3. Feature flags are not authorization

Never use a client-side or locally evaluated flag as the sole control preventing unauthorized access to protected data/action.

Applications still enforce authentication/authorization independently.

## 4. Feature flags are not secrets

Do not store:

- database passwords;
- API secrets;
- private keys;
- credentials

as flag variation values.

A separate secret manager is appropriate.

## 5. No arbitrary rule language

Avoid:

```text
eval("user.country == ...")
```

A bounded operator model is easier to:

- validate;
- secure;
- implement consistently;
- port across SDK languages;
- benchmark.

## 6. No delta protocol initially

Full snapshot fetch after revision notification is intentionally simpler.

Future deltas require:

- ordered patch semantics;
- missing patch recovery;
- version compatibility;
- atomic application;
- stronger tests.

Only add if snapshot size/traffic measurements justify it.

## 7. No premature microservices

Management server can remain a modular Spring application.

Config Edge is separated because its runtime profile is materially different.

Projector/analytics can be separate deployables when needed.

Do not split every domain noun into a service.

## 8. Future language SDKs

Potential:

- Go;
- Python;
- .NET;
- Node server;
- mobile.

Each must pass the same golden semantics and snapshot contract.

## 9. Future OpenFeature support

Consider an OpenFeature-compatible provider after core SDK works. This could improve adoption without replacing the native SDK.

Treat external standard compatibility as a separate issue with current spec verification.

## 10. Future experimentation

Potential:

- exposure events;
- goals/conversions;
- experiment assignment;
- statistical analysis;
- guardrail metrics.

Requires careful statistical design. Do not display simplistic "winner" claims without methodology.

## 11. Future approvals

Possible enterprise workflow:

```text
Developer creates change
 -> Reviewer approves
 -> Production publish
```

Useful only after simple team workflow is validated.

## 12. Future GitOps

Potential declarative flag definitions in Git with:

- validation;
- plan/diff;
- protected apply;
- audit linkage.

Do not let GitOps complicate first UI/API workflow.

## 13. Future multi-region edge

Regional Config Edge + Redis can reduce snapshot/bootstrap latency.

Need explicit design for:

- key revocation;
- revision propagation;
- region health;
- failover;
- data residency.

## 14. Future relay proxy

For browser/mobile-sensitive rules, a customer-side relay can:

- keep server-only rules off public clients;
- evaluate closer to private data;
- reduce outbound access.

Not MVP.

## 15. Future commercialization

Possible:

- hosted SaaS;
- self-hosted paid support;
- open-source SDKs;
- team plans.

Commercial decisions follow user validation, not architecture ambition.

## 16. Decision rule

Before adding a future capability, answer:

1. Does a real user need it?
2. Does it strengthen a target job skill?
3. Can it be tested properly?
4. Does it introduce operational burden?
5. Is there a simpler solution?

If the first two are both "no," do not build it.
