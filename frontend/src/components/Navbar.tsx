"use client";
import { ThemeToggle } from "@/components/ThemeToggle";
import { useAuth } from "@/context/AuthContext";
import { api } from "@/lib/apiClient";
import { ROLE_LABELS, isAdmin } from "@/lib/labels";
import { useLiveEvent } from "@/lib/liveEvents";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";

export function Navbar() {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [unread, setUnread] = useState(0);

  const [inboxKey, setInboxKey] = useState(0);

  // The live stream says when something new arrives (and when it reconnects, in case anything
  // was missed); the count is also re-checked on every page change.
  useLiveEvent((e) => {
    if (e.event === "notification" || e.event === "ready") setInboxKey((k) => k + 1);
  });

  // biome-ignore lint/correctness/useExhaustiveDependencies: re-check on page change and live events
  useEffect(() => {
    if (!user) {
      setUnread(0);
      return;
    }
    let active = true;
    api
      .getNotifications()
      .then((inbox) => active && setUnread(inbox.unreadCount))
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [user, pathname, inboxKey]);

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
        ...(user.role !== "EMPLOYEE" ? [{ href: "/board", label: "Board" }] : []),
        { href: "/issues", label: "All issues" },
        { href: "/status", label: "Service status" },
        { href: "/incidents/new", label: "Report an issue" },
      ];
  const adminLinks =
    user && isAdmin(user.role)
      ? [
          { href: "/reports", label: "Reports" },
          { href: "/audit", label: "Audit log" },
          { href: "/admin/users", label: "Users" },
          { href: "/admin", label: "Settings" },
        ]
      : [];

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
        {adminLinks.length > 0 && (
          <AdminMenu links={adminLinks} pathname={pathname} isActive={isActive} />
        )}
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
                <Link href="/account" className="user-chip-name">
                  {user.fullName}
                </Link>
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

/** The administrator pages, behind one button so the top bar stays on a single line. */
function AdminMenu({
  links,
  pathname,
  isActive,
}: {
  links: { href: string; label: string }[];
  pathname: string;
  isActive: (href: string) => boolean;
}) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const current = links.find((link) => isActive(link.href));

  // Close after navigating, on a click elsewhere, or on Escape (returning focus to the button).
  // biome-ignore lint/correctness/useExhaustiveDependencies: closes whenever the page changes
  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <div className="admin-menu" ref={wrapRef}>
      <button
        ref={buttonRef}
        type="button"
        className="nav-link admin-menu-button"
        aria-expanded={open}
        aria-controls="admin-menu-list"
        data-current={current ? "true" : undefined}
        onClick={() => setOpen((v) => !v)}
      >
        {current ? current.label : "Admin"}
        <span aria-hidden="true" className="admin-menu-caret">
          ▾
        </span>
      </button>
      {open && (
        <ul className="admin-menu-list" id="admin-menu-list">
          {links.map((link) => (
            <li key={link.href}>
              <Link
                href={link.href}
                className="admin-menu-item"
                aria-current={isActive(link.href) ? "page" : undefined}
              >
                {link.label}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
