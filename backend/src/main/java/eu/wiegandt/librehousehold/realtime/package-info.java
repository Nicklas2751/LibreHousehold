/**
 * Realtime updates: streams thin change signals of a household to its members over Server-Sent
 * Events (ADR-019). Only depends on the public API of {@code household}; no module references
 * {@code realtime}.
 */
@ApplicationModule(allowedDependencies = "household")
package eu.wiegandt.librehousehold.realtime;

import org.springframework.modulith.ApplicationModule;
