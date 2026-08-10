# Codex Prompt 12 - Production Containers and Helm

Implement **LF-1101 through LF-1104 only**.

Build:

- non-root production images;
- production-shaped profile-driven Docker Compose;
- explicit migration ordering;
- Helm deployment for management/edge/projector/web;
- external production dependencies/secrets assumptions;
- probes/resources/service accounts/PDB/HPA where justified;
- local Kubernetes deployment and resiliency proof.

Demonstrate:

- migration before workload;
- edge restart;
- rolling replacement;
- SDK reconnect/LKG;
- revision correctness.

Do not place production credentials in repo.

Update docs/status/changelog and stop.
