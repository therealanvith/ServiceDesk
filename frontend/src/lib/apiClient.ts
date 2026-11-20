const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1"
).replace(/\/+$/, "");

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    /** From Retry-After when the server refuses with 429. */
    public readonly retryAfterSeconds?: number
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export type Role = "EMPLOYEE" | "TECHNICIAN" | "ADMINISTRATOR" | "ROOT";
export type Category =
  | "NETWORK"
  | "HARDWARE"
  | "SOFTWARE"
  | "ACCESS"
  | "DATABASE"
  | "CLOUD"
  | "SECURITY"
  | "APPLICATION"
  | "STORAGE";
export type Impact = "HIGH" | "MEDIUM" | "LOW";
export type Urgency = "HIGH" | "MEDIUM" | "LOW";
export type Priority = "P1_CRITICAL" | "P2_HIGH" | "P3_MEDIUM" | "P4_LOW";
export type Criticality = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";
export type IncidentStatus =
  | "REPORTED"
  | "ASSIGNED"
  | "ACKNOWLEDGED"
  | "IN_PROGRESS"
  | "PENDING_INFO"
  | "RESOLVED"
  | "CLOSED"
  | "CANCELLED";
export type SlaStatus = "WITHIN_SLA" | "WARNING_BREACH_IMMINENT" | "BREACHED";

export interface UserSummary {
  id: string;
  fullName: string;
  email: string | null;
}

export interface UserProfile {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  role: Role;
  skills: Category[];
  mustChangePassword: boolean;
  googleLinked: boolean;
  hasPassword: boolean;
}

export interface AuthResult {
  token: string;
  user: UserProfile;
}

export interface AuthConfig {
  googleClientId: string | null;
  registrationEnabled: boolean;
}

export interface SlaWindow {
  responseDeadline: string;
  resolutionDeadline: string;
  slaStatus: SlaStatus;
  pausedSeconds: number;
}

