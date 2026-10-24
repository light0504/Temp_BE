package vn.fbus.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class Contracts {
    private Contracts() {}
    public record Login(@NotBlank @Size(max=254) String username, @NotBlank @Size(max=72) String password) {}
    public record Refresh(@NotBlank @Size(max=200) String refreshToken) {}
    public record NewStop(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,60}") String stopCode,
                          @NotBlank @Size(max=150) String name, @NotBlank @Size(max=300) String address) {}
    public record RoutePoint(@NotNull @Positive Long stopId, @NotNull @Min(0) @Max(1440) Integer offsetMinutes) {}
    public record NewRoute(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,60}") String routeCode,
                           @NotBlank @Size(max=150) String name,
                           @NotNull @Size(min=2,max=30) List<@NotNull @Valid RoutePoint> stops) {}
    public interface TripVehicle {
        Long vehicleId(); String vehicle(); Integer capacity(); Long driverId(); String driver();
        Long busAttendantId(); String busAttendant();
    }
    public record NewTrip(@NotNull @Positive Long routeId, @Positive Long vehicleId,
                          @NotNull LocalDate date, @NotNull LocalTime time, @NotNull @Min(1) @Max(6) Integer slot,
                          @Size(max=30) String vehicle, @Min(1) @Max(100) Integer capacity,
                          @Positive Long driverId, @Size(max=150) String driver,
                          @Positive Long busAttendantId, @Size(max=150) String busAttendant) implements TripVehicle {}
    public record BatchTrips(@NotNull @Positive Long routeId, @Positive Long vehicleId,
                             @NotNull @Size(min=1,max=90) List<@NotNull LocalDate> dates,
                             @NotNull LocalTime time, @NotNull @Min(1) @Max(6) Integer slot,
                             @Size(max=30) String vehicle, @Min(1) @Max(100) Integer capacity,
                             @Positive Long driverId, @Size(max=150) String driver,
                             @Positive Long busAttendantId, @Size(max=150) String busAttendant) implements TripVehicle {}
    public record VehicleInput(@NotBlank @Size(max=30) String licensePlate,
                               @NotNull @Min(1) @Max(100) Integer capacity,
                               @NotNull @Positive Long driverId, @Positive Long busAttendantId,
                               @NotNull @Pattern(regexp="ACTIVE|INACTIVE") String status) {}
    public record NewUser(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,100}") String username,
                          @NotBlank @Email @Size(max=254) String email,
                          @NotBlank @Size(min=8,max=72) String password,
                          @NotBlank @Size(max=150) String fullName,
                          @NotNull @Pattern(regexp="STUDENT|DRIVER|BUS_ATTENDANT|LECTURER") String role) {}
    public record TripStatus(@NotNull @Pattern(regexp="SCHEDULED|PAUSED") String status) {}
    public record NewBooking(@NotNull @Positive Long tripId, @NotNull @Positive Long boardingTripStopId,
                             @NotNull @Positive Long alightingTripStopId, @NotNull @Positive Long seatId,
                             @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,100}") String idempotencyKey) {}
    public record PreferredStop(@NotNull @Positive Long stopId) {}
}
