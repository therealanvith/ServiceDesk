package com.servicedesk.domain.user;

/**
 * System roles defining role-based authorization boundaries.
 *
 * <ul>
 *   <li>EMPLOYEE: reports issues and sees only their own tickets.
 *   <li>TECHNICIAN: works the tickets assigned to them.
 *   <li>ADMINISTRATOR: sees and manages every ticket and the service settings.
 *   <li>ROOT: everything an administrator can do, plus managing user accounts and roles.
 * </ul>
 */
public enum Role {
  EMPLOYEE,
  TECHNICIAN,
  ADMINISTRATOR,
  ROOT;

  /** Administrators and root have full access to every ticket and setting. */
  public boolean hasAdminRights() {
    return this == ADMINISTRATOR || this == ROOT;
  }
}
