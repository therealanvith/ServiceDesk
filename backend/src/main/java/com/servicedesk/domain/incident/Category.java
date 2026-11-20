package com.servicedesk.domain.incident;

/** Problem category classification for incidents and technician skills. */
public enum Category {
  NETWORK,
  HARDWARE,
  SOFTWARE,
  /** Authentication, accounts and permissions. */
  ACCESS,
  DATABASE,
  CLOUD,
  SECURITY,
  APPLICATION,
  STORAGE
}
