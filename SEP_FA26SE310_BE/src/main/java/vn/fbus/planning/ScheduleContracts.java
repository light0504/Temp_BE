package vn.fbus.planning;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class ScheduleContracts {
    private ScheduleContracts() {}
    public record WishlistInput(@NotNull @Positive Long routeId,@NotNull @Positive Long boardingStopId,
        @NotNull @Positive Long alightingStopId,@NotNull LocalDate date,@NotNull @Min(1) @Max(6) Integer slot) {}
    public record PlanInput(@NotNull @Positive Long routeId,@NotNull @Positive Long boardingStopId,
        @NotNull @Positive Long alightingStopId,@NotNull LocalDate startDate,@NotNull LocalDate endDate,
        @NotNull @Size(min=1,max=7) List<@NotNull @Min(1) @Max(7) Integer> weekdays,
        @NotNull @Min(1) @Max(6) Integer slot,@Pattern(regexp="[A-Za-z0-9_-]{16,100}") String idempotencyKey,boolean wishlistMissingTrips) {}
}
