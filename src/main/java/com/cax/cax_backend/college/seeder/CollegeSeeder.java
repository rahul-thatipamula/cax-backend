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
        long existing = collegeRepository.count();
        if (existing > 0) {
            log.info("Colleges in DB: {}. Skipping seeder to preserve existing college IDs.", existing);
            return;
        }
        log.info("Colleges in DB is empty. Seeding with full 250-college dataset...");

        {
            log.info("Seeding top 250 Indian BTech colleges into the database...");

            List<College> colleges = Arrays.asList(
                // ─── Telangana ───────────────────────────────────────────
                createCollege("JNTUH College of Engineering Hyderabad", "JNTUH-CEH", "Kukatpally, Hyderabad, Telangana", "Jawaharlal Nehru Technological University Hyderabad", "Government"),
                createCollege("University College of Engineering, Osmania University", "UCE-OU", "Amberpet, Hyderabad, Telangana", "Osmania University", "Government"),
                createCollege("Chaitanya Bharathi Institute of Technology", "CBIT", "Gandipet, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("Vasavi College of Engineering", "VCE", "Ibrahimbagh, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("VNR Vignana Jyothi Institute of Engineering and Technology", "VNR-VJIET", "Bachupally, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("Gokaraju Rangaraju Institute of Engineering and Technology", "GRIET", "Bachupally, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("CVR College of Engineering", "CVR", "Ibrahimpatnam, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("MVSR Engineering College", "MVSR", "Nadergul, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("Keshav Memorial Institute of Technology", "KMIT", "Narayanguda, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("Mahatma Gandhi Institute of Technology", "MGIT", "Gandipet, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("BVRIT Hyderabad College of Engineering for Women", "BVRITH", "Bachupally, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("Sreenidhi Institute of Science and Technology", "SNIST", "Ghatkesar, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("Vardhaman College of Engineering", "VARDHAMAN", "Shamshabad, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("Anurag University", "ANURAG", "Ghatkesar, Hyderabad, Telangana", "Anurag University", "Private / Autonomous"),
                createCollege("G. Narayanamma Institute of Technology and Science for Women", "GNITS", "Shaikpet, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("SR Engineering College", "SREC-WGL", "Warangal, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("JNTUH College of Engineering Manthani", "JNTUH-CEM", "Manthani, Karimnagar, Telangana", "JNTUH", "Government"),
                createCollege("JNTUH College of Engineering Sultanpur", "JNTUH-CES", "Sultanpur, Medak, Telangana", "JNTUH", "Government"),
                createCollege("Kakatiya Institute of Technology and Science", "KITS-WGL", "Warangal, Telangana", "Kakatiya University", "Government"),
                createCollege("Nizam Institute of Engineering and Technology", "NIET", "Nalgonda, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("TKR College of Engineering and Technology", "TKRCET", "Meerpet, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("Matrusri Engineering College", "MEC", "Saidabad, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("Muffakham Jah College of Engineering and Technology", "MJCET", "Banjara Hills, Hyderabad, Telangana", "Osmania University", "Private / Autonomous"),
                createCollege("Vignana Bharathi Institute of Technology", "VBIT", "Aushapur, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),
                createCollege("Sreyas Institute of Engineering and Technology", "SIET", "Nagole, Hyderabad, Telangana", "JNTUH", "Private / Autonomous"),

                // ─── Andhra Pradesh ──────────────────────────────────────
                createCollege("JNTUA College of Engineering Anantapur", "JNTUA-CEA", "Anantapur, Andhra Pradesh", "JNTUA", "Government"),
                createCollege("JNTUA College of Engineering Kalikiri", "JNTUA-CEK", "Kalikiri, Chittoor, Andhra Pradesh", "JNTUA", "Government"),
                createCollege("Andhra University College of Engineering", "AUCE", "Visakhapatnam, Andhra Pradesh", "Andhra University", "Government"),
                createCollege("Sri Venkateswara University College of Engineering", "SVUCE", "Tirupati, Andhra Pradesh", "Sri Venkateswara University", "Government"),
                createCollege("RVR & JC College of Engineering", "RVRJC", "Guntur, Andhra Pradesh", "Acharya Nagarjuna University", "Private / Autonomous"),
                createCollege("Gayatri Vidya Parishad College of Engineering", "GVP", "Visakhapatnam, Andhra Pradesh", "Andhra University", "Private / Autonomous"),
                createCollege("Prasad V. Potluri Siddhartha Institute of Technology", "PVPSIT", "Vijayawada, Andhra Pradesh", "Krishna University", "Private / Autonomous"),
                createCollege("Koneru Lakshmaiah Education Foundation University", "KLEF", "Vaddeswaram, Guntur, Andhra Pradesh", "KL University", "Deemed University"),
                createCollege("VIT-AP University", "VIT-AP", "Amaravati, Andhra Pradesh", "VIT-AP University", "Deemed University"),
                createCollege("Vignan's Foundation for Science Technology and Research", "VFSTR", "Vadlamudi, Guntur, Andhra Pradesh", "Vignan University", "Deemed University"),
                createCollege("Sree Vidyanikethan Engineering College", "SVEC-TPT", "Tirupati, Andhra Pradesh", "JNTUA", "Private / Autonomous"),
                createCollege("Narasaraopeta Engineering College", "NEC", "Narasaraopet, Guntur, Andhra Pradesh", "JNTUK", "Private / Autonomous"),
                createCollege("Anil Neerukonda Institute of Technology and Sciences", "ANITS", "Visakhapatnam, Andhra Pradesh", "Andhra University", "Private / Autonomous"),
                createCollege("AITAM Engineering College", "AITAM", "Tekkali, Srikakulam, Andhra Pradesh", "JNTUK", "Private / Autonomous"),
                createCollege("Bapatla Engineering College", "BEC", "Bapatla, Guntur, Andhra Pradesh", "JNTUK", "Private / Autonomous"),

                // ─── Tamil Nadu ───────────────────────────────────────────
                createCollege("Anna University", "AU-CEG", "Guindy, Chennai, Tamil Nadu", "Anna University", "Government / Autonomous"),
                createCollege("College of Engineering Guindy", "CEG", "Guindy, Chennai, Tamil Nadu", "Anna University", "Government / Autonomous"),
                createCollege("PSG College of Technology", "PSGCT", "Coimbatore, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Coimbatore Institute of Technology", "CIT-CBE", "Coimbatore, Tamil Nadu", "Anna University", "Government Aided / Autonomous"),
                createCollege("Sri Sivasubramaniya Nadar College of Engineering", "SSN", "Kalavakkam, Chennai, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Thiagarajar College of Engineering", "TCE", "Madurai, Tamil Nadu", "Anna University", "Government Aided / Autonomous"),
                createCollege("National Engineering College", "NEC-KVL", "Kovilpatti, Tamil Nadu", "Anna University", "Government Aided / Autonomous"),
                createCollege("Mepco Schlenk Engineering College", "MSEC", "Sivakasi, Tamil Nadu", "Anna University", "Government Aided / Autonomous"),
                createCollege("Sri Krishna College of Engineering and Technology", "SKCET", "Coimbatore, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Kumaraguru College of Technology", "KCT", "Coimbatore, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Kongu Engineering College", "KEC-ERD", "Erode, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Rajalakshmi Engineering College", "REC-CHEN", "Thandalam, Chennai, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Sri Venkateswara College of Engineering", "SVCE-SRIPERUMBUDUR", "Sriperumbudur, Chennai, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Bannari Amman Institute of Technology", "BIT-STV", "Sathyamangalam, Erode, Tamil Nadu", "Anna University", "Private / Autonomous"),
                createCollege("Government College of Technology Coimbatore", "GCT-CBE", "Coimbatore, Tamil Nadu", "Anna University", "Government"),

                // ─── Karnataka ───────────────────────────────────────────
                createCollege("University Visvesvaraya College of Engineering", "UVCE", "Bengaluru, Karnataka", "Bangalore University", "Government"),
                createCollege("BMS College of Engineering", "BMSCE", "Basavanagudi, Bengaluru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("RV College of Engineering", "RVCE", "Jayanagar, Bengaluru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("PES University", "PES", "Bengaluru, Karnataka", "PES University", "Deemed University"),
                createCollege("M.S. Ramaiah Institute of Technology", "MSRIT", "Bengaluru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("Dayananda Sagar College of Engineering", "DSCE", "Bengaluru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("Nitte Meenakshi Institute of Technology", "NMIT", "Bengaluru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("New Horizon College of Engineering", "NHCE", "Bengaluru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("Jain University", "JAIN-UNIV", "Bengaluru, Karnataka", "Jain University", "Deemed University"),
                createCollege("CMR Institute of Technology", "CMRIT-BLR", "Bengaluru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("Vidyavardhaka College of Engineering", "VVCE", "Mysuru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("National Institute of Engineering", "NIE-MYS", "Mysuru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("Dr. Ambedkar Institute of Technology", "AIT-BLR", "Bengaluru, Karnataka", "VTU", "Government Aided"),
                createCollege("Siddaganga Institute of Technology", "SIT-TKR", "Tumkuru, Karnataka", "VTU", "Private / Autonomous"),
                createCollege("KLE Technological University", "KLTECH", "Hubballi, Karnataka", "KLE Technological University", "Deemed University"),

                // ─── Maharashtra ─────────────────────────────────────────
                createCollege("College of Engineering Pune", "COEP", "Pune, Maharashtra", "SPPU", "Government / Autonomous"),
                createCollege("Veermata Jijabai Technological Institute", "VJTI", "Mumbai, Maharashtra", "University of Mumbai", "Government / Autonomous"),
                createCollege("Sardar Patel College of Engineering", "SPCE", "Mumbai, Maharashtra", "University of Mumbai", "Government Aided"),
                createCollege("KJ Somaiya College of Engineering", "KJSCE", "Vidyavihar, Mumbai, Maharashtra", "University of Mumbai", "Private / Autonomous"),
                createCollege("Pune Institute of Computer Technology", "PICT", "Pune, Maharashtra", "SPPU", "Private / Autonomous"),
                createCollege("MIT College of Engineering Pune", "MITCOE", "Pune, Maharashtra", "SPPU", "Private / Autonomous"),
                createCollege("Symbiosis Institute of Technology", "SIT-PUNE", "Lavale, Pune, Maharashtra", "Symbiosis International University", "Deemed University"),
                createCollege("Vishwakarma Institute of Technology", "VIT-PUNE", "Pune, Maharashtra", "SPPU", "Private / Autonomous"),
                createCollege("DY Patil College of Engineering", "DYPCOE", "Akurdi, Pune, Maharashtra", "SPPU", "Private / Autonomous"),
                createCollege("Walchand College of Engineering", "WCE", "Sangli, Maharashtra", "SPPU", "Government Aided / Autonomous"),
                createCollege("Government College of Engineering Karad", "GCEK", "Karad, Satara, Maharashtra", "SPPU", "Government"),
                createCollege("Government College of Engineering Aurangabad", "GCEA", "Aurangabad, Maharashtra", "MGM University / Dr. BAMU", "Government"),
                createCollege("Cummins College of Engineering for Women", "CCEW", "Pune, Maharashtra", "SPPU", "Private / Autonomous"),
                createCollege("Army Institute of Technology", "AIT-PUNE", "Dighi, Pune, Maharashtra", "SPPU", "Private / Autonomous"),
                createCollege("PCCOE Pimpri Chinchwad College of Engineering", "PCCOE", "Nigdi, Pune, Maharashtra", "SPPU", "Private / Autonomous"),

                // ─── Delhi / NCR ─────────────────────────────────────────
                createCollege("Delhi Technological University", "DTU", "Rohini, New Delhi", "DTU", "Government"),
                createCollege("Netaji Subhas University of Technology", "NSUT", "Dwarka, New Delhi", "NSUT", "Government"),
                createCollege("Indraprastha Institute of Information Technology Delhi", "IIIT-D", "Okhla, New Delhi", "IIIT Delhi", "Deemed University"),
                createCollege("Jamia Millia Islamia Faculty of Engineering and Technology", "JMI-FET", "New Delhi", "Jamia Millia Islamia", "Central University"),
                createCollege("Guru Gobind Singh Indraprastha University", "GGSIPU", "Dwarka, New Delhi", "GGSIPU", "Government"),
                createCollege("Amity University Noida", "AMITY-NOI", "Noida, Uttar Pradesh", "Amity University", "Private University"),
                createCollege("Jaypee Institute of Information Technology", "JIIT", "Noida, Uttar Pradesh", "JIIT", "Deemed University"),
                createCollege("GL Bajaj Institute of Technology and Management", "GLBITM", "Greater Noida, Uttar Pradesh", "AKTU", "Private / Affiliated"),
                createCollege("Dronacharya College of Engineering", "DCE-GGN", "Gurugram, Haryana", "MDU", "Private / Affiliated"),
                createCollege("The NorthCap University", "NCU", "Gurugram, Haryana", "NorthCap University", "Deemed University"),

                // ─── Uttar Pradesh ────────────────────────────────────────
                createCollege("Harcourt Butler Technical University", "HBTU", "Kanpur, Uttar Pradesh", "HBTU", "Government"),
                createCollege("Institute of Engineering and Technology Lucknow", "IET-LKO", "Lucknow, Uttar Pradesh", "Dr. APJ Abdul Kalam Technical University", "Government"),
                createCollege("Madan Mohan Malaviya University of Technology", "MMMUT", "Gorakhpur, Uttar Pradesh", "MMMUT", "Government"),
                createCollege("Bundelkhand Institute of Engineering and Technology", "BIET-JHS", "Jhansi, Uttar Pradesh", "AKTU", "Government"),
                createCollege("Kamla Nehru Institute of Technology", "KNIT", "Sultanpur, Uttar Pradesh", "AKTU", "Government Aided"),
                createCollege("Motilal Nehru National Institute of Technology", "MNNIT", "Allahabad, Uttar Pradesh", "MNNIT (NIT)", "Government / NIT"),
                createCollege("Ajay Kumar Garg Engineering College", "AKGEC", "Ghaziabad, Uttar Pradesh", "AKTU", "Private / Autonomous"),
                createCollege("Krishna Engineering College", "KEC-GZB", "Ghaziabad, Uttar Pradesh", "AKTU", "Private / Affiliated"),
                createCollege("JSS Academy of Technical Education Noida", "JSSATEN", "Noida, Uttar Pradesh", "AKTU", "Private / Autonomous"),
                createCollege("Galgotias College of Engineering and Technology", "GCET-GN", "Greater Noida, Uttar Pradesh", "AKTU", "Private / Affiliated"),

                // ─── Rajasthan ────────────────────────────────────────────
                createCollege("Malaviya National Institute of Technology", "MNIT", "Jaipur, Rajasthan", "MNIT (NIT)", "Government / NIT"),
                createCollege("Government Engineering College Ajmer", "GEC-AJM", "Ajmer, Rajasthan", "RTU", "Government"),
                createCollege("Government Engineering College Bikaner", "GEC-BKN", "Bikaner, Rajasthan", "RTU", "Government"),
                createCollege("Poornima College of Engineering", "PCE-JPR", "Jaipur, Rajasthan", "RTU", "Private / Autonomous"),
                createCollege("Arya College of Engineering and IT", "ACEIT", "Jaipur, Rajasthan", "RTU", "Private / Affiliated"),
                createCollege("LNM Institute of Information Technology", "LNMIIT", "Jaipur, Rajasthan", "LNMIIT", "Deemed University"),
                createCollege("Manipal University Jaipur", "MUJ", "Jaipur, Rajasthan", "Manipal University Jaipur", "Deemed University"),
                createCollege("Amity University Jaipur", "AMITY-JPR", "Jaipur, Rajasthan", "Amity University", "Private University"),
                createCollege("Jaipur Engineering College and Research Centre", "JECRC", "Jaipur, Rajasthan", "RTU", "Private / Affiliated"),
                createCollege("Swami Keshvanand Institute of Technology", "SKIT", "Jaipur, Rajasthan", "RTU", "Private / Affiliated"),

                // ─── Gujarat ──────────────────────────────────────────────
                createCollege("LD College of Engineering", "LDCE", "Ahmedabad, Gujarat", "GTU", "Government"),
                createCollege("BVM Engineering College", "BVM", "Vallabh Vidyanagar, Anand, Gujarat", "GTU", "Government Aided"),
                createCollege("Sarvajanik College of Engineering and Technology", "SCET", "Surat, Gujarat", "GTU", "Private / Aided"),
                createCollege("Dharmsinh Desai University", "DDU", "Nadiad, Gujarat", "DDU", "Deemed University"),
                createCollege("Nirma University", "NIRMA", "Ahmedabad, Gujarat", "Nirma University", "Deemed University"),
                createCollege("Pandit Deendayal Energy University", "PDEU", "Gandhinagar, Gujarat", "PDEU", "Deemed University"),
                createCollege("Charotar University of Science and Technology", "CHARUSAT", "Changa, Anand, Gujarat", "CHARUSAT", "Deemed University"),
                createCollege("Silver Oak University", "SOU", "Ahmedabad, Gujarat", "GTU", "Private / Affiliated"),
                createCollege("Vishwakarma Government Engineering College", "VGEC", "Chandkheda, Ahmedabad, Gujarat", "GTU", "Government"),
                createCollege("G.H. Patel College of Engineering and Technology", "GCET-VVN", "Vallabh Vidyanagar, Gujarat", "GTU", "Private / Aided"),

                // ─── Madhya Pradesh ────────────────────────────────────────
                createCollege("Maulana Azad National Institute of Technology", "MANIT", "Bhopal, Madhya Pradesh", "MANIT (NIT)", "Government / NIT"),
                createCollege("Shri Govindram Seksaria Institute of Technology and Science", "SGSITS", "Indore, Madhya Pradesh", "RGPV", "Government Aided"),
                createCollege("Institute of Engineering and Technology DAVV", "IET-DAVV", "Indore, Madhya Pradesh", "DAVV", "Government"),
                createCollege("Madhav Institute of Technology and Science", "MITS-GWALIOR", "Gwalior, Madhya Pradesh", "RGPV", "Government Aided"),
                createCollege("Jabalpur Engineering College", "JEC", "Jabalpur, Madhya Pradesh", "RGPV", "Government"),
                createCollege("Samrat Ashok Technological Institute", "SATI", "Vidisha, Madhya Pradesh", "RGPV", "Government"),
                createCollege("Truba Institute of Engineering and Information Technology", "TIEIT", "Bhopal, Madhya Pradesh", "RGPV", "Private / Affiliated"),
                createCollege("Oriental Institute of Science and Technology", "OIST-BPL", "Bhopal, Madhya Pradesh", "RGPV", "Private / Affiliated"),
                createCollege("IPS Academy College of Engineering", "IPS-ACET", "Indore, Madhya Pradesh", "RGPV", "Private / Affiliated"),
                createCollege("Acropolis Institute of Technology and Research", "AITR", "Indore, Madhya Pradesh", "RGPV", "Private / Affiliated"),

                // ─── West Bengal ──────────────────────────────────────────
                createCollege("Jadavpur University Faculty of Engineering and Technology", "JU-FET", "Jadavpur, Kolkata, West Bengal", "Jadavpur University", "Government"),
                createCollege("Heritage Institute of Technology", "HIT-KOL", "Kolkata, West Bengal", "MAKAUT", "Private / Affiliated"),
                createCollege("Institute of Engineering and Management Kolkata", "IEM-KOL", "Kolkata, West Bengal", "MAKAUT", "Private / Affiliated"),
                createCollege("Techno India University", "TIU", "Salt Lake, Kolkata, West Bengal", "Techno India University", "Private University"),
                createCollege("Narula Institute of Technology", "NIT-KOL", "Agarpara, Kolkata, West Bengal", "MAKAUT", "Private / Affiliated"),
                createCollege("Calcutta Institute of Engineering and Management", "CIEM", "Tollygunge, Kolkata, West Bengal", "MAKAUT", "Private / Affiliated"),
                createCollege("RCC Institute of Information Technology", "RCCIIT", "Beliaghata, Kolkata, West Bengal", "MAKAUT", "Government Aided"),
                createCollege("GNIT Girls Institute of Technology", "GNIT-KOL", "Kolkata, West Bengal", "MAKAUT", "Private / Affiliated"),
                createCollege("Haldia Institute of Technology", "HIT-HALDIA", "Haldia, West Bengal", "MAKAUT", "Private / Affiliated"),
                createCollege("Asansol Engineering College", "AEC", "Asansol, West Bengal", "MAKAUT", "Government Aided"),

                // ─── Bihar & Jharkhand ────────────────────────────────────
                createCollege("National Institute of Technology Patna", "NIT-PAT", "Patna, Bihar", "NIT Patna", "Government / NIT"),
                createCollege("Bihar Engineering University", "BEU", "Patna, Bihar", "BEU", "Government"),
                createCollege("Gaya College of Engineering", "GCE-GAYA", "Gaya, Bihar", "BEU", "Government"),
                createCollege("National Institute of Technology Jamshedpur", "NIT-JMS", "Jamshedpur, Jharkhand", "NIT Jamshedpur", "Government / NIT"),
                createCollege("BIT Sindri", "BIT-SINDRI", "Sindri, Dhanbad, Jharkhand", "BIT Sindri", "Government"),
                createCollege("Birla Institute of Technology Mesra", "BIT-MESRA", "Mesra, Ranchi, Jharkhand", "BIT Mesra", "Deemed University"),
                createCollege("BIT Deoghar", "BIT-DEOGHAR", "Deoghar, Jharkhand", "BIT Mesra", "Deemed University"),
                createCollege("Ramgovind Institute of Technology", "RGIT-JH", "Koderma, Jharkhand", "JKUAT", "Private / Affiliated"),

                // ─── Odisha ───────────────────────────────────────────────
                createCollege("National Institute of Technology Rourkela", "NIT-RKL", "Rourkela, Odisha", "NIT Rourkela", "Government / NIT"),
                createCollege("College of Engineering and Technology Bhubaneswar", "CET-BBS", "Bhubaneswar, Odisha", "BPUT", "Government"),
                createCollege("Gandhi Engineering College", "GEC-BBS", "Bhubaneswar, Odisha", "BPUT", "Private / Affiliated"),
                createCollege("Siksha O Anusandhan University", "SOA", "Bhubaneswar, Odisha", "SOA University", "Deemed University"),
                createCollege("Kalinga Institute of Industrial Technology", "KIIT", "Bhubaneswar, Odisha", "KIIT University", "Deemed University"),
                createCollege("IIIT Bhubaneswar", "IIIT-BBS", "Bhubaneswar, Odisha", "IIIT Bhubaneswar", "Government"),
                createCollege("Trident Academy of Technology", "TAT", "Bhubaneswar, Odisha", "BPUT", "Private / Affiliated"),
                createCollege("Silicon Institute of Technology", "SIT-BBS", "Bhubaneswar, Odisha", "BPUT", "Private / Affiliated"),

                // ─── Punjab & Haryana ─────────────────────────────────────
                createCollege("Thapar Institute of Engineering and Technology", "THAPAR", "Patiala, Punjab", "Thapar University", "Deemed University"),
                createCollege("National Institute of Technology Jalandhar", "NIT-JLD", "Jalandhar, Punjab", "NIT Jalandhar", "Government / NIT"),
                createCollege("Punjab Engineering College", "PEC", "Chandigarh", "Chandigarh University", "Government"),
                createCollege("Chandigarh University", "CU-MOHALI", "Mohali, Punjab", "Chandigarh University", "Deemed University"),
                createCollege("Chitkara University Punjab", "CHITKARA-PB", "Rajpura, Punjab", "Chitkara University", "Deemed University"),
                createCollege("Lovely Professional University", "LPU", "Phagwara, Punjab", "LPU", "Private University"),
                createCollege("DAV Institute of Engineering and Technology", "DAVIET", "Jalandhar, Punjab", "IKG-PTU", "Private / Affiliated"),
                createCollege("National Institute of Technology Kurukshetra", "NIT-KKR", "Kurukshetra, Haryana", "NIT Kurukshetra", "Government / NIT"),
                createCollege("DCR University of Science and Technology", "DCRUST", "Murthal, Sonepat, Haryana", "DCRUST", "Government"),
                createCollege("Manav Rachna College of Engineering", "MRCE", "Faridabad, Haryana", "MDU / Manav Rachna University", "Private / Affiliated"),

                // ─── Himachal Pradesh ─────────────────────────────────────
                createCollege("National Institute of Technology Hamirpur", "NIT-HMR", "Hamirpur, Himachal Pradesh", "NIT Hamirpur", "Government / NIT"),
                createCollege("Jaypee University of Information Technology", "JUIT", "Waknaghat, Solan, Himachal Pradesh", "JUIT", "Deemed University"),
                createCollege("Shoolini University", "SHOOLINI", "Solan, Himachal Pradesh", "Shoolini University", "Deemed University"),
                createCollege("APG Shimla University", "APGSU", "Shimla, Himachal Pradesh", "Shimla University", "Private University"),

                // ─── Uttarakhand ──────────────────────────────────────────
                createCollege("IIT Roorkee", "IITR", "Roorkee, Uttarakhand", "IIT Roorkee", "Government / IIT"),
                createCollege("Graphic Era University", "GEU", "Dehradun, Uttarakhand", "Graphic Era University", "Deemed University"),
                createCollege("DIT University", "DITU", "Dehradun, Uttarakhand", "DIT University", "Private University"),
                createCollege("Uttarakhand Technical University", "UTU", "Dehradun, Uttarakhand", "UTU", "Government"),
                createCollege("College of Technology G.B. Pant University", "COT-GBPU", "Pantnagar, Uttarakhand", "GBPUAT", "Government"),

                // ─── Assam & North East ────────────────────────────────────
                createCollege("National Institute of Technology Silchar", "NIT-SLC", "Silchar, Assam", "NIT Silchar", "Government / NIT"),
                createCollege("Assam Engineering College", "AEC-GHY", "Guwahati, Assam", "Gauhati University", "Government"),
                createCollege("Royal School of Engineering and Technology", "RSET-GHY", "Guwahati, Assam", "Gauhati University", "Private / Affiliated"),
                createCollege("National Institute of Technology Meghalaya", "NIT-MEG", "Shillong, Meghalaya", "NIT Meghalaya", "Government / NIT"),
                createCollege("National Institute of Technology Mizoram", "NIT-MIZ", "Aizawl, Mizoram", "NIT Mizoram", "Government / NIT"),
                createCollege("National Institute of Technology Nagaland", "NIT-NAG", "Dimapur, Nagaland", "NIT Nagaland", "Government / NIT"),
                createCollege("National Institute of Technology Manipur", "NIT-MNP", "Imphal, Manipur", "NIT Manipur", "Government / NIT"),
                createCollege("National Institute of Technology Arunachal Pradesh", "NIT-ARU", "Yupia, Arunachal Pradesh", "NIT Arunachal Pradesh", "Government / NIT"),

                // ─── Kerala ───────────────────────────────────────────────
                createCollege("College of Engineering Trivandrum", "CET-TVM", "Thiruvananthapuram, Kerala", "APJ Abdul Kalam Technological University", "Government"),
                createCollege("Government Engineering College Thrissur", "GEC-TCR", "Thrissur, Kerala", "APJ Abdul Kalam Technological University", "Government"),
                createCollege("National Institute of Technology Calicut", "NIT-CLT", "Calicut, Kerala", "NIT Calicut", "Government / NIT"),
                createCollege("TKM College of Engineering", "TKMCE", "Kollam, Kerala", "APJ Abdul Kalam Technological University", "Government Aided"),
                createCollege("Rajagiri School of Engineering and Technology", "RSET", "Kakkanad, Ernakulam, Kerala", "APJ Abdul Kalam Technological University", "Private / Affiliated"),
                createCollege("Model Engineering College", "MEC-EKM", "Thrikkakara, Ernakulam, Kerala", "APJ Abdul Kalam Technological University", "Government"),
                createCollege("Mar Athanasius College of Engineering", "MACE", "Kothamangalam, Ernakulam, Kerala", "APJ Abdul Kalam Technological University", "Government Aided"),
                createCollege("LBS College of Engineering", "LBS-KSD", "Kasaragod, Kerala", "APJ Abdul Kalam Technological University", "Government"),
                createCollege("Sree Buddha College of Engineering", "SBCE", "Pathanamthitta, Kerala", "APJ Abdul Kalam Technological University", "Private / Affiliated"),
                createCollege("Cochin University of Science and Technology", "CUSAT", "Ernakulam, Kerala", "CUSAT", "Government"),

                // ─── Goa ──────────────────────────────────────────────────
                createCollege("Goa College of Engineering", "GCE-GOA", "Farmagudi, Ponda, Goa", "Goa University", "Government"),
                createCollege("Padre Conceicao College of Engineering", "PCCE", "Verna, Goa", "Goa University", "Private / Aided"),
                createCollege("Agnel Institute of Technology and Design", "AITD-GOA", "Assagao, Bardez, Goa", "Goa University", "Private / Affiliated"),

                // ─── Chhattisgarh ─────────────────────────────────────────
                createCollege("National Institute of Technology Raipur", "NIT-RPR", "Raipur, Chhattisgarh", "NIT Raipur", "Government / NIT"),
                createCollege("Bhilai Institute of Technology", "BIT-BHL", "Bhilai, Durg, Chhattisgarh", "CSVTU", "Private / Autonomous"),
                createCollege("Rungta College of Engineering and Technology", "RCET-BHL", "Bhilai, Chhattisgarh", "CSVTU", "Private / Affiliated"),
                createCollege("Columbia Institute of Engineering and Technology", "CIET-RPR", "Raipur, Chhattisgarh", "CSVTU", "Private / Affiliated"),

                // ─── Jammu & Kashmir ──────────────────────────────────────
                createCollege("National Institute of Technology Srinagar", "NIT-SRN", "Srinagar, Jammu & Kashmir", "NIT Srinagar", "Government / NIT"),
                createCollege("Islamic University of Science and Technology", "IUST", "Awantipora, Kashmir, J&K", "IUST", "Government"),
                createCollege("Model Institute of Engineering and Technology", "MIET-JAM", "Jammu, J&K", "University of Jammu", "Private / Affiliated"),

                // ─── IITs ─────────────────────────────────────────────────
                createCollege("Indian Institute of Technology Bombay", "IITB", "Powai, Mumbai, Maharashtra", "IIT Bombay", "Government / IIT"),
                createCollege("Indian Institute of Technology Delhi", "IITD", "Hauz Khas, New Delhi", "IIT Delhi", "Government / IIT"),
                createCollege("Indian Institute of Technology Madras", "IITM", "Adyar, Chennai, Tamil Nadu", "IIT Madras", "Government / IIT"),
                createCollege("Indian Institute of Technology Kanpur", "IITK", "Kanpur, Uttar Pradesh", "IIT Kanpur", "Government / IIT"),
                createCollege("Indian Institute of Technology Kharagpur", "IITKGP", "Kharagpur, West Bengal", "IIT Kharagpur", "Government / IIT"),
                createCollege("Indian Institute of Technology Hyderabad", "IITH", "Sangareddy, Telangana", "IIT Hyderabad", "Government / IIT"),
                createCollege("Indian Institute of Technology Gandhinagar", "IITGN", "Palaj, Gandhinagar, Gujarat", "IIT Gandhinagar", "Government / IIT"),
                createCollege("Indian Institute of Technology Bhubaneswar", "IITBBS", "Arugul, Bhubaneswar, Odisha", "IIT Bhubaneswar", "Government / IIT"),
                createCollege("Indian Institute of Technology Jodhpur", "IITJ", "Jodhpur, Rajasthan", "IIT Jodhpur", "Government / IIT"),
                createCollege("Indian Institute of Technology Patna", "IITP", "Bihta, Patna, Bihar", "IIT Patna", "Government / IIT"),
                createCollege("Indian Institute of Technology Indore", "IITI", "Simrol, Indore, Madhya Pradesh", "IIT Indore", "Government / IIT"),
                createCollege("Indian Institute of Technology Mandi", "IITMANDI", "Kamand, Mandi, Himachal Pradesh", "IIT Mandi", "Government / IIT"),
                createCollege("Indian Institute of Technology Varanasi (BHU)", "IIT-BHU", "Varanasi, Uttar Pradesh", "IIT BHU", "Government / IIT"),
                createCollege("Indian Institute of Technology Guwahati", "IITG", "Guwahati, Assam", "IIT Guwahati", "Government / IIT"),
                createCollege("Indian Institute of Technology Tirupati", "IITTP", "Tirupati, Andhra Pradesh", "IIT Tirupati", "Government / IIT"),

                // ─── NITs (remaining) ─────────────────────────────────────
                createCollege("National Institute of Technology Surathkal", "NIT-SRT", "Surathkal, Mangaluru, Karnataka", "NIT Karnataka", "Government / NIT"),
                createCollege("National Institute of Technology Trichy", "NIT-TCY", "Tiruchirappalli, Tamil Nadu", "NIT Trichy", "Government / NIT"),
                createCollege("National Institute of Technology Warangal", "NIT-WGL", "Warangal, Telangana", "NIT Warangal", "Government / NIT"),
                createCollege("National Institute of Technology Durgapur", "NIT-DGP", "Durgapur, West Bengal", "NIT Durgapur", "Government / NIT"),
                createCollege("National Institute of Technology Goa", "NIT-GOA", "Farmagudi, Goa", "NIT Goa", "Government / NIT"),
                createCollege("National Institute of Technology Delhi", "NIT-DEL", "Narela, New Delhi", "NIT Delhi", "Government / NIT"),
                createCollege("National Institute of Technology Puducherry", "NIT-PDY", "Karaikal, Puducherry", "NIT Puducherry", "Government / NIT"),
                createCollege("National Institute of Technology Agartala", "NIT-AGT", "Agartala, Tripura", "NIT Agartala", "Government / NIT"),
                createCollege("National Institute of Technology Uttarakhand", "NIT-UTK", "Srinagar, Uttarakhand", "NIT Uttarakhand", "Government / NIT"),
                createCollege("National Institute of Technology Sikkim", "NIT-SKM", "Ravangla, Sikkim", "NIT Sikkim", "Government / NIT"),

                // ─── IIITs ────────────────────────────────────────────────
                createCollege("IIIT Hyderabad", "IIIT-H", "Gachibowli, Hyderabad, Telangana", "IIIT Hyderabad", "Deemed University"),
                createCollege("IIIT Allahabad", "IIIT-A", "Allahabad, Uttar Pradesh", "IIIT Allahabad", "Government"),
                createCollege("IIIT Bangalore", "IIITB", "Electronic City, Bengaluru, Karnataka", "IIIT Bangalore", "Deemed University"),
                createCollege("IIIT Gwalior", "IIITM-GWL", "Gwalior, Madhya Pradesh", "IIITM Gwalior", "Deemed University"),
                createCollege("IIIT Kancheepuram", "IIIT-KAN", "Kancheepuram, Tamil Nadu", "IIIT Kancheepuram", "Government"),
                createCollege("IIIT Jabalpur", "IIIT-JBP", "Jabalpur, Madhya Pradesh", "IIIT Jabalpur", "Government"),
                createCollege("IIIT Vadodara", "IIIT-VDR", "Vadodara, Gujarat", "IIIT Vadodara", "Government"),
                createCollege("IIIT Sri City", "IIIT-SRICITY", "Sri City, Andhra Pradesh", "IIIT Sri City", "Government"),
                createCollege("IIIT Lucknow", "IIIT-LKO", "Lucknow, Uttar Pradesh", "IIIT Lucknow", "Government"),
                createCollege("IIIT Dharwad", "IIIT-DWD", "Dharwad, Karnataka", "IIIT Dharwad", "Government"),

                // ─── Miscellaneous Top Private Deemed Universities ─────────
                createCollege("VIT University Vellore", "VIT-VLR", "Vellore, Tamil Nadu", "VIT University", "Deemed University"),
                createCollege("VIT University Chennai", "VIT-CHN", "Chennai, Tamil Nadu", "VIT University", "Deemed University"),
                createCollege("SRM Institute of Science and Technology", "SRMIST", "Kattankulathur, Chennai, Tamil Nadu", "SRM University", "Deemed University"),
                createCollege("Manipal Institute of Technology", "MIT-MANIPAL", "Manipal, Udupi, Karnataka", "Manipal Academy of Higher Education", "Deemed University"),
                createCollege("Birla Institute of Technology and Science Pilani", "BITS-PILANI", "Pilani, Rajasthan", "BITS Pilani", "Deemed University"),
                createCollege("BITS Pilani Hyderabad Campus", "BITS-HYD", "Hyderabad, Telangana", "BITS Pilani", "Deemed University"),
                createCollege("BITS Pilani Goa Campus", "BITS-GOA", "Vasco da Gama, Goa", "BITS Pilani", "Deemed University"),
                createCollege("Amrita School of Engineering Coimbatore", "AMRITA-CBE", "Coimbatore, Tamil Nadu", "Amrita Vishwa Vidyapeetham", "Deemed University"),
                createCollege("Amrita School of Engineering Bengaluru", "AMRITA-BLR", "Bengaluru, Karnataka", "Amrita Vishwa Vidyapeetham", "Deemed University"),
                createCollege("Vellore Institute of Technology Amaravati", "VIT-AMR", "Amaravati, Andhra Pradesh", "VIT-AP University", "Deemed University")
            );

            collegeRepository.saveAll(colleges);
            log.info("Successfully seeded {} colleges.", colleges.size());
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
