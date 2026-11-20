"use client";

import { useAuth } from "@/context/AuthContext";
import { type NotificationInbox, api } from "@/lib/apiClient";
import { timeAgo } from "@/lib/labels";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

export default function NotificationsPage() {
  const { user, loading: authLoading } = useAuth();
  const [inbox, setInbox] = useState<NotificationInbox | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    api
      .getNotifications()
      .then(setInbox)
      .catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    if (user) load();
  }, [user, load]);

  const markRead = async (id: string) => {
    try {
      await api.markNotificationRead(id);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Couldn’t mark the notification as read");
      return;
    }
    setError(null);
    setInbox((current) =>
      current
        ? {
            unreadCount: Math.max(0, current.unreadCount - 1),
            items: current.items.map((n) => (n.id === id ? { ...n, read: true } : n)),
          }
        : current
    );
  };

  if (authLoading || !user) return <output className="empty">Loading…</output>;

  return (
    <>
      <div className="page-header">
        <div>
          <h1>
            Inbox
            {inbox && inbox.unreadCount > 0 && (
              <span className="count">{inbox.unreadCount} unread</span>
            )}
          </h1>
          <p>Updates about tickets you reported or work on. Copies are also sent by email.</p>
        </div>
        {inbox && inbox.unreadCount > 0 && (
          <button
            type="button"
            className="btn"
            onClick={async () => {
              try {
                await api.markAllNotificationsRead();
                setError(null);
              } catch (err: unknown) {
                setError(
                  err instanceof Error ? err.message : "Couldn’t mark notifications as read"
                );
              }
              load();
            }}
          >
            Mark all as read
          </button>
        )}
      </div>
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
      <section className="card">
        {!inbox ? (
          <output className="empty">Loading…</output>
        ) : inbox.items.length === 0 ? (
          <div className="empty">
            <h2>Nothing here yet</h2>
            <p>You’ll be notified when a ticket you’re involved in changes.</p>
          </div>
        ) : (
          <ul className="inbox">
            {inbox.items.map((n) => (
              <li key={n.id} className={n.read ? "" : "is-unread"}>
                <div className="inbox-main">
                  <p className="inbox-title">{n.title}</p>
                  <p className="inbox-message">{n.message}</p>
                  <p className="inbox-meta">
                    <time dateTime={n.createdAt} title={new Date(n.createdAt).toLocaleString()}>
                      {timeAgo(n.createdAt)}
                    </time>
                    {n.incidentId && (
                      <>
                        {" · "}
                        <Link
                          href={`/incidents/${n.incidentId}`}
                          className="link"
                          onClick={() => !n.read && markRead(n.id)}
                        >
                          Open {n.incidentNumber ?? "ticket"}
                        </Link>
                      </>
                    )}
                  </p>
                </div>
                {!n.read && (
                  <button
                    type="button"
                    className="btn btn-ghost btn-sm"
                    onClick={() => markRead(n.id)}
                  >
                    Mark read
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
