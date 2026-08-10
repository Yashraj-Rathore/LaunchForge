#!/usr/bin/env bash
set -euo pipefail

: "${KC_BOOTSTRAP_ADMIN_USERNAME:?Missing Keycloak admin username}"
: "${KC_BOOTSTRAP_ADMIN_PASSWORD:?Missing Keycloak admin password}"
: "${LAUNCHFORGE_DEMO_USER_PASSWORD:?Missing local demo user password}"

KCADM=/opt/keycloak/bin/kcadm.sh
KCADM_CONFIG=/tmp/launchforge-kcadm.config

authenticated=false
for attempt in $(seq 1 30); do
  if "${KCADM}" config credentials \
      --config "${KCADM_CONFIG}" \
      --server http://keycloak:8080 \
      --realm master \
      --user "${KC_BOOTSTRAP_ADMIN_USERNAME}" \
      --password "${KC_BOOTSTRAP_ADMIN_PASSWORD}"; then
    authenticated=true
    break
  fi
  sleep 2
done

if [[ "${authenticated}" != true ]]; then
  echo "Keycloak admin authentication did not become ready" >&2
  exit 1
fi

for username in owner admin developer viewer; do
  "${KCADM}" set-password \
    --config "${KCADM_CONFIG}" \
    --realm launchforge \
    --username "${username}" \
    --new-password "${LAUNCHFORGE_DEMO_USER_PASSWORD}" \
    --temporary=false
done
