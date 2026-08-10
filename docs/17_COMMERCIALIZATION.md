# 17 - Commercialization and Pilot Strategy

## 1. Product hypothesis

LaunchForge could become a small developer-tool SaaS, but the first goal is to prove that teams value a simpler feature-flag platform with understandable pricing and strong Java support.

This document contains hypotheses, not guaranteed demand/revenue.

## 2. Initial customer profile

Start narrow:

- small SaaS teams;
- Java/Spring-heavy backend;
- roughly 2-30 developers;
- already deploy multiple times per month;
- want safer rollouts/kill switches;
- do not need every enterprise experimentation feature.

Avoid competing immediately for highly regulated global enterprises.

## 3. First commercial promise

> Ship risky features gradually, disable them instantly, and keep evaluation local to your application.

Avoid claiming:

- "zero downtime";
- "100% propagation";
- "enterprise grade";
- specific scale;
- cost savings;

until supported by evidence/customer outcomes.

## 4. MVP commercial features

Potential pilot:

- organizations/projects;
- dev/staging/prod;
- boolean/string/number/JSON flags;
- targeting;
- percentage rollouts;
- Java SDK;
- JavaScript/React SDK;
- streaming/polling;
- audit/revisions;
- key rotation;
- team roles.

Analytics can be optional.

## 5. Pricing hypotheses

Example hypotheses only:

### Free

- 1 project;
- small team;
- limited environments/flags.

### Developer

- multiple projects;
- longer audit;
- increased limits.

### Team

- roles;
- higher usage;
- analytics;
- support.

Do not choose final prices from this document. Interview pilot users and compare current alternatives before charging.

## 6. What to meter commercially

Potential billing dimensions:

- monthly active client instances;
- monthly evaluation events only if analytics is enabled;
- seats;
- projects/environments;
- premium retention/support.

Avoid charging on every local evaluation if the platform does not observe those evaluations; that creates confusing/invasive metering.

## 7. Pilot acquisition

Good first channels:

- developer communities;
- Java/Spring meetups;
- small SaaS founders;
- open-source users;
- direct outreach to engineering leads;
- GitHub portfolio visitors.

Offer a narrow pilot and ask for concrete feedback.

## 8. Pilot success questions

- Did integration take less than one hour?
- Did SDK behavior feel safe during outage?
- Was production publish/rollback understandable?
- Which targeting operators were missing?
- Did the team trust key/security model?
- Would they replace their current homegrown flags?
- What would they pay for?
- What prevented deployment?

Collect qualitative results before expanding features.

## 9. Hosting stages

### Stage 1 - Portfolio/local

Self-contained Compose.

### Stage 2 - Private pilot

Single hosted region with explicit beta limitations, backups, monitoring, support contact.

### Stage 3 - Paid beta

Only after:

- auth/security review;
- restore test;
- operational alerts;
- documented retention;
- terms/privacy;
- billing and support boundaries.

## 10. Open-source strategy

Possible model:

- SDKs open source;
- core server source available/open;
- hosted service paid.

Or:

- reference SDKs open;
- hosted control plane proprietary.

Choose later based on goals. Do not complicate the initial build with licensing strategy.

## 11. Competitive positioning

Do not clone every feature from established platforms.

Potential differentiation:

- simple Java-first integration;
- transparent deterministic algorithm;
- easy self-host/local demo;
- clear outage behavior;
- affordable small-team tier;
- developer-readable audit/revision model.

## 12. Product validation before scale work

Before building multi-region or advanced experimentation, try to obtain:

- 5-10 developer interviews;
- 2-3 teams willing to integrate demo/pilot;
- documented objections;
- at least one repeated pain point.

If nobody needs a feature, do not build it merely because it sounds enterprise-like.

## 13. Legal/privacy work before real users

Before accepting real customer data:

- privacy policy;
- terms;
- data processing assumptions;
- subprocessor list where relevant;
- retention/deletion policy;
- incident contact;
- appropriate company/tax/payment setup.

This repository is engineering documentation, not legal advice.

## 14. Commercial evidence for portfolio

Even without revenue, useful evidence includes:

- external user installed SDK;
- pilot feedback;
- GitHub stars/contributors;
- integration time measurement;
- issue/feature request from real developer;
- real but anonymized operational learnings.

Never fabricate customer logos, revenue, or adoption.
