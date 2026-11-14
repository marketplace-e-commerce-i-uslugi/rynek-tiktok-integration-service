package pl.rynek.tiktokintegration.connection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConnectionRepository extends JpaRepository<TiktokConnection, UUID> {

    /** Połączenia, którym access token wygasa przed {@code before}. */
    @Query("select c from TiktokConnection c where c.accessExpiresAt < :before")
    List<TiktokConnection> expiringBefore(Instant before);

    /** Połączenia bez synchronizacji albo z synchronizacją sprzed {@code before}. */
    @Query("select c from TiktokConnection c where c.lastSyncAt is null or c.lastSyncAt < :before")
    List<TiktokConnection> notSyncedSince(Instant before);
}
