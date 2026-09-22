# Implementation Prompt 06 - JavaScript and React SDKs

Implement **LF-0501 through LF-0505 only**.

Build:

- strict TypeScript evaluator that passes the **same golden vectors** as Java;
- JavaScript SDK bootstrap/poll/stream/local evaluation;
- browser-safe public client-key model and endpoint filtering;
- thin React provider/hooks wrapper;
- fictional React demo storefront.

Security:

- browser never receives server SDK key;
- delivered browser configuration is assumed inspectable;
- flags are not authorization;
- no duplicate evaluator inside React wrapper.

Run Java and JS golden compatibility gates together, browser tests, update docs/status/changelog, and stop.
