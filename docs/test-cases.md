# Test Cases Specification

All test cases below are automated and run with `./mvnw test` in `backend/`. 104 tests (JUnit,
including the ArchUnit architecture rules) across unit, integration (Spring Boot + H2 in PostgreSQL mode) and architecture
suites cover them. Every row names the test method that proves it. External services are never
called: Gemini and Brevo are tested against a mocked HTTP server, and the integration tests run
without API keys, so they exercise the rule-based assistant and log-only email.

| Type | Meaning |
| :--- | :--- |
| Normal | Expected behaviour on valid input |
| Boundary | Edge values (limits, thresholds, empty input) |
| Invalid | Malformed or out-of-range input |
| Exception | Error handling and invalid state |
| AuthZ | Authentication and role-based authorization |
| Rule | Business rule |

## 1. Incident lifecycle and state pattern

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-01 | Normal | Walk a ticket REPORTED → ASSIGNED → IN_PROGRESS → PENDING_INFO → IN_PROGRESS → RESOLVED → CLOSED | Each transition succeeds; SLA pauses while waiting on the reporter | `IncidentStateTest.testFullLifecycleHappyPath` |
| TC-02 | Exception | Close a ticket that is still REPORTED (skipping the work) | `InvalidStateTransitionException` (HTTP 409) | `IncidentStateTest.testForbiddenTransitions` |
| TC-03 | Rule | Cancel from REPORTED and ASSIGNED; reassign while ASSIGNED or IN_PROGRESS | Allowed only in those states | `IncidentStateTest.testCancellation`, `testReassignment` |
| TC-04 | Normal | Reopen a resolved ticket | Returns to IN_PROGRESS and clears the resolution | `IncidentStateTest.testReopenResolvedIncident` |
| TC-05 | Normal | Create a ticket through the API | 201 Created, unique INC number, CREATED audit entry | `IncidentPersistenceTest.createsIncidentWithAuditAndUniqueNumbers` |
| TC-06 | Invalid | Create with blank title/description, a non-UUID id or an unknown enum value | 400 Bad Request with a readable message | `IncidentControllerTest.testCreateIncidentValidationFailure`, `malformedRequestsReturnBadRequest` |

## 2. Priority, services and SLA

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-07 | Normal | All 9 impact × urgency combinations | Matches the documented priority matrix | `MatrixPriorityStrategyTest.testAllPriorityMatrixCells` |
| TC-08 | Rule | Weighted strategy on a medium-criticality service | Identical to the matrix for all 9 cells | `IntelligenceAndPolicyTest.weightedMatchesMatrixAtMediumCriticality` |
| TC-09 | Boundary | Medium/medium on CRITICAL, HIGH and LOW services | P1, P2, and LOW/LOW stays P4 | `IntelligenceAndPolicyTest.criticalityShiftsPriority` |
| TC-10 | Normal | Report against the Payment Application (critical) | Medium/medium becomes P1; service shown on the ticket and in reports | `IncidentPersistenceTest.criticalServiceRaisesPriorityAndShowsInReports` |
| TC-11 | Normal | Initial SLA deadlines per priority | P1 5m/30m, P2 15m/2h, P3 1h/8h, P4 4h/24h | `SlaWindowTest.testInitialDeadlines` |
| TC-12 | Rule | Administrator-configured SLA targets | New tickets use the configured targets | `IntelligenceAndPolicyTest.factoryUsesConfiguredSla` |
| TC-13 | Invalid | SLA policy with zero response or resolution shorter than response | Rejected | `IntelligenceAndPolicyTest.slaPolicyValidation` |
| TC-14 | Boundary | 5h / 6h30 / 8h01 into an 8-hour SLA | On track / at risk (last quarter) / breached | `SlaWindowTest.testBreachEvaluation` |
| TC-15 | Rule | Pause for 45 minutes while waiting on the reporter | Deadline moves out by exactly 45 minutes | `SlaWindowTest.testPauseAndResume` |
| TC-16 | Rule | Breached ticket evaluated repeatedly | Priority raised once, one audit entry | `SlaScheduledEvaluatorTest.persistsWarningsAndEscalatesOnlyOnFirstBreach` |
| TC-17 | Rule | Resolved ticket past its deadline | Not counted as a breach while awaiting confirmation | `SlaScheduledEvaluatorTest.resolvedTicketsDoNotBreachWhileWaitingForClosure` |
| TC-18 | Rule | Nobody responds by the response deadline | Escalated once; administrators notified | `SlaScheduledEvaluatorTest.escalatesOnceWhenNobodyRespondsInTime` |

