/**
 * Notifications: sends and shows notifications to members. E-mails go through
 * {@code notifications.internal}; {@code notifications.realtime} streams thin change signals of a
 * household to its open clients over Server-Sent Events (ADR-019). Reacts to the public domain
 * events of {@code household} and reads preferences from {@code usersettings}; no module
 * references {@code notifications}. {@code model} holds the types generated from
 * {@code api/openapi.yml}, which Spring Modulith sees as a module of its own.
 */
@ApplicationModule(allowedDependencies = {"household", "usersettings", "model"})
package eu.wiegandt.librehousehold.notifications;

import org.springframework.modulith.ApplicationModule;
