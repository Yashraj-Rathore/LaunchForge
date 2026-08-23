{{- define "launchforge.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "launchforge.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name (include "launchforge.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{- define "launchforge.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
app.kubernetes.io/name: {{ include "launchforge.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{- define "launchforge.selectorLabels" -}}
app.kubernetes.io/name: {{ include "launchforge.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{- define "launchforge.image" -}}
{{- if .digest -}}
{{- printf "%s@%s" .repository .digest }}
{{- else -}}
{{- printf "%s:%s" .repository .tag }}
{{- end -}}
{{- end }}

{{- define "launchforge.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: 10001
runAsGroup: 10001
fsGroup: 10001
fsGroupChangePolicy: OnRootMismatch
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{- define "launchforge.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end }}

{{- define "launchforge.databaseEnv" -}}
- name: LAUNCHFORGE_DB_URL
  value: {{ .Values.external.database.jdbcUrl | quote }}
- name: LAUNCHFORGE_DB_USER
  value: {{ .Values.external.database.username | quote }}
- name: LAUNCHFORGE_DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.existingSecret | quote }}
      key: {{ .Values.secrets.databasePasswordKey | quote }}
{{- end }}

{{- define "launchforge.redisTlsEnv" -}}
- name: LAUNCHFORGE_REDIS_SSL_ENABLED
  value: {{ .Values.external.redis.tls.enabled | quote }}
{{- if .Values.external.redis.tls.enabled }}
- name: SPRING_DATA_REDIS_SSL_BUNDLE
  value: redis
- name: SPRING_SSL_BUNDLE_PEM_REDIS_TRUSTSTORE_CERTIFICATE
  value: file:/var/run/secrets/launchforge/redis/ca.crt
{{- end }}
{{- end }}

{{- define "launchforge.kafkaSecurityEnv" -}}
{{- $protocol := .Values.external.kafka.securityProtocol -}}
{{- $sasl := or (eq $protocol "SASL_PLAINTEXT") (eq $protocol "SASL_SSL") -}}
{{- $tls := or (eq $protocol "SSL") (eq $protocol "SASL_SSL") -}}
- name: LAUNCHFORGE_KAFKA_SECURITY_PROTOCOL
  value: {{ $protocol | quote }}
- name: LAUNCHFORGE_KAFKA_SASL_ENABLED
  value: {{ $sasl | quote }}
{{- if $sasl }}
- name: LAUNCHFORGE_KAFKA_SASL_MECHANISM
  value: {{ .Values.external.kafka.saslMechanism | quote }}
- name: LAUNCHFORGE_KAFKA_SASL_LOGIN_MODULE
  value: {{ .Values.external.kafka.saslLoginModule | quote }}
- name: LAUNCHFORGE_KAFKA_SASL_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.existingSecret | quote }}
      key: {{ .Values.secrets.kafkaSaslUsernameKey | quote }}
- name: LAUNCHFORGE_KAFKA_SASL_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.existingSecret | quote }}
      key: {{ .Values.secrets.kafkaSaslPasswordKey | quote }}
{{- end }}
{{- if $tls }}
- name: SPRING_KAFKA_SSL_BUNDLE
  value: kafka
- name: SPRING_SSL_BUNDLE_PEM_KAFKA_TRUSTSTORE_CERTIFICATE
  value: file:/var/run/secrets/launchforge/kafka/ca.crt
{{- end }}
{{- end }}

{{- define "launchforge.redisTlsVolumeMount" -}}
{{- if .Values.external.redis.tls.enabled }}
- name: redis-tls-trust
  mountPath: /var/run/secrets/launchforge/redis
  readOnly: true
{{- end }}
{{- end }}

{{- define "launchforge.redisTlsVolume" -}}
{{- if .Values.external.redis.tls.enabled }}
- name: redis-tls-trust
  secret:
    secretName: {{ .Values.secrets.existingSecret | quote }}
    defaultMode: 0440
    items:
      - key: {{ .Values.secrets.redisTlsTrustCertificateKey | quote }}
        path: ca.crt
{{- end }}
{{- end }}

{{- define "launchforge.kafkaTlsVolumeMount" -}}
{{- if or (eq .Values.external.kafka.securityProtocol "SSL") (eq .Values.external.kafka.securityProtocol "SASL_SSL") }}
- name: kafka-tls-trust
  mountPath: /var/run/secrets/launchforge/kafka
  readOnly: true
{{- end }}
{{- end }}

{{- define "launchforge.kafkaTlsVolume" -}}
{{- if or (eq .Values.external.kafka.securityProtocol "SSL") (eq .Values.external.kafka.securityProtocol "SASL_SSL") }}
- name: kafka-tls-trust
  secret:
    secretName: {{ .Values.secrets.existingSecret | quote }}
    defaultMode: 0440
    items:
      - key: {{ .Values.secrets.kafkaTlsTrustCertificateKey | quote }}
        path: ca.crt
{{- end }}
{{- end }}

{{- define "launchforge.commonJavaEnv" -}}
- name: LAUNCHFORGE_OTEL_ENABLED
  value: {{ .Values.external.observability.otelEnabled | quote }}
- name: LAUNCHFORGE_OTLP_TRACES_ENDPOINT
  value: {{ .Values.external.observability.otlpTracesEndpoint | quote }}
- name: LAUNCHFORGE_DEPLOYMENT_ENVIRONMENT
  value: {{ .Values.external.observability.deploymentEnvironment | quote }}
{{- end }}
