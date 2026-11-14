package pl.rynek.tiktokintegration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TiktokIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiktokIntegrationServiceApplication.class, args);
    }
}
