"use client";

import { Avatar } from "@/components/Avatar";
import { Icon } from "@/components/Icon";
import { Logo } from "@/components/Logo";
import { ThemeToggle } from "@/components/ThemeToggle";
import { useAuth } from "@/context/AuthContext";
import { api } from "@/lib/apiClient";
import { ROLE_LABELS, isAdmin } from "@/lib/labels";
import { useLiveEvent } from "@/lib/liveEvents";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

interface NavItem {
  href: string;
  label: string;
  badge?: number;
}

/** Main navigation down the left side; a slide-in drawer under a top strip on phones. */
export function Sidebar({ onCollapse }: { onCollapse: () => void }) {
  const { user, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const [unread, setUnread] = useState(0);
  const [inboxKey, setInboxKey] = useState(0);
  const [open, setOpen] = useState(false);

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

  // Close the drawer after navigating, and on Escape.
  // biome-ignore lint/correctness/useExhaustiveDependencies: closes whenever the page changes
  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  if (!user) return null;

  const work: NavItem[] = [
    {
      href: "/",

      label:
        isAdmin(user.role) || user.teamLead
          ? "All incidents"
          : user.role === "TECHNICIAN"
            ? "My queue"
            : "My tickets",
    },
    ...(user.role !== "EMPLOYEE" ? [{ href: "/board", label: "Board" }] : []),
    { href: "/issues", label: "All issues" },
    { href: "/status", label: "Service status" },
    { href: "/notifications", label: "Inbox", badge: unread },
  ];
  const admin: NavItem[] = isAdmin(user.role)
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

  const renderItem = (item: NavItem) => (
    <li key={item.href}>
      <Link
        href={item.href}
        className="side-link"
        aria-current={isActive(item.href) ? "page" : undefined}
        aria-label={item.badge ? `${item.label}, ${item.badge} unread` : undefined}
      >
        <span>{item.label}</span>
        {item.badge ? <span className="badge">{item.badge > 99 ? "99+" : item.badge}</span> : null}
      </Link>
    </li>
  );

  return (
    <>
      <div className="mobile-bar">
        <button
          type="button"
          className="mobile-bar-button"
          aria-label="Open menu"
          aria-expanded={open}
          aria-controls="sidebar"
          onClick={() => setOpen(true)}
        >
          <Icon name="menu" size={20} />
        </button>
        <Link href="/" className="brand">
          <Logo />
          ServiceDesk
        </Link>
      </div>
      {open && (
        <button
          type="button"
          className="sidebar-scrim"
          aria-label="Close menu"
          onClick={() => setOpen(false)}
        />
      )}
      <aside id="sidebar" className="sidebar" data-open={open ? "true" : undefined}>
        <div className="sidebar-head">
          <Link href="/" className="brand">
            <Logo />
            ServiceDesk
          </Link>
          <button
            type="button"
            className="sidebar-close"
            aria-label="Hide sidebar"
            onClick={() => {
              // On a wide screen the sidebar is hidden for good; on a phone it is a drawer.
              if (window.matchMedia("(min-width: 901px)").matches) onCollapse();
              else setOpen(false);
            }}
          >
            <Icon name="close" size={18} />
          </button>
        </div>

        <Link href="/incidents/new" className="btn btn-primary side-cta">
          Report an issue
        </Link>

        <nav className="side-nav" aria-label="Main">
          <ul>{work.map(renderItem)}</ul>
          {admin.length > 0 && (
            <>
              <p className="side-label">Admin</p>
              <ul>{admin.map(renderItem)}</ul>
            </>
          )}
        </nav>

        <div className="side-foot">
          <Link href="/account" className="side-user" aria-label="Your account">
            <Avatar name={user.fullName} src={user.avatar} size={30} />
            <span className="side-user-text">
              <span className="side-user-name">{user.fullName}</span>
              <span className="side-user-role">
                {user.teamLead ? "Team lead" : ROLE_LABELS[user.role]}
              </span>
            </span>
          </Link>
          <ThemeToggle />
          <button
            type="button"
            className="side-icon"
            aria-label="Sign out"
            title="Sign out"
            onClick={() => {
              logout();
              router.replace("/login");
            }}
          >
            <Icon name="logout" size={16} />
          </button>
        </div>
      </aside>
    </>
  );
}
