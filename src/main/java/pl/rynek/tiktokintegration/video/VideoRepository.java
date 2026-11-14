package pl.rynek.tiktokintegration.video;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    Optional<Video> findByOwnerUserIdAndExternalId(UUID ownerUserId, String externalId);

    Optional<Video> findFirstByExternalIdAndStatus(String externalId, String status);

    Optional<Video> findFirstByOfferIdAndStatus(UUID offerId, String status);

    List<Video> findByOfferIdInAndStatus(Collection<UUID> offerIds, String status);

    List<Video> findByOwnerUserIdAndSource(UUID ownerUserId, String source);

    @Query("select v from Video v where v.ownerUserId = :userId and v.source = 'TIKTOK' and v.status = 'ACTIVE' order by v.createdAt desc, v.id")
    List<Video> accountVideos(UUID userId, Pageable page);

    @Query("select v from Video v where v.channelId = :channelId and v.status = 'ACTIVE' order by v.createdAt desc, v.id")
    List<Video> ofChannel(UUID channelId, Pageable page);

    @Query("select v from Video v where v.status = 'ACTIVE' and v.vertical = true order by v.createdAt desc, v.id")
    List<Video> shorts(Pageable page);

    /** Oferta ma najwyżej jeden film: zdejmuje ją z innych filmów przed przypisaniem nowego. */
    @Modifying
    @Query("update Video v set v.offerId = null where v.offerId = :offerId and v.id <> :keep")
    int unlinkOthers(UUID offerId, UUID keep);

    @Modifying
    @Query("update Video v set v.offerId = null where v.offerId = :offerId")
    int unlinkOffer(UUID offerId);
}
