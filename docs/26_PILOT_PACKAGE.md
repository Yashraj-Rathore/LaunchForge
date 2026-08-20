# 26 - Pilot Package

## 1. Status

This is a commercial-validation hypothesis for a private beta, not evidence of customers, revenue,
availability, or product-market fit. No payment system is required before an external team proves
repeated value.

## 2. Narrow pilot profile

Start with a small SaaS or agency team that:

- has roughly 2-30 developers and a Java/Spring-heavy backend;
- deploys multiple times per month;
- currently uses configuration files or homegrown flags for risky releases;
- wants targeting, gradual rollout, a kill switch, and an audit trail; and
- can accept a single-region beta with explicit support and recovery boundaries.

The pilot is not appropriate for regulated/global workloads requiring contractual availability,
multi-region failover, SAML/SCIM, advanced experimentation statistics, or completed disaster-
recovery evidence.

## 3. Entry checklist

- name one technical owner and one decision-maker;
- select one reversible, non-authorization feature;
- choose local/self-hosted or private hosted evaluation;
- confirm the Java, JavaScript, or React integration path;
- agree that flags do not contain secrets or replace server authorization;
- agree on fictional/synthetic data for the initial walkthrough;
- record target integration time and the current release process;
- define a rollback/kill-switch exercise before production use.

## 4. Local/self-hosted pilot

1. Run the deterministic Compose demo and the relevant SDK quick start.
2. Replace only the fictional test application with the team's reversible feature.
3. Issue environment-specific credentials; never reuse the demo values.
4. Exercise targeting, 10% rollout, 50% rollout, kill switch, and configuration rollback.
5. Stop Config Edge after bootstrap and confirm last-known-good behavior.
6. Record integration time, confusing steps, missing operators, and operational concerns.

The team owns infrastructure, HTTPS, OIDC, backups, monitoring, upgrades, and incident response.
LaunchForge documentation supplies tested containers, Helm, and runbooks but does not claim a
managed-service SLO.

## 5. Private hosted pilot

Before inviting a real team:

- use a dedicated HTTPS deployment and OIDC tenant;
- isolate pilot organization data and credentials;
- configure monitoring, alert routing, audit retention, and key-compromise response;
- complete and record PostgreSQL backup/restore evidence;
- disclose the single-region beta boundary and maintenance process;
- document support hours, escalation contact, data deletion, and pilot exit/export;
- complete appropriate privacy, terms, subprocessor, tax, and company review outside this
  engineering repository.

The repository's successful local and CI tests are engineering evidence, not proof that those
hosted operating controls have been executed.

## 6. Pricing hypotheses to test

These are interview choices, not published prices:

- **Onboarding hypothesis:** a no-cost, time-boxed technical pilot reduces adoption friction more
  than a paid setup package.
- **Simple subscription hypothesis:** a flat team tier based on projects/environments/support is
  easier to understand than per-evaluation billing.
- **Managed-service hypothesis:** teams may pay first for hosted operations and onboarding while
  keeping SDK evaluation local.
- **Self-host hypothesis:** a free self-hosted core may create trust and integration evidence, with
  paid support or hosting considered only after repeated usage.

Do not meter unobserved local evaluations. Test willingness to pay and compare alternatives before
selecting any currency amount, packaging, or contract term.

## 7. Interview and feedback questions

Ask for concrete behavior rather than compliments:

1. Walk through the last release where a kill switch would have helped.
2. How long did the first SDK integration actually take?
3. Which step felt unsafe or unclear?
4. Did the application keep expected behavior during an Edge outage?
5. Was production publish, audit, and rollback understandable?
6. Which operator, SDK, deployment, or approval was missing?
7. Would the team replace its current mechanism? Why or why not?
8. Who would own LaunchForge operationally?
9. Which pricing unit feels predictable: project, environment, seat, support, or active client?
10. What must be true before using it for a second feature?

## 8. Pilot success and stop signals

Positive evidence:

- an external developer completes integration and records the time;
- the team publishes more than one revision for a real reversible use case;
- kill switch/outage behavior is exercised and understood;
- at least one team asks to continue or integrate a second feature;
- objections and feature requests are recorded without overstating adoption.

Stop or narrow the pilot when:

- the integration owner cannot explain local evaluation and credential classes;
- the proposed flag controls authorization or secrets;
- required legal/restore/security work is incomplete for real data;
- the team does not return after the walkthrough; or
- requested enterprise scope exceeds the stated beta boundary.

## 9. Support and operating expectations

For each pilot, write down rather than imply:

- supported SDK/version and deployment topology;
- response window and contact channel;
- planned maintenance and upgrade notice;
- data retention/deletion and export process;
- backup/restore owner and last tested date;
- incident communication owner;
- credential rotation responsibility; and
- pilot end date and removal procedure.

No uptime percentage, support SLA, recovery objective, customer logo, or adoption number may be
published until it exists in a real agreement or measured artifact.

## 10. Evidence record template

```text
Pilot identifier (non-customer-safe label):
Deployment mode:
SDK and version:
Reversible use case:
Integration start/end:
First successful local evaluation:
First publish/revision:
Kill-switch exercise:
Outage/LKG exercise:
Confusing steps:
Missing capability:
Continue/stop decision:
Permission for any anonymized portfolio statement:
```

Keep real pilot records outside the public repository unless the participant explicitly approves a
bounded anonymized statement.
