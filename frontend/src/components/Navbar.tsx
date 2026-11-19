"use client";
import { ThemeToggle } from "@/components/ThemeToggle";
import { useAuth } from "@/context/AuthContext";
import { api } from "@/lib/apiClient";
import { ROLE_LABELS, isAdmin } from "@/lib/labels";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

const UNREAD_POLL_MS = 60_000;

export function Navbar() {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [unread, setUnread] = useState(0);

  // Light polling keeps the inbox badge current without a websocket.
  // biome-ignore lint/correctness/useExhaustiveDependencies: re-check whenever the page changes
  useEffect(() => {
    if (!user) {
      setUnread(0);
      return;
    }
    let active = true;
    const load = () =>
      api
        .getNotifications()
        .then((inbox) => active && setUnread(inbox.unreadCount))
        .catch(() => {});
    load();
    const timer = window.setInterval(load, UNREAD_POLL_MS);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [user, pathname]);

  const links = !user
    ? []
    : [
        {
          href: "/",
          label: isAdmin(user.role)
            ? "All incidents"
            : user.role === "TECHNICIAN"
              ? "My queue"
              : "My tickets",
        },
        { href: "/incidents/new", label: "Report an issue" },
        ...(isAdmin(user.role)
          ? [
              { href: "/reports", label: "Reports" },
              { href: "/audit", label: "Audit log" },
              { href: "/admin/users", label: "Users" },
              { href: "/admin", label: "Settings" },
            ]
          : []),
      ];

  const isActive = (href: string) =>
    href === "/"
      ? pathname === "/" || (pathname.startsWith("/incidents/") && pathname !== "/incidents/new")
      : pathname === href;

  return (
    <header className="topbar">
      <div className="topbar-inner">
        <Link href="/" className="brand">
          ServiceDesk
        </Link>
        <nav className="nav" aria-label="Main">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="nav-link"
              aria-current={isActive(link.href) ? "page" : undefined}
            >
              {link.label}
            </Link>
          ))}
        </nav>
        <div className="topbar-end">
          {user && (
            <Link
              href="/notifications"
              className="inbox-link"
              aria-current={pathname === "/notifications" ? "page" : undefined}
              aria-label={unread ? `Inbox, ${unread} unread` : "Inbox"}
            >
              Inbox
              {unread > 0 && <span className="badge">{unread > 99 ? "99+" : unread}</span>}
            </Link>
          )}
          <ThemeToggle />
          {user && (
            <div className="user-chip">
              <span className="user-chip-text">
                <span className="user-chip-name">{user.fullName}</span>
                <br />
                <span className="user-chip-role">{ROLE_LABELS[user.role]}</span>
              </span>
              <button
                type="button"
                className="signout"
                onClick={() => {
                  logout();
                  router.replace("/login");
                }}
              >
                Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