## 3. Assignment strategies

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-19 | Normal | Round-robin over three technicians | Cycles in order | `AssignmentStrategyTest.testRoundRobin` |
| TC-20 | Normal | Least-loaded | Technician with the fewest open tickets | `AssignmentStrategyTest.testLeastLoaded` |
| TC-21 | Rule | Skill match with several specialists, and with none | Least-loaded specialist; falls back to everyone | `AssignmentStrategyTest.testSkillMatch` |
| TC-22 | Normal | Ticket created while a technician is available | Auto-assigned with an ASSIGNED audit entry | `IncidentPersistenceTest.createsAssignedIncidentWithAudit` |

## 4. Intelligent features

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-23 | Normal | Similarity of identical, disjoint and overlapping texts | 1.0, 0.0 and the exact Jaccard value | `JaccardSimilarityStrategyTest.*` |
| TC-24 | Boundary | Similarity with null or empty text | 0.0, no exception | `JaccardSimilarityStrategyTest.testEdgeCases` |
| TC-25 | Normal | Draft that matches an existing open ticket | Shown as a possible duplicate | `IncidentPersistenceTest.warnsAboutDuplicatesAndSuggestsCategory` |
| TC-26 | AuthZ | Duplicate check by a different employee | Someone else's ticket is not revealed | `IncidentPersistenceTest.warnsAboutDuplicatesAndSuggestsCategory` |
| TC-27 | Normal | "Wi-Fi keeps dropping … VPN" / "Account locked … password reset" | NETWORK / ACCESS suggested with matched keywords | `IntelligenceAndPolicyTest.suggestsCategoryFromText` |
| TC-28 | Boundary | Text with no domain keywords | No suggestion | `IntelligenceAndPolicyTest.suggestsCategoryFromText` |

## 5. Authentication and role-based access

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-29 | Normal | Login with valid credentials | 200 with a JWT and profile | `AuthControllerTest.testLoginSuccess` |
| TC-30 | AuthZ | Disabled account logs in or reuses an old token | 401 for both | `UserAccessPersistenceTest.disabledUsersCannotLoginOrReuseToken` |
| TC-31 | AuthZ | Token for a deleted user | 401, not 500 | `UserAccessPersistenceTest.deletedUsersTokensReturnUnauthorizedInsteadOfServerError` |
| TC-32 | AuthZ | Unauthenticated request to a protected endpoint | 401 | `HealthControllerTest.unmappedEndpointsShouldBeDeniedByDefault` |
| TC-33 | AuthZ | Ticket list as employee, technician, idle technician, admin, root | Own / assigned / none / all / all | `IncidentPersistenceTest.listIsScopedByRole` |
| TC-34 | AuthZ | Another employee reads or changes someone's ticket | 404 (existence not revealed) | `IncidentPersistenceTest.otherEmployeesCannotReadCommentsOrChangeIncident` |
| TC-35 | AuthZ | Non-assigned technician starts work; assignee closes instead of reporter | 404 / 403; reporter can close | `IncidentPersistenceTest.onlyAssigneeWorksTicketAndOnlyReporterClosesIt` |
| TC-36 | AuthZ | Technician tries to assign | 403; root can | `IncidentPersistenceTest.onlyAdminsAssignTickets` |
| TC-37 | AuthZ | Admin promotes to technician, then to administrator; second root | Technician OK; administrator 403 (root only); root 400 | `UserAccessPersistenceTest.adminsManageStaffButOnlyRootGrantsAdmin` |
| TC-38 | Invalid | Create account with a short password | 400; valid account can then sign in | `UserAccessPersistenceTest.adminsCreatePasswordAccounts` |
| TC-39 | AuthZ | Self-registration asking for the ROOT role; duplicate username | Always EMPLOYEE; duplicate rejected | `UserAccessPersistenceTest.selfRegistrationCreatesEmployees` |
| TC-40 | AuthZ | Password login for a Google-only account | 401 | `UserAccessPersistenceTest.googleOnlyAccountsCannotUsePasswordLogin` |
| TC-41 | Normal | Google sign-in without a configured client id | Button hidden; endpoint returns 400 | `UserAccessPersistenceTest.googleSignInIsOffUntilConfigured` |
| TC-42 | Invalid | Skills update with null entries or for a non-technician | 400 | `UserAccessPersistenceTest.rejectsNullSkillsAndNonTechnicians` |

