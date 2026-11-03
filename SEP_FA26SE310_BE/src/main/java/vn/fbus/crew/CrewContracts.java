package vn.fbus.crew;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import vn.fbus.transport.TransportRepository.TripView;

public final class CrewContracts {
 private CrewContracts() {}
 public record Operation(String status, int currentStopPosition, LocalDateTime startedAt, LocalDateTime completedAt) {}
 public record CrewTrip(TripView trip, Operation operation, boolean canDrive, boolean canManagePassengers) {}
 public record Detail(TripView trip, Operation operation, boolean canDrive, boolean canManagePassengers, List<Passenger> passengers) {}
 public record Passenger(long bookingId, String bookingCode, String fullName, String email, String seatCode,
   long boardingTripStopId, String boardingStop, int boardingPosition, long alightingTripStopId, String alightingStop,
   int alightingPosition, String status, LocalDateTime checkedInAt, LocalDateTime alightedAt) {}
 public record Progress(@NotNull @Pattern(regexp="START|ARRIVE|COMPLETE") String action,
   @NotNull @Min(-1) @Max(30) Integer expectedPosition) {}
 public record Attendance(@NotNull @Pattern(regexp="CHECK_IN|CHECK_OUT|NO_SHOW") String action) {}
 public record ReportInput(@NotNull @Pattern(regexp="VEHICLE|CAPACITY|DELAY|PASSENGER|OTHER") String category,
   @NotNull @Pattern(regexp="LOW|NORMAL|URGENT") String priority,
   @NotNull @Size(max=200) String location, @NotBlank @Size(max=1000) String description,
   @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,100}") String idempotencyKey) {}
 public record Resolution(@NotNull @Pattern(regexp="OPEN|IN_REVIEW|RESOLVED") String status, @Size(max=500) String resolution) {}
 public record Report(long reportId, long tripId, String tripCode, String routeName, String vehicle,
   String reporter, String reporterRole, String category, String priority, String location, String description,
   String status, LocalDateTime createdAt, String resolution, String resolvedBy, LocalDateTime updatedAt) {}
}

