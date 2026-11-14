package pl.rynek.tiktokintegration.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Zadania w tle: odświeżanie tokenów przed wygaśnięciem i synchronizacja filmów (co {@code rynek.tiktok.sync-every}). */
@Component
@ConditionalOnProperty(name = "rynek.tiktok.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class TiktokJobs {

    private static final Logger log = LoggerFactory.getLogger(TiktokJobs.class);

    private final TiktokService tiktok;

    public TiktokJobs(TiktokService tiktok) {
        this.tiktok = tiktok;
    }

    @Scheduled(initialDelayString = "${rynek.tiktok.jobs-interval}", fixedDelayString = "${rynek.tiktok.jobs-interval}")
    void run() {
        try {
            tiktok.runJobs();
        } catch (RuntimeException e) {
            log.warn("Zadanie TikTok nie powiodło się: {}", e.getMessage());
        }
    }
}