## 6. Feedback, persistence and architecture

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-43 | Boundary | Rate a closed ticket 0, 6 and 5 | 0 and 6 rejected, 5 stored | `IntelligenceAndPolicyTest.feedbackBounds` |
| TC-44 | Rule | Repeated skill updates | Existing rows kept, no duplicate-key failure | `UserAccessPersistenceTest.repeatedSkillUpdatesKeepExistingRows` |
| TC-45 | Rule | Domain layer depends on Spring or infrastructure | Build fails (ArchUnit) | `DomainIsolationTest.*` |

## 7. Updates thread and All issues

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-46 | Normal | Start, ask a question, reporter answers, resolve, close | Thread reads PICKUP, QUESTION, ANSWER, RESOLUTION, STATUS in order | `UpdatesAndIssueBoardTest.lifecycleActionsAppearInTheUpdatesThread` |
| TC-47 | AuthZ | Another employee opens the issue in All issues after a public comment and an internal note | 200 with one update, author shown as "Reporter"; no reporter name, no internal note | `UpdatesAndIssueBoardTest.everyoneCanReadIssuesWithoutReporterIdentityOrInternalNotes` |
| TC-48 | AuthZ | Same employee opens the ticket through `/incidents/{id}` | 404: All issues is read-only and doesn't widen ticket access | `UpdatesAndIssueBoardTest.everyoneCanReadIssuesWithoutReporterIdentityOrInternalNotes` |

## 8. Acknowledge, capacity and escalation

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-49 | Exception | Acknowledge a REPORTED ticket, or acknowledge twice | `InvalidStateTransitionException`; ASSIGNED → ACKNOWLEDGED → IN_PROGRESS works | `EscalationAndCapacityTest.acknowledgeSitsBetweenAssignedAndInProgress` |
| TC-50 | Rule | Reassign an acknowledged ticket | Back to ASSIGNED with the new assignee | `EscalationAndCapacityTest.reassigningAnAcknowledgedTicketNeedsTheNewTechnicianToAcceptIt` |
| TC-51 | Normal | Technician acknowledges through the API, then starts | ACKNOWLEDGED with `respondedAt` set; one PICKUP update, not two | `AcknowledgeAndCapacityTest.acknowledgingMeetsTheResponseTargetAndTellsTheReporter` |
| TC-52 | Rule | Only technician is at their limit; new ticket; admin assigns it to them; limit raised | Ticket stays REPORTED; 409 mentioning the limit; after raising, assignment succeeds | `AcknowledgeAndCapacityTest.aTechnicianAtTheirLimitIsNotGivenMoreWork` |
| TC-53 | AuthZ | Technician sets their own capacity | 403 | `AcknowledgeAndCapacityTest.onlyAdministratorsSetCapacity` |
| TC-54 | Rule | Capacity decorator over least-loaded with technicians at 5/5, 3/5 and 4/10 open tickets | The 3/5 one is chosen (the full one is skipped); with only the full one, nobody | `EscalationAndCapacityTest.capacityDecoratorSkipsTechniciansAtTheirLimit` |
| TC-55 | Boundary | One minute after reporting | No escalation | `EscalationAndCapacityTest.nothingEscalatesBeforeTheResponseDeadline` |
| TC-56 | Rule | Just past the response deadline, again at once, then past twice the target | TEAM_LEAD, then nothing, then ADMINISTRATOR; `escalatedAt` keeps the first time | `EscalationAndCapacityTest.chainClimbsOneLevelAtATime` |
| TC-57 | Rule | Acknowledged ticket an hour past the response deadline | No response escalation | `EscalationAndCapacityTest.acknowledgedTicketsDoNotEscalateForResponse` |
| TC-58 | Rule | P1 ticket breaches its resolution target, evaluated twice | IT_MANAGER once | `EscalationAndCapacityTest.breachedCriticalTicketGoesToTheItManager` |
| TC-59 | Rule | Scheduler runs twice on an unacknowledged ticket, long overdue | First run L1 (assignee unchanged), second L2 and reassigned to the technician with room, never to the full one | `SlaScheduledEvaluatorTest.administratorLevelReassignsToSomeoneWithRoom` |

