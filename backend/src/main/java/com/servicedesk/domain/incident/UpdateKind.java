package com.servicedesk.domain.incident;

/** What an entry in a ticket's Updates thread represents. */
public enum UpdateKind {
  /** Free text typed by a person. */
  COMMENT,
  /** A technician asked the reporter for details (the SLA clock pauses). */
  QUESTION,
  /** The reporter replied to a question (the SLA clock resumes). */
  ANSWER,
  /** A technician picked the ticket up and started work. */
  PICKUP,
  /** How the problem was fixed. */
  RESOLUTION,
  /** Closed, cancelled or similar lifecycle notes. */
  STATUS
}
