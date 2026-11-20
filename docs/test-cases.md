# Test Cases Specification

All test cases below are automated and run with `./mvnw test` in `backend/`. 54 automated tests
across unit, integration (Spring Boot + H2 in PostgreSQL mode) and architecture suites cover
them. Every row names the test method that proves it.

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

## 7. Manual end-to-end checks (demo)

| ID | Workflow | Steps | Expected result |
| :--- | :--- | :--- | :--- |
| M-01 | Report → prioritise → assign → notify | `emp` reports "checkout failing" on Payment Application, medium/medium | P1, auto-assigned, technician inbox shows the assignment |
| M-02 | Investigate → SLA → resolve → notify | Technician starts work, asks a question, reporter replies, technician resolves, reporter closes with a rating | SLA paused while waiting; reporter notified on resolve; rating visible in Reports |
| M-03 | Duplicates and correlation | Report two similar tickets on one service | Second report warns of the first; Reports shows "Possible common cause" for the service |
| M-04 | Escalation | Leave a P1 ticket untouched for 5 minutes | Ticket shows "Escalated"; administrators receive an inbox notification |
| M-05 | Role separation | Sign in as each role | Employee: own tickets; technician: assigned queue; admin: all tickets, Reports, Audit log, Users, Settings |
