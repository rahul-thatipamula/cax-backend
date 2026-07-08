package com.cax.cax_backend.carousel.repository;

import com.cax.cax_backend.carousel.model.Carousel;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface CarouselRepository extends MongoRepository<Carousel, String> {

    @Query(value = "{ 'isActive': true, 'deleted': { $ne: true } }", sort = "{ 'displayOrder': 1 }")
    List<Carousel> findByIsActiveTrueOrderByDisplayOrderAsc();

    @Query(value = "{ 'collegeId': ?0, 'isActive': true, 'deleted': { $ne: true } }", sort = "{ 'displayOrder': 1 }")
    List<Carousel> findByCollegeIdAndIsActiveTrueOrderByDisplayOrderAsc(String collegeId);

    @Query(value = "{ 'collegeId': null, 'isActive': true, 'deleted': { $ne: true } }", sort = "{ 'displayOrder': 1 }")
    List<Carousel> findByCollegeIdIsNullAndIsActiveTrueOrderByDisplayOrderAsc();

    boolean existsByTitleAndCollegeIdIsNull(String title);

    @Query(value = "{ 'deleted': { $ne: true } }", sort = "{ 'displayOrder': 1 }")
    List<Carousel> findAllByOrderByDisplayOrderAsc();

    @Query(value = "{ 'collegeId': ?0, 'deleted': { $ne: true } }", sort = "{ 'displayOrder': 1 }")
    List<Carousel> findByCollegeIdOrderByDisplayOrderAsc(String collegeId);

    @Query(value = "{ 'collegeId': null, 'deleted': { $ne: true } }", sort = "{ 'displayOrder': 1 }")
    List<Carousel> findByCollegeIdIsNullOrderByDisplayOrderAsc();
}
