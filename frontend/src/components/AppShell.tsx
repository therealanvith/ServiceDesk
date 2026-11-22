"use client";

import { ChatWidget } from "@/components/ChatWidget";
import { Icon } from "@/components/Icon";
import { Sidebar } from "@/components/Sidebar";
import { useAuth } from "@/context/AuthContext";
import { type ReactNode, useEffect, useState } from "react";

const STORAGE_KEY = "sidebar-collapsed";

/** Sidebar and help chat around the page, shown only while someone is signed in. */
export function AppShell({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  // The choice is remembered per browser; storage can be unavailable, so it is only a convenience.
  useEffect(() => {
    try {
      setCollapsed(localStorage.getItem(STORAGE_KEY) === "1");
    } catch {}
  }, []);

  const change = (value: boolean) => {
    setCollapsed(value);
    try {
      localStorage.setItem(STORAGE_KEY, value ? "1" : "0");
    } catch {}
  };

  const classes = ["shell"];
  if (user) classes.push("shell-with-sidebar");
  if (user && collapsed) classes.push("shell-collapsed");

  return (
    <div className={classes.join(" ")}>
      {user && <Sidebar onCollapse={() => change(true)} />}
      {user && collapsed && (
        <button
          type="button"
          className="sidebar-reopen"
          aria-label="Show sidebar"
          onClick={() => change(false)}
        >
          <Icon name="menu" size={18} />
        </button>
      )}
      <main id="main-content" className="page">
        {children}
      </main>
      {user && <ChatWidget />}
    </div>
  );
}
