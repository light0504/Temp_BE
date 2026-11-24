package vn.fbus.wishlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WishlistAllocationRunner {
    private static final Logger log=LoggerFactory.getLogger(WishlistAllocationRunner.class);
    private final WishlistAllocationService allocator;
    public WishlistAllocationRunner(WishlistAllocationService allocator){this.allocator=allocator;}
    @TransactionalEventListener
    public void created(TripCreated event){assign(event.tripId());}
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void retry(){for(long trip:allocator.pendingTrips())assign(trip);}
    private void assign(long trip){try{allocator.allocate(trip);}catch(RuntimeException failure){log.error("Wishlist allocation failed for trip {}; it will be retried",trip,failure);}}
}
