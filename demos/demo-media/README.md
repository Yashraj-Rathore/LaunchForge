# Demo media provenance

Every PNG and WebM file in this directory is captured by
`tests/e2e/specs/demo-media.spec.ts` from the real isolated Compose stack. The automated route logs
in through local OIDC, edits and publishes the deterministic seed, observes the connected browser
SDK, and visits the immutable revision and audit views. Images are not composited or retouched.

Regenerate the complete set from revision 1 with a deliberate four-second pause between presentation
steps:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File eng/capture_demo_media.ps1
```

The wrapper uses the pinned `deploy/docker/Dockerfile.demo-capture` toolchain rather than the host's
Node installation. Docker Desktop host networking must be enabled.

The capture uses only fictional Northstar Commerce data. The local password is injected into the
browser test at runtime, remains masked in the recording, and is never written to these artifacts.
