const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type Role = "EMPLOYEE" | "TECHNICIAN" | "ADMINISTRATOR";
export type Category = "NETWORK" | "HARDWARE" | "SOFTWARE" | "ACCESS";
export type Impact = "HIGH" | "MEDIUM" | "LOW";
export type Urgency = "HIGH" | "MEDIUM" | "LOW";
export type Priority = "P1_CRITICAL" | "P2_HIGH" | "P3_MEDIUM" | "P4_LOW";
export type IncidentStatus =
  | "REPORTED"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "PENDING_INFO"
  | "RESOLVED"
  | "CLOSED"
  | "CANCELLED";
export type SlaStatus = "WITHIN_SLA" | "WARNING_BREACH_IMMINENT" | "BREACHED";

export interface UserSummary {
  id: string;
  fullName: string;
  email: string;
}

export interface UserProfile {
  id: string;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  skills: Category[];
}

export interface SlaWindow {
  responseDeadline: string;
  resolutionDeadline: string;
  slaStatus: SlaStatus;
  pausedSeconds: number;
}

export interface IncidentDetail {
  id: string;
  incidentNumber: string;
  title: string;
  description: string;
  category: Category;
  status: IncidentStatus;
  priority: Priority;
  impact: Impact;
  urgency: Urgency;
  reporter: UserSummary;
  assignee: UserSummary | null;
  slaWindow: SlaWindow | null;
  resolutionNotes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface IncidentSummary {
  id: string;
  incidentNumber: string;
  title: string;
  category: Category;
  status: IncidentStatus;
  priority: Priority;
  reporterName: string;
  assigneeName: string;
  slaStatus: SlaStatus | null;
  createdAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CommentItem {
  id: string;
  incidentId: string;
  authorId: string;
  authorName: string;
  commentText: string;
  isInternal: boolean;
  createdAt: string;
}

export interface AuditLogItem {
  id: string;
  incidentId: string;
  actorId: string | null;
  actorName: string;
  action: string;
  oldValue: string | null;
  newValue: string | null;
  remarks: string | null;
  timestamp: string;
}

export interface SimilarIncident {
  incidentNumber: string;
  title: string;
  similarityScore: number;
  status: string;
  resolutionSummary: string | null;
}

export interface TechnicianItem {
  id: string;
  username: string;
  fullName: string;
  email: string;
  skills: Category[];
  activeTicketsCount: number;
  available: boolean;
}

export interface AssignmentConfig {
  activeStrategy: string;
}

export interface SlaEvaluationSummary {
  evaluatedCount: number;
  breachedCount: number;
  evaluatedAt: string;
}

export interface HealthResponse {
  status: "UP" | "DOWN";
  service?: string;
  time?: string;
}

function getAuthToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return localStorage.getItem("servicedesk_jwt");
}

export async function checkBackendHealth(): Promise<HealthResponse> {
  try {
    const response = await fetch(`${API_BASE_URL}/health`, {
      method: "GET",
      headers: { Accept: "application/json" },
      cache: "no-store",
    });

    if (!response.ok) {
      return { status: "DOWN" };
    }

    const data = (await response.json()) as {
      status: string;
      service: string;
      time: string;
    };

    return {
      status: data.status === "UP" ? "UP" : "DOWN",
      service: data.service,
      time: data.time,
    };
  } catch {
    return { status: "DOWN" };
  }
}

export async function apiClient<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const path = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;
  const url = `${API_BASE_URL}${path}`;

  const headers = new Headers(options.headers);
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }
  if (!headers.has("Content-Type") && options.body) {
    headers.set("Content-Type", "application/json");
  }

  const token = getAuthToken();
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let errorDetail = response.statusText;
    try {
      const errJson = await response.json();
      errorDetail = errJson.message || errJson.error || response.statusText;
    } catch {
      // ignore
    }
    throw new Error(errorDetail);
  }

  return (await response.json()) as T;
}