export interface ItService {
  id: string;
  name: string;
  description: string | null;
  criticality: Criticality;
  active: boolean;
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
  service: ItService | null;
  respondedAt: string | null;
  escalatedAt: string | null;
  escalationLevel: EscalationLevel;
  feedbackRating: number | null;
  feedbackComment: string | null;
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
  serviceName: string | null;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type UpdateKind = "COMMENT" | "QUESTION" | "ANSWER" | "PICKUP" | "RESOLUTION" | "STATUS";

export interface CommentItem {
  id: string;
  incidentId: string;
  authorId: string;
  authorName: string;
  commentText: string;
  isInternal: boolean;
  createdAt: string;
  kind: UpdateKind;
  authorRole: Role | null;
}

export interface PublicIssue {
  id: string;
  incidentNumber: string;
  title: string;
  description: string;
  status: IncidentStatus;
  priority: Priority;
  category: Category;
  serviceName: string | null;
  handledBy: string | null;
  createdAt: string;
  updatedAt: string;
  updateCount: number;
  updates: {
    id: string;
    kind: UpdateKind;
    author: string;
    authorRole: Role | null;
    text: string;
    createdAt: string;
  }[];
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
  incidentNumber: string | null;
}

export interface SimilarIncident {
  id: string;
  incidentNumber: string;
  title: string;
  similarityScore: number;
  status: string;
  resolutionSummary: string | null;
  open: boolean;
}

export interface CategorySuggestion {
  category: Category | null;
  confidence: number;
  matchedKeywords: string[];
}

/** "gemini" when the language model answered, "rules" for the built-in fallback. */
export interface TriageSuggestion {
  category: Category;
  impact: Impact;
  urgency: Urgency;
  serviceId: string | null;
  serviceName: string | null;
  predictedPriority: Priority;
  reason: string;
  source: string;
}

export interface AssistantAdvice {
  summary: string;
  steps: string[];
  source: string;
}

export interface AssistantSummary {
  summary: string;
  source: string;
}

export type EscalationLevel = "NONE" | "TEAM_LEAD" | "ADMINISTRATOR" | "IT_MANAGER";

export interface TechnicianItem {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  skills: Category[];
  activeTicketsCount: number;
  available: boolean;
  maxActiveTickets: number;
  teamLead: boolean;
}

export interface UserAccount {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  role: Role;
  active: boolean;
  passwordSignIn: boolean;
  googleSignIn: boolean;
  createdAt: string;
}

export interface PriorityPreview {
  priority: Priority;
  criticality: Criticality;
  matrix: Record<Impact, Record<Urgency, Priority>>;
}

export interface SlaPolicy {
  priority: Priority;
  responseMinutes: number;
  resolutionMinutes: number;
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  message: string;
  incidentId: string | null;
  incidentNumber: string | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationInbox {
  unreadCount: number;
  items: NotificationItem[];
}

export interface ServiceLoad {
  service: string;
  total: number;
  open: number;
}

export interface Report {
  overview: {
    total: number;
    open: number;
    criticalOpen: number;
    resolved: number;
    slaBreached: number;
    slaAtRisk: number;
    escalated: number;
    responseSlaMetPercent: number | null;
    averageRating: number | null;
    ratedCount: number;
  };
  byStatus: Record<string, number>;
  byPriority: Record<string, number>;
  byCategory: Record<string, number>;
  byService: ServiceLoad[];
  technicianWorkload: {
    username: string;
    fullName: string;
    active: number;
    breached: number;
    resolved: number;
    available: boolean;
  }[];
  correlatedServices: ServiceLoad[];
}

export interface Workload {
  active: number;
  inProgress: number;
  waitingOnReporter: number;
  atRisk: number;
  breached: number;
  resolvedThisWeek: number;
}

export type TrendRange = 7 | 30 | 90;

/** Trends over the last 7, 30 or 90 UTC days; dates are ISO yyyy-mm-dd. */
export interface Trends {
  days: TrendRange;
  from: string;
  to: string;
  daily: { date: string; created: number; resolved: number }[];
  weeklyCompliance: {
    weekStart: string;
    resolvedCount: number;
    withinSlaCount: number;
    compliancePercent: number | null;
  }[];
  resolutionByCategory: { category: Category; resolvedCount: number; meanHours: number }[];
  averageFirstResponseMinutes: number | null;
  respondedCount: number;
  escalationsByLevel: { level: Exclude<EscalationLevel, "NONE">; count: number }[];
  selfHelp: { helped: number; notHelped: number };
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

export interface IncidentQuery {
  status?: IncidentStatus;
  priority?: Priority;
  category?: Category;
  assigneeId?: string;
  slaStatus?: SlaStatus;
  serviceId?: string;
  createdFrom?: string;
  createdTo?: string;
  searchText?: string;
  page?: number;
  size?: number;
}

const TOKEN_KEY = "servicedesk_jwt";
const AUTH_PATHS = ["/auth/login", "/auth/google", "/auth/register"];

/** Where the live-updates stream lives (Server-Sent Events). */
export function liveEventsUrl(): string {
  return `${API_BASE_URL}/events`;
}

/** The signed-in session's token, for the live-updates stream. */
export function readAuthToken(): string | null {
  return getAuthToken();
}

function getAuthToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return localStorage.getItem(TOKEN_KEY);
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

  let response: Response;
  try {
    response = await fetch(url, { ...options, headers });
  } catch {
    throw new ApiError("Can't reach the server. Check your connection and try again.", 0);
  }

