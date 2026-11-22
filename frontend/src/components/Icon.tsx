export type IconName =
  | "inbox"
  | "plus"
  | "settings"
  | "layers"
  | "logout"
  | "arrow"
  | "search"
  | "clock"
  | "check"
  | "alert"
  | "menu"
  | "close"
  | "refresh"
  | "back"
  | "sun"
  | "moon"
  | "tickets"
  | "board"
  | "pulse"
  | "chart"
  | "shield"
  | "users"
  | "chat"
  | "send"
  | "globe";
const paths: Record<IconName, string> = {
  inbox: "M4 4h16v16H4z M4 14h5l2 3h2l2-3h5",
  plus: "M12 5v14 M5 12h14",
  settings:
    "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M12 2v3 M12 19v3 M2 12h3 M19 12h3 M5 5l2 2 M17 17l2 2 M5 19l2-2 M17 7l2-2",
  layers: "m12 3 9 5-9 5-9-5 9-5z M3 12l9 5 9-5 M3 16l9 5 9-5",
  logout: "M9 4H4v16h5 M9 12h12 M17 8l4 4-4 4",
  arrow: "M4 12h16 M14 6l6 6-6 6",
  search: "M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14 M15 15l6 6",
  clock: "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18 M12 7v5l3 2",
  check: "m5 12 4 4L19 6",
  alert: "m12 3 10 18H2L12 3z M12 9v4 M12 16v1",
  menu: "M4 6h16 M4 12h16 M4 18h16",
  close: "m6 6 12 12 M6 18 18 6",
  refresh: "M20 7v5h-5 M20 12a8 8 0 1 0-2 6",
  back: "M20 12H4 M10 6l-6 6 6 6",
  sun: "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8 M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4",
  moon: "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z",
  tickets:
    "M4 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4V7z M9 5v14",
  board: "M4 4h5v16H4z M10 4h5v10h-5z M16 4h4v13h-4z",
  pulse: "M3 12h4l3-8 4 16 3-8h4",
  chart: "M4 20V10 M10 20V4 M16 20v-7 M22 20H2",
  shield: "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z M9 12l2 2 4-4",
  users:
    "M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z M2 20c0-3.3 3.1-6 7-6s7 2.7 7 6 M17 5a3 3 0 0 1 0 6 M19 14c1.8.7 3 2.3 3 4",
  chat: "M21 12a8 8 0 0 1-11.6 7.1L3 21l1.9-5.4A8 8 0 1 1 21 12z",
  send: "M22 2 11 13 M22 2l-7 20-4-9-9-4 20-7z",
  globe:
    "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M3 12h18 M12 3c2.5 2.5 3.8 5.5 3.8 9S14.5 18.5 12 21c-2.5-2.5-3.8-5.5-3.8-9S9.5 5.5 12 3z",
};
export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}
