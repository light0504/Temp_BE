package vn.fbus.recurrence;
import jakarta.validation.constraints.*;
import vn.fbus.common.Contracts;
import java.time.*;
import java.util.List;
public final class RecurrenceContracts {
    private RecurrenceContracts() {}
    public record Create(@NotNull @Positive Long routeId,@Positive Long vehicleId,
        @NotNull LocalDate startDate,@NotNull LocalTime time,@NotNull @Min(1) @Max(6) Integer slot,
        @Size(max=30) String vehicle,@Min(1) @Max(100) Integer capacity,@Positive Long driverId,@Size(max=150) String driver,
        @Positive Long busAttendantId,@Size(max=150) String busAttendant,
        @NotNull @Min(1) @Max(52) Integer intervalWeeks,@NotNull @Size(min=1,max=7) List<@NotNull @Min(1) @Max(7) Integer> weekdays,
        @NotNull @Pattern(regexp="NEVER|UNTIL|COUNT") String endType,LocalDate untilDate,@Min(1) @Max(1000) Integer occurrences,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,100}") String idempotencyKey) implements Contracts.TripVehicle {}
    public record Status(@NotNull @Pattern(regexp="ACTIVE|PAUSED") String status) {}
}
