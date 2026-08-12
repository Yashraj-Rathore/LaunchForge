# React SDK

`@launchforge/react-sdk` is a thin subscription layer over one
`@launchforge/js-browser` client. It contains no evaluator or transport implementation.

```tsx
const context = useMemo(
  () => createEvaluationContext(user.id, { country: user.country, plan: user.plan }),
  [user.id, user.country, user.plan],
);

<LaunchForgeProvider options={options} context={context}>
  <Storefront />
</LaunchForgeProvider>
```

Use `useBooleanFlag`, `useStringFlag`, `useNumberFlag`, or `useJsonFlag` for values. Each has a
matching `...Detail` hook with reason, variation, revision, rule, and rollout metadata. The provider
owns, starts, and closes its client unless a `client` prop is supplied for dependency injection.
Every successful snapshot activation and each explicit context replacement rerenders subscribers;
unmount removes subscriptions and closes owned resources.

Memoize context objects at the application boundary. Context is local evaluator input and is never
sent by this SDK, but a new object is still an intentional context replacement and rerender.