  if (!response.ok) {
    let errorDetail = response.statusText;
    try {
      const errJson = await response.json();
      errorDetail = errJson.message || errJson.error || response.statusText;
    } catch {
      // ignore
    }
    if (
      response.status === 401 &&
      token &&
      typeof window !== "undefined" &&
      localStorage.getItem(TOKEN_KEY) === token &&
      !AUTH_PATHS.includes(path)
    ) {
      window.dispatchEvent(new Event("servicedesk:session-expired"));
    }
    const retryAfter = Number(response.headers.get("Retry-After"));
    throw new ApiError(
      errorDetail || `Request failed (${response.status})`,
      response.status,
      response.status === 429 && retryAfter > 0 ? retryAfter : undefined
    );
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const post = (body?: unknown): RequestInit => ({
  method: "POST",
  body: body === undefined ? undefined : JSON.stringify(body),
});
const put = (body: unknown): RequestInit => ({ method: "PUT", body: JSON.stringify(body) });

export const api = {
  // Auth
  login: (credentials: { username: string; password: string }) =>
    apiClient<AuthResult>("/auth/login", post(credentials)),
  loginWithGoogle: (credential: string) =>
    apiClient<AuthResult>("/auth/google", post({ credential })),
  register: (data: { username: string; fullName: string; email?: string; password: string }) =>
    apiClient<AuthResult>("/auth/register", post(data)),
  getAuthConfig: () => apiClient<AuthConfig>("/auth/config"),

  getCurrentUser: () => apiClient<UserProfile>("/users/me"),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiClient<void>("/users/me/password", post({ currentPassword, newPassword })),
  linkGoogle: (credential: string) =>
    apiClient<UserProfile>("/users/me/google", post({ credential })),

  // Users
  listTechnicians: () => apiClient<TechnicianItem[]>("/users/technicians"),
  updateTechnicianSkills: (id: string, skills: Category[]) =>
    apiClient<TechnicianItem>(`/users/${id}/skills`, put({ skills })),
  updateCapacity: (id: string, maxActiveTickets: number, teamLead: boolean) =>
    apiClient<TechnicianItem>(`/users/${id}/capacity`, put({ maxActiveTickets, teamLead })),
  listUsers: () => apiClient<UserAccount[]>("/users"),
  createUser: (data: {
    username: string;
    fullName: string;
    email?: string;
    role: Role;
    password: string;
  }) => apiClient<UserAccount>("/users", post(data)),
  updateUserRole: (id: string, role: Role) =>
    apiClient<UserAccount>(`/users/${id}/role`, put({ role })),
  updateUserEmail: (id: string, email: string) =>
    apiClient<UserAccount>(`/users/${id}/email`, put({ email })),
  setUserActive: (id: string, active: boolean) =>
    apiClient<UserAccount>(`/users/${id}/active`, put({ active })),

  // Catalogue
  listServices: (includeRetired = false) =>
    apiClient<ItService[]>(`/catalog/services${includeRetired ? "?includeRetired=true" : ""}`),
  createService: (data: Omit<ItService, "id">) =>
    apiClient<ItService>("/catalog/services", post(data)),
  updateService: (id: string, data: Omit<ItService, "id">) =>
    apiClient<ItService>(`/catalog/services/${id}`, put(data)),
  listSlaPolicies: () => apiClient<SlaPolicy[]>("/catalog/sla-policies"),
  previewPriority: (impact: Impact, urgency: Urgency, serviceId?: string) =>
    apiClient<PriorityPreview>(
      `/catalog/priority-preview?impact=${impact}&urgency=${urgency}${serviceId ? `&serviceId=${serviceId}` : ""}`
    ),
  updateSlaPolicy: (priority: Priority, responseMinutes: number, resolutionMinutes: number) =>
    apiClient<SlaPolicy>(
      `/catalog/sla-policies/${priority}`,
      put({ responseMinutes, resolutionMinutes })
    ),

  // Incidents
  createIncident: (data: {
    title: string;
    description: string;
    category: Category;
    impact: Impact;
    urgency: Urgency;
    serviceId?: string;
    aiSuggestionAccepted?: boolean;
  }) => apiClient<IncidentDetail>("/incidents", post(data)),

  // Assistant (Gemini, or built-in rules when it isn't configured)
  triage: (title: string, description: string) =>
    apiClient<TriageSuggestion>("/incidents/triage", post({ title, description })),
  selfHelp: (title: string, description: string) =>
    apiClient<AssistantAdvice>("/incidents/self-help", post({ title, description })),
  selfHelpFeedback: (helped: boolean) =>
    apiClient<void>("/incidents/self-help/feedback", post({ helped })),
  suggestFix: (id: string) => apiClient<AssistantAdvice>(`/incidents/${id}/ai/suggest-fix`, post()),
  aiSummary: (id: string) => apiClient<AssistantSummary>(`/incidents/${id}/ai/summary`, post()),

  listIncidents: (params: IncidentQuery = {}) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== "") query.set(key, String(value));
    }
    const qs = query.toString();
    return apiClient<PagedResponse<IncidentSummary>>(`/incidents${qs ? `?${qs}` : ""}`);
  },

