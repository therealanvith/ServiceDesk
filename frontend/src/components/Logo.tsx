/** The ServiceDesk mark: a headset around a chat bubble. */
export function Logo({ size = 32 }: { size?: number }) {
  return (
    <svg
      className="logo"
      width={size}
      height={size}
      viewBox="0 0 64 64"
      aria-hidden="true"
      fill="currentColor"
      stroke="currentColor"
      strokeWidth="0"
    >
      <rect x="3" y="21" width="9" height="19" rx="3.5" />
      <rect x="52" y="21" width="9" height="19" rx="3.5" />
      <path d="M9 22C9 11 19 5 32 5s23 6 23 17" fill="none" strokeWidth="4" strokeLinecap="round" />
      <circle cx="32" cy="30" r="14" />
      <path d="M18.5 47l1.5-12 9 8z" />
      <path d="M56 40c0 10-7 15-17 15" fill="none" strokeWidth="3.6" strokeLinecap="round" />
      <circle cx="35.5" cy="55" r="3.8" />
      <g fill="var(--surface)" stroke="none">
        <circle cx="25.5" cy="30" r="2.2" />
        <circle cx="32" cy="30" r="2.2" />
        <circle cx="38.5" cy="30" r="2.2" />
      </g>
    </svg>
  );
}
