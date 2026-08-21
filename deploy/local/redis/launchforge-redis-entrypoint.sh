#!/bin/sh
set -eu

validate_password() {
  name="$1"
  value="$2"
  case "$value" in
    ''|*[!A-Za-z0-9._~-]*)
      echo "$name must contain only local ACL-safe characters" >&2
      exit 1
      ;;
  esac
  if [ "${#value}" -lt 24 ]; then
    echo "$name must contain at least 24 characters" >&2
    exit 1
  fi
}

validate_password LAUNCHFORGE_REDIS_MANAGEMENT_PASSWORD "$LAUNCHFORGE_REDIS_MANAGEMENT_PASSWORD"
validate_password LAUNCHFORGE_REDIS_CONFIG_EDGE_PASSWORD "$LAUNCHFORGE_REDIS_CONFIG_EDGE_PASSWORD"
validate_password LAUNCHFORGE_REDIS_EVENT_WORKER_PASSWORD "$LAUNCHFORGE_REDIS_EVENT_WORKER_PASSWORD"

umask 077
cat >/tmp/launchforge-users.acl <<EOF
user default off
user launchforge-management on >$LAUNCHFORGE_REDIS_MANAGEMENT_PASSWORD %RW~launchforge:security:rate:v1:control:* %R~launchforge:config:snapshot:* &launchforge:config:revision-hints:v1 +@connection +@read +@write +@pubsub +eval +evalsha
user launchforge-config-edge on >$LAUNCHFORGE_REDIS_CONFIG_EDGE_PASSWORD %RW~launchforge:security:* %R~launchforge:config:snapshot:* &launchforge:config:revision-hints:v1 +@connection +@read +@write +@pubsub +eval +evalsha
user launchforge-event-worker on >$LAUNCHFORGE_REDIS_EVENT_WORKER_PASSWORD %RW~launchforge:config:snapshot:* &launchforge:config:revision-hints:v1 +@connection +@read +@write +publish +eval +evalsha
EOF

exec redis-server --save '' --appendonly no --aclfile /tmp/launchforge-users.acl