  suggestCategory: (title: string, description: string) =>
    apiClient<CategorySuggestion>("/incidents/suggest-category", post({ title, description })),
  checkDuplicates: (title: string, description: string) =>
    apiClient<SimilarIncident[]>("/incidents/check-duplicates", post({ title, description })),

  getIncident: (id: string) => apiClient<IncidentDetail>(`/incidents/${id}`),
  assignIncident: (id: string, technicianId?: string) =>
    apiClient<IncidentDetail>(
      `/incidents/${id}/assign`,
      post(technicianId ? { technicianId } : {})
    ),
  acknowledge: (id: string) => apiClient<IncidentDetail>(`/incidents/${id}/acknowledge`, post()),
  startWork: (id: string) => apiClient<IncidentDetail>(`/incidents/${id}/start`, post()),
  requestInfo: (id: string, question: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/request-info`, post({ question })),
  provideInfo: (id: string, answer: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/provide-info`, post({ answer })),
  resolveIncident: (id: string, resolutionSummary: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/resolve`, post({ resolutionSummary })),
  closeIncident: (id: string, feedback?: { rating?: number; remarks?: string }) =>
    apiClient<IncidentDetail>(`/incidents/${id}/close`, post(feedback ?? {})),
  cancelIncident: (id: string, reason: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/cancel`, post({ reason })),
  overridePriority: (id: string, priority: Priority, remarks: string) =>
    apiClient<IncidentDetail>(`/incidents/${id}/priority`, put({ priority, remarks })),

  // All issues (read-only, every signed-in user)
  listIssues: (params: {
    status?: IncidentStatus;
    category?: Category;
    serviceId?: string;
    searchText?: string;
    createdFrom?: string;
    createdTo?: string;
    page?: number;
    size?: number;
  }) => {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== "") query.set(key, String(value));
    }
    const qs = query.toString();
    return apiClient<PagedResponse<PublicIssue>>(`/issues${qs ? `?${qs}` : ""}`);
  },
  getIssue: (id: string) => apiClient<PublicIssue>(`/issues/${id}`),

  // Comments
  addComment: (id: string, commentText: string, isInternal: boolean) =>
    apiClient<CommentItem>(`/incidents/${id}/comments`, post({ commentText, isInternal })),
  getComments: (id: string) => apiClient<CommentItem[]>(`/incidents/${id}/comments`),

  // Audit
  getAuditLogs: (id: string) => apiClient<AuditLogItem[]>(`/incidents/${id}/audit-logs`),
  getRecentAuditLogs: (limit = 200) =>
    apiClient<AuditLogItem[]>(`/reports/audit-logs?limit=${limit}`),

  // Intelligence
  getSimilarIncidents: (id: string) => apiClient<SimilarIncident[]>(`/incidents/${id}/similar`),

  // Reports
  getReport: () => apiClient<Report>("/reports/overview"),
  getMyWorkload: () => apiClient<Workload>("/reports/my-workload"),
  getTrends: (days: TrendRange) => apiClient<Trends>(`/reports/trends?days=${days}`),

  // Notifications
  getNotifications: () => apiClient<NotificationInbox>("/notifications"),
  markNotificationRead: (id: string) => apiClient<void>(`/notifications/${id}/read`, post()),
  markAllNotificationsRead: () => apiClient<void>("/notifications/read-all", post()),

  // Admin
  getAssignmentConfig: () => apiClient<AssignmentConfig>("/admin/config/assignment"),
  updateAssignmentConfig: (strategy: string) =>
    apiClient<AssignmentConfig>("/admin/config/assignment", put({ strategy })),
  triggerSlaEvaluation: () => apiClient<SlaEvaluationSummary>("/admin/sla/evaluate", post()),
};
