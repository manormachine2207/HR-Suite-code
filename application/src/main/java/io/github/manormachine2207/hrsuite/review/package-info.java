/**
 * Review-Pfad (ADR-013): Task-Inbox + Complete-API fuer hr-reviewer/tenant-admin,
 * mit synchronem Status-Sync auf den Antrag (kein Engine-Listener — der
 * Request-Thread traegt die Tenant-GUC, Job-Threads nicht).
 */
@org.springframework.modulith.ApplicationModule(displayName = "Review")
package io.github.manormachine2207.hrsuite.review;
