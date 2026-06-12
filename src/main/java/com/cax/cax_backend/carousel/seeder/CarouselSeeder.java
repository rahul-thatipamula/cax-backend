package com.cax.cax_backend.carousel.seeder;

import com.cax.cax_backend.carousel.model.Carousel;
import com.cax.cax_backend.carousel.repository.CarouselRepository;
import com.cax.cax_backend.common.enums.CarouselEnums.CarouselType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CarouselSeeder implements CommandLineRunner {

    private static final String CREATED_BY = "temporary-seed";

    private final CarouselRepository carouselRepository;

    @Override
    public void run(String... args) {
        List<Carousel> records = new ArrayList<>();



        if (!carouselRepository.existsByTitleAndCollegeIdIsNull("Build your student community")) {
            records.add(Carousel.builder()
                    .title("Build your student community")
                    .description("Explore clubs, meet teams, and join communities on campus.")
                    .imageUrl("https://images.unsplash.com/photo-1523240795612-9a054b0db644?auto=format&fit=crop&w=1200&q=80")
                    .actionLink("/clubs")
                    .type(CarouselType.FEATURED)
                    .displayOrder(2)
                    .isActive(true)
                    .targetAudience("all")
                    .createdBy(CREATED_BY)
                    .createdAt(Instant.now())
                    .build());
        }

        records.forEach(record -> record.setActionUrl(record.getActionLink()));
        records.forEach(record -> record.setUpdatedAt(Instant.now()));

        if (!records.isEmpty()) {
            carouselRepository.saveAll(records);
            log.info("Seeded {} temporary carousel records.", records.size());
        } else {
            log.info("Temporary carousel records already exist. Skipping carousel seeding.");
        }
    }
}
