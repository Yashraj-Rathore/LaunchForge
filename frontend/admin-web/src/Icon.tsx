import type { ReactNode } from 'react';

export type IconName =
  | 'activity'
  | 'alert'
  | 'analytics'
  | 'audit'
  | 'check'
  | 'chevron'
  | 'close'
  | 'flag'
  | 'key'
  | 'logout'
  | 'menu'
  | 'plus'
  | 'revision'
  | 'search'
  | 'shield';

export function Icon({
  name,
  size = 20,
  className,
}: {
  readonly name: IconName;
  readonly size?: number;
  readonly className?: string;
}) {
  return (
    <svg
      aria-hidden="true"
      className={className}
      fill="none"
      height={size}
      viewBox="0 0 24 24"
      width={size}
    >
      {path(name)}
    </svg>
  );
}

function path(name: IconName): ReactNode {
  const shared = {
    stroke: 'currentColor',
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    strokeWidth: 1.8,
  };
  switch (name) {
    case 'activity':
      return <path {...shared} d="M3 12h4l2.25-7 5.5 14L17 12h4" />;
    case 'alert':
      return (
        <>
          <path
            {...shared}
            d="M10.3 3.8 2.8 17a2 2 0 0 0 1.74 3h14.92a2 2 0 0 0 1.74-3L13.7 3.8a2 2 0 0 0-3.4 0Z"
          />
          <path {...shared} d="M12 9v4m0 3.5h.01" />
        </>
      );
    case 'analytics':
      return (
        <>
          <path {...shared} d="M4 19V9m6 10V5m6 14v-7m4 7H2" />
          <path {...shared} d="m3 6 6-3 6 6 6-4" />
        </>
      );
    case 'audit':
      return (
        <>
          <path {...shared} d="M8 4h8m-9 4h10M7 12h6" />
          <path {...shared} d="M17 13.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z" />
          <path {...shared} d="M17 16v2l1.5 1" />
          <path {...shared} d="M5 21H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v7" />
        </>
      );
    case 'check':
      return <path {...shared} d="m5 12 4 4L19 6" />;
    case 'chevron':
      return <path {...shared} d="m9 18 6-6-6-6" />;
    case 'close':
      return <path {...shared} d="M6 6l12 12M18 6 6 18" />;
    case 'flag':
      return (
        <>
          <path {...shared} d="M5 21V4" />
          <path {...shared} d="M5 5c4-3 7 3 14 0v9c-7 3-10-3-14 0" />
        </>
      );
    case 'key':
      return (
        <>
          <circle {...shared} cx="8" cy="15" r="4" />
          <path {...shared} d="m11 12 8-8m-3 3 3 3m-6 0 2 2" />
        </>
      );
    case 'logout':
      return (
        <>
          <path {...shared} d="M10 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5" />
          <path {...shared} d="m16 16 4-4-4-4m4 4H8" />
        </>
      );
    case 'menu':
      return <path {...shared} d="M4 7h16M4 12h16M4 17h16" />;
    case 'plus':
      return <path {...shared} d="M12 5v14M5 12h14" />;
    case 'revision':
      return (
        <>
          <path {...shared} d="M12 8v4l3 2" />
          <path {...shared} d="M3.1 11a9 9 0 1 1 2.5 7M3 16v-5h5" />
        </>
      );
    case 'search':
      return (
        <>
          <circle {...shared} cx="10.5" cy="10.5" r="6.5" />
          <path {...shared} d="m16 16 5 5" />
        </>
      );
    case 'shield':
      return (
        <>
          <path {...shared} d="M12 22s8-3.5 8-10V5l-8-3-8 3v7c0 6.5 8 10 8 10Z" />
          <path {...shared} d="m8.5 12 2.2 2.2 4.8-5" />
        </>
      );
  }
}
