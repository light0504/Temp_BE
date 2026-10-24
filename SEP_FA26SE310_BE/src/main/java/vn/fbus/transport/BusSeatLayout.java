package vn.fbus.transport;

import java.util.ArrayList;
import java.util.List;

public final class BusSeatLayout {
    private BusSeatLayout() {}

    public static List<String> codes(int capacity) {
        if (capacity < 1 || capacity > 100) throw new IllegalArgumentException("Invalid bus capacity");
        int remainder = capacity % 4;
        // For 45/49 seats, move the spare seat into a five-seat rear bench.
        int rearCount = remainder == 1 && capacity >= 5 ? 5 : remainder == 0 ? 4 : remainder;
        int regularRows = (capacity - rearCount) / 4;
        var codes = new ArrayList<String>(capacity);
        for (int row = 1; row <= regularRows + 1; row++) {
            int count = row <= regularRows ? 4 : rearCount;
            for (int column = 0; column < count; column++) codes.add(row + String.valueOf((char) ('A' + column)));
        }
        return List.copyOf(codes);
    }
}