## 9. Rate limiting and lockout

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-60 | Boundary | Six-per-minute bucket: six requests, then a seventh | Six allowed (remaining counts down to 0), seventh refused with `retryAfterSeconds` 10 | `InMemoryRateLimiterTest.allowsABurstUpToCapacityThenRefuses` |
| TC-61 | Rule | Empty bucket, clock +10 s, then +1 hour | One token back after 10 s; after an hour full again (never above capacity) | `InMemoryRateLimiterTest.refillsGraduallyAndNeverAboveCapacity` |
| TC-62 | Rule | Different keys and policies | Separate buckets | `InMemoryRateLimiterTest.keysAndPoliciesHaveSeparateBuckets` |
| TC-63 | Boundary | 11th sign-in attempt in a minute from one address | 429 with `Retry-After`, `RateLimit-Remaining: 0`, "Too many requests…"; another address still gets 401 | `RateLimitingTest.theEleventhSignInAttemptInAMinuteIsRefused` |
| TC-64 | Invalid | Each attempt sends a different forged `X-Forwarded-For` | Still 429 on the 11th (header not trusted by default) | `RateLimitingTest.aForgedForwardedForHeaderDoesNotResetTheLimit` |
| TC-65 | Rule | Five wrong passwords for one username from five addresses, then the right one | 401 "Invalid username or password" (locked) | `RateLimitingTest.fiveWrongPasswordsLockTheAccountFromAnyAddress` |
| TC-66 | Rule | Assistant calls over its (test) allowance of 2 | Third call 429 | `RateLimitingTest.theAssistantHasItsOwnTighterAllowance` |

## 10. Assistant

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-67 | Normal | Rules: "Wi-Fi outage … whole floor … urgent" | NETWORK, HIGH impact, HIGH urgency, with a reason | `RuleBasedIncidentAssistantTest.widespreadBlockingNetworkProblemIsSerious` |
| TC-68 | Boundary | Rules: one person's minor email issue | LOW / LOW, service "Email" | `RuleBasedIncidentAssistantTest.aSinglePersonsMinorIssueStaysLow` |
| TC-69 | Normal | Rules: self-help with a past fix | 2–4 steps, the past fix first | `RuleBasedIncidentAssistantTest.selfHelpLeadsWithPastFixesThenGenericSteps` |
| TC-70 | Normal | Gemini triage | Request carries a response schema; answer parsed into category, impact, urgency, service | `GeminiAssistantAdapterTest.triageSendsASchemaBoundRequestAndParsesTheAnswer` |
| TC-71 | Invalid | Gemini names a service we didn't offer | Service dropped | `GeminiAssistantAdapterTest.aServiceWeDidNotOfferIsDropped` |
| TC-72 | Invalid | Gemini returns an unknown enum value | `AssistantUnavailableException`, so the caller falls back to rules | `GeminiAssistantAdapterTest.unexpectedValuesAreRejectedSoTheCallerFallsBack` |
| TC-73 | Exception | Malformed JSON or a server error from Gemini | `AssistantUnavailableException` | `GeminiAssistantAdapterTest.malformedJsonAndServerErrorsBecomeUnavailable` |
| TC-74 | Boundary | Gemini returns more than four steps | Trimmed to four | `GeminiAssistantAdapterTest.adviceIsTrimmedToFourSteps` |
| TC-75 | Rule | No API key | Adapter disabled; no HTTP call is made | `GeminiAssistantAdapterTest.withoutAKeyItIsDisabledAndNeverCallsOut` |
| TC-76 | Normal | `POST /incidents/triage` without a key | 200, `source: "rules"`, NETWORK / HIGH and a predicted priority | `AssistantEndpointsTest.triageFallsBackToRulesWithoutAKey` |
| TC-77 | AuthZ | Self-help for a printer jam when another user's resolved ticket matches | Fix text included; no ticket number, title or id | `AssistantEndpointsTest.selfHelpUsesPastFixesButNeverOtherPeoplesTickets` |
| TC-78 | Normal | Self-help feedback `{helped: true}` | 204 and a stored row | `AssistantEndpointsTest.selfHelpFeedbackIsRecorded` |
| TC-79 | AuthZ | Employee asks for a ticket summary; technician asks for summary and fix | 403; 200 with summary / steps | `AssistantEndpointsTest.fixSuggestionsAndSummariesAreForStaffOnly` |

