package vn.fbus.priority;
import jakarta.validation.constraints.*;
public final class PriorityContracts {
 private PriorityContracts(){}
 public record Entry(@NotBlank @Email @Size(max=254) String email,@NotNull @Pattern(regexp="STAFF|STUDENT") String group){}
 public record Remove(@NotBlank @Email @Size(max=254) String email){}
}

