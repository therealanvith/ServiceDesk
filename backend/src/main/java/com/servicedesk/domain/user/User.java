package com.servicedesk.domain.user;

import com.servicedesk.domain.incident.Category;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Pure domain entity representing a user in the ServiceDesk system. */
public class User {

  private final UserId id;
  private final String username;
  private final String fullName;
  private final String email;
  private final Role role;
  private final Set<Category> skills;
  private int activeTicketsCount;
  private boolean available;
  private final int maxActiveTickets;
  private final boolean teamLead;

  public static final int DEFAULT_MAX_ACTIVE_TICKETS = 5;

  public User(
      UserId id,
      String username,
      String fullName,
      String email,
      Role role,
      Set<Category> skills,
      int activeTicketsCount,
      boolean available) {
    this(
        id,
        username,
        fullName,
        email,
        role,
        skills,
        activeTicketsCount,
        available,
        DEFAULT_MAX_ACTIVE_TICKETS,
        false);
  }

  public User(
      UserId id,
      String username,
      String fullName,
      String email,
      Role role,
      Set<Category> skills,
      int activeTicketsCount,
      boolean available,
      int maxActiveTickets,
      boolean teamLead) {
    this.maxActiveTickets = Math.max(1, maxActiveTickets);
    this.teamLead = teamLead;
    this.id = Objects.requireNonNull(id, "User ID must not be null");
    this.username = Objects.requireNonNull(username, "Username must not be null");
    this.fullName = Objects.requireNonNull(fullName, "Full name must not be null");
    this.email = email; // optional: accounts may have no email
    this.role = Objects.requireNonNull(role, "Role must not be null");
    this.skills = skills != null ? new HashSet<>(skills) : new HashSet<>();
    this.activeTicketsCount = Math.max(0, activeTicketsCount);
    this.available = available;
  }

  public UserId getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public Role getRole() {
    return role;
  }

  public Set<Category> getSkills() {
    return Collections.unmodifiableSet(skills);
  }

  public int getActiveTicketsCount() {
    return activeTicketsCount;
  }

  public boolean isAvailable() {
    return available;
  }

  /** Most tickets this person works on at once; automatic and manual assignment respect it. */
  public int getMaxActiveTickets() {
    return maxActiveTickets;
  }

  public boolean hasCapacity() {
    return activeTicketsCount < maxActiveTickets;
  }

  /** Team leads receive the first escalation when a ticket is not acknowledged in time. */
  public boolean isTeamLead() {
    return teamLead;
  }

  public boolean hasSkill(Category category) {
    return skills.contains(category);
  }

  public void setAvailable(boolean available) {
    this.available = available;
  }

  public void updateSkills(Set<Category> newSkills) {
    this.skills.clear();
    if (newSkills != null) {
      this.skills.addAll(newSkills);
    }
  }

  public void incrementActiveTickets() {
    this.activeTicketsCount++;
  }

  public void decrementActiveTickets() {
    if (this.activeTicketsCount > 0) {
      this.activeTicketsCount--;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    User user = (User) o;
    return Objects.equals(id, user.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
