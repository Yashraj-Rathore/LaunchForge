/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_LAUNCHFORGE_EDGE_URL?: string;
  readonly VITE_LAUNCHFORGE_CLIENT_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
