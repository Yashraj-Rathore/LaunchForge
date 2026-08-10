# LaunchForge SDKs

Reserved reference SDK boundaries:

- `sdks/java/launchforge-java-sdk` - pure Java reference implementation, flagship;
- `sdks/javascript/packages/core` - framework-neutral TypeScript boundary;
- `sdks/javascript/packages/browser` - browser boundary depending on Core;
- React wrapper inside the JavaScript workspace.

See:

- `docs/05_FLAG_EVALUATION_ENGINE.md`;
- `docs/06_SDK_ARCHITECTURE.md`;
- `ADR-0002`;
- `ADR-0003`.

No SDK should call the network during the flag-evaluation hot path.
