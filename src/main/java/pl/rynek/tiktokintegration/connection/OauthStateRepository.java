package pl.rynek.tiktokintegration.connection;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OauthStateRepository extends JpaRepository<OauthState, String> {

    @Modifying
    @Query("delete from OauthState s where s.createdAt < :before")
    int deleteOlderThan(Instant before);
}
