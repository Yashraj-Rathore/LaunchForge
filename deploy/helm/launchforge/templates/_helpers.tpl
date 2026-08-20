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

{{- define "launchforge.commonJavaEnv" -}}
- name: LAUNCHFORGE_OTEL_ENABLED
  value: {{ .Values.external.observability.otelEnabled | quote }}
- name: LAUNCHFORGE_OTLP_TRACES_ENDPOINT
  value: {{ .Values.external.observability.otlpTracesEndpoint | quote }}
- name: LAUNCHFORGE_DEPLOYMENT_ENVIRONMENT
  value: {{ .Values.external.observability.deploymentEnvironment | quote }}
{{- end }}
