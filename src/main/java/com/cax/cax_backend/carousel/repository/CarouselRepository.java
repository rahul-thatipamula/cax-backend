package com.cax.cax_backend.carousel.repository;

import com.cax.cax_backend.carousel.model.Carousel;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface CarouselRepository extends MongoRepository<Carousel, String> {
    List<Carousel> findByIsActiveTrueOrderByDisplayOrderAsc();
    List<Carousel> findByCollegeIdAndIsActiveTrueOrderByDisplayOrderAsc(String collegeId);
    List<Carousel> findByCollegeIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
    boolean existsByTitleAndCollegeIdIsNull(String title);
    List<Carousel> findAllByOrderByDisplayOrderAsc();
    List<Carousel> findByCollegeIdOrderByDisplayOrderAsc(String collegeId);
    List<Carousel> findByCollegeIdIsNullOrderByDisplayOrderAsc();
}