## 11. Email and live updates

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-80 | Normal | Brevo adapter sends a ticket notification | One `POST /v3/smtp/email` with escaped HTML and a link to the ticket | `BrevoEmailChannelAdapterTest.sendsATransactionalEmailWithAnEscapedBodyAndATicketLink` |
| TC-81 | Exception | Recipient has no email; Brevo returns 500 | No request; error logged, not thrown | `BrevoEmailChannelAdapterTest.skipsPeopleWithoutAnEmailAndSurvivesApiErrors` |
| TC-82 | AuthZ | Open `/events` without signing in | 401 | `LiveEventsTest.theStreamNeedsASignedInUser` |
| TC-83 | Normal | Notification sent while the recipient's stream is open | Stream has `ready`, then a `notification` event with the title | `LiveEventsTest.notificationsArriveOnTheRecipientsStream` |
| TC-84 | Invalid | Admin sets an email used by another account (different case); employee sets an email | 400; 403 | `LiveEventsTest.administratorsSetEmailsAndAddressesStayUnique` |

## 12. Major incidents and service status

| ID | Type | Scenario | Expected result | Automated test |
| :--- | :--- | :--- | :--- | :--- |
| TC-85 | AuthZ | Employee links a ticket under another | 403; administrator can | `MajorIncidentTest.resolvingTheMainTicketResolvesEveryLinkedOne` |
| TC-86 | Normal | Report with `relatedIncidentId`, and link a second ticket | Both appear in the main ticket's `childIncidents` | `MajorIncidentTest.resolvingTheMainTicketResolvesEveryLinkedOne` |
| TC-87 | Rule | Service status with a major incident and two linked tickets open | OUTAGE, 3 open tickets, 1 major incident | `MajorIncidentTest.resolvingTheMainTicketResolvesEveryLinkedOne` |
| TC-88 | Rule | Resolve the main ticket | Both linked tickets RESOLVED with the same fix in their notes; service back to OPERATIONAL | `MajorIncidentTest.resolvingTheMainTicketResolvesEveryLinkedOne` |
| TC-89 | Invalid | Link under a linked ticket, link a main ticket under another, link to itself | 400 each; unlink then clears `parentIncident` | `MajorIncidentTest.linksAreOneLevelDeep` |
| TC-90 | AuthZ / Invalid | Trends as an employee, and with `days=14` | 403 for the employee; 400 for a range other than 7, 30 or 90 | `TrendsReportTest.onlyAdministratorsSeeTrendsAndOnlyForTheOfferedRanges` |
| TC-91 | Normal | Create and resolve a ticket, then read 7-day trends | It counts in that day's created and resolved, and in the week's SLA compliance | `TrendsReportTest.aResolvedTicketShowsUpInTheDailySeriesAndCompliance` |

## 13. Manual end-to-end checks (demo)

| ID | Workflow | Steps | Expected result |
| :--- | :--- | :--- | :--- |
| M-01 | Report → prioritise → assign → notify | `emp` reports "checkout failing" on Payment Application, medium/medium | P1, auto-assigned, technician inbox shows the assignment |
| M-02 | Investigate → SLA → resolve → notify | Technician starts work, asks a question, reporter replies, technician resolves, reporter closes with a rating | SLA paused while waiting; reporter notified on resolve; rating visible in Reports |
| M-03 | Duplicates and correlation | Report two similar tickets on one service | Second report warns of the first; Reports shows "Possible common cause" for the service |
| M-04 | Escalation | Leave a P1 ticket unacknowledged for 5 minutes, then 5 more | First the team lead (`tech_lead`) is notified; then administrators are, and the ticket is reassigned to a technician with room |
| M-05 | Role separation | Sign in as each role | Employee: own tickets; technician: assigned queue; admin: all tickets, Reports, Audit log, Users, Settings |
| M-06 | Live updates | Open the same ticket as `emp` and `tech_net` in two browsers; the technician acknowledges | The reporter's page updates without a reload and a toast says the ticket was picked up |
| M-07 | Major incident | Two employees report the same outage; `admin` links the second under the first; the technician resolves the first | Both tickets resolved with the same fix, both reporters notified; service status back to Operational |
| M-08 | Board | As a technician, drag a card from Assigned to Acknowledged, then move another with the keyboard "Move to" control | Each move runs the matching lifecycle action and the card shows in its new column |
| M-09 | Trends | As `admin`, open the trends report and switch between 7, 30 and 90 days | Created vs resolved, SLA compliance, time to resolve, first response, escalations and self-help deflection update for the window |
