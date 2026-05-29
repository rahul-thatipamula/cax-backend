package com.cax.cax_backend.college.seeder;

import com.cax.cax_backend.college.model.College;
import com.cax.cax_backend.college.repository.CollegeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollegeSeeder implements CommandLineRunner {

    private final CollegeRepository collegeRepository;

    @Override
    public void run(String... args) {
        if (collegeRepository.count() == 0) {
            log.info("Seeding Telangana BTech colleges into the database...");

            List<College> colleges = Arrays.asList(
                createCollege("JNTUH College of Engineering Hyderabad", "JNTUH-CEH", "Kukatpally, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Government"),
                createCollege("University College of Engineering, Osmania University", "UCE-OU", "Amberpet, Hyderabad, Telangana", "Osmania University", "Government"),
                createCollege("Chaitanya Bharathi Institute of Technology", "CBIT", "Gandipet, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("Vasavi College of Engineering", "VCE", "Ibrahimbagh, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("VNR Vignana Jyothi Institute of Engineering and Technology", "VNR-VJIET", "Bachupally, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous"),
                createCollege("Gokaraju Rangaraju Institute of Engineering and Technology", "GRIET", "Bachupally, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous"),
                createCollege("CVR College of Engineering", "CVR", "Ibrahimpatnam, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous"),
                createCollege("MVSR Engineering College", "MVSR", "Nadergul, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("Keshav Memorial Institute of Technology", "KMIT", "Narayanguda, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous"),
                createCollege("Mahatma Gandhi Institute of Technology", "MGIT", "Gandipet, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("BVRIT Hyderabad College of Engineering for Women", "BVRITH", "Bachupally, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous"),
                createCollege("Sreenidhi Institute of Science and Technology", "SNIST", "Ghatkesar, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous"),
                createCollege("Vardhaman College of Engineering", "VARDHAMAN", "Shamshabad, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous"),
                createCollege("Anurag University", "ANURAG", "Ghatkesar, Hyderabad, Telangana", "Anurag University", "Private / Autonomous"),
                createCollege("G. Narayanamma Institute of Technology and Science (for Women)", "GNITS", "Shaikpet, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Private / Autonomous")
            );

            collegeRepository.saveAll(colleges);
            log.info("Successfully seeded {} colleges.", colleges.size());
        } else {
            log.info("Colleges collection is not empty. Skipping seeding.");
        }
    }

    private College createCollege(String name, String code, String location, String university, String type) {
        return College.builder()
                .collegeName(name)
                .collegeCode(code)
                .location(location)
                .university(university)
                .type(type)
                .logoUrl("")
                .studentCount(0)
                .isActive(true)
                .createdAt(Instant.now())
                .build();
    }
}
