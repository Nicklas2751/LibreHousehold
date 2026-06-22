package eu.wiegandt.librehousehold.tasks;

import eu.wiegandt.librehousehold.model.TaskStatsByMember;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TaskStatisticsProvider {

    List<TaskStatsByMember> getTaskStatsByMember(UUID householdId, LocalDate from, LocalDate to);
}