export const api = {
  // Auth
  login: (credentials: { username: string; password: string }) =>
    apiClient<{ token: string; user: UserProfile }>("/auth/login", {
      method: "POST",
      body: JSON.stringify(credentials),
    }),

  getCurrentUser: () => apiClient<UserProfile>("/users/me"),

  listTechnicians: () => apiClient<TechnicianItem[]>("/users/technicians"),

  updateTechnicianSkills: (id: string, skills: Category[]) =>
    apiClient<TechnicianItem>(`/users/${id}/skills`, {
      method: "PUT",
      body: JSON.stringify({ skills }),
    }),

  // Incidents
  createIncident: (data: {
    title: string;
    description: string;
    category: Category;
    impact: Impact;
    urgency: Urgency;
  }) =>
    apiClient<IncidentDetail>("/incidents", {
      method: "POST",
      body: JSON.stringify(data),
    }),

  listIncidents: (
    params: {
      status?: IncidentStatus;
      priority?: Priority;
      category?: Category;
      assigneeId?: string;
      reporterId?: string;
      slaStatus?: SlaStatus;
      searchText?: string;
      page?: number;
      size?: number;
    } = {}
  ) => {
    const query = new URLSearchParams();
    if (params.status) query.set("status", params.status);
    if (params.priority) query.set("priority", params.priority);
    if (params.category) query.set("category", params.category);
    if (params.assigneeId) query.set("assigneeId", params.assigneeId);
    if (params.reporterId) query.set("reporterId", params.reporterId);
    if (params.slaStatus) query.set("slaStatus", params.slaStatus);
    if (params.searchText) query.set("searchText", params.searchText);
    if (params.page !== undefined) query.set("page", params.page.toString());
    if (params.size !== undefined) query.set("size", params.size.toString());

    const qs = query.toString();
    return apiClient<PagedResponse<IncidentSummary>>(`/incidents${qs ? `?${qs}` : ""}`);
  },

  getIncident: (id: string) => apiClient<IncidentDetail>(`/incidents/${id}`),

  assignIncident: (id: string, technicianId?: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/assign`, {
      method: "POST",
      body: JSON.stringify(technicianId ? { technicianId } : {}),
    }),

  startWork: (id: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/start`, { method: "POST" }),

  requestInfo: (id: string, question: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/request-info`, {
      method: "POST",
      body: JSON.stringify({ question }),
    }),

  provideInfo: (id: string, answer: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/provide-info`, {
      method: "POST",
      body: JSON.stringify({ answer }),
    }),

  resolveIncident: (id: string, resolutionSummary: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/resolve`, {
      method: "POST",
      body: JSON.stringify({ resolutionSummary }),
    }),

  closeIncident: (id: string, remarks?: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/close`, {
      method: "POST",
      body: JSON.stringify({ remarks }),
    }),

  cancelIncident: (id: string, reason: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/cancel`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    }),

  overridePriority: (id: string, priority: Priority, remarks: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/priority`, {
      method: "PUT",
      body: JSON.stringify({ priority, remarks }),
    }),

  // Comments
  addComment: (id: string, commentText: string, isInternal: boolean) =>
    apiClient<CommentItem>(`/incidents/${id}/comments`, {
      method: "POST",
      body: JSON.stringify({ commentText, isInternal }),
    }),

  getComments: (id: string) => apiClient<CommentItem[]>(`/incidents/${id}/comments`),

  // Audit
  getAuditLogs: (id: string) => apiClient<AuditLogItem[]>(`/incidents/${id}/audit-logs`),

  // Intelligence
  getSimilarIncidents: (id: string) => apiClient<SimilarIncident[]>(`/incidents/${id}/similar`),

  // Admin
  getAssignmentConfig: () => apiClient<AssignmentConfig>("/admin/config/assignment"),

  updateAssignmentConfig: (strategy: string) =>
    apiClient<AssignmentConfig>("/admin/config/assignment", {
      method: "PUT",
      body: JSON.stringify({ strategy }),
    }),

  triggerSlaEvaluation: () =>
    apiClient<SlaEvaluationSummary>("/admin/sla/evaluate", { method: "POST" }),
};
