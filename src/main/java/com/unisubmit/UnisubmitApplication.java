package com.unisubmit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import com.unisubmit.service.UserService;
import java.util.List;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class UnisubmitApplication {

	private static final Logger log = LoggerFactory.getLogger(UnisubmitApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(UnisubmitApplication.class, args);
	}

	/** Creates a core account only if its username is not already taken. */
	private static void ensureUser(UserService userService, String username, String name,
								   Role role, String studentId, String staffId) {
		try {
			userService.createUser(username, "password123", name, role, studentId, staffId);
		} catch (Exception alreadyExists) {
			// Username/ID already present — leave the existing account untouched.
		}
	}

	@Bean
	public CommandLineRunner seedData(
			UserService userService,
			TechnologyRepository technologyRepository,
			ResearchAreaRepository researchAreaRepository,
			FrameworkRepository frameworkRepository,
			DatabaseRepository databaseRepository,
			ProgrammingLanguageRepository programmingLanguageRepository,
			SkillRepository skillRepository) {
		return args -> {
			// Boot-time timezone banner — deadlines enforce on this wall-clock (2.1).
			// On Railway this must read Africa/Nairobi (see Dockerfile TZ + -Duser.timezone).
			log.info("Application timezone: {} (offset now {})",
					TimeZone.getDefault().getID(),
					java.time.ZonedDateTime.now().getOffset());

			// Default accounts — created idempotently (per-account), so they exist
			// regardless of seeder ordering. Admin logs in with username; students
			// with studentId; lecturers with staffId. All password123.
			ensureUser(userService, "admin", "System Administrator", Role.ADMIN, null, null);
			ensureUser(userService, "lecturer", "Dr. Smith", Role.LECTURER, null, "L001");
			ensureUser(userService, "student", "John Doe", Role.STUDENT, "S001", null);

			if (technologyRepository.count() == 0) {
				List.of("Spring Boot", "React", "Docker", "TensorFlow", "EHR Systems",
						"GIS Mapping", "EEG Biosensors", "CAD Modeling", "Microscopy Imaging",
						"Financial Ledger", "Online Survey Tools", "Kubernetes", "Unity 3D",
						"SPSS Software", "MATLAB Toolkit").forEach(name -> {
							Technology t = new Technology();
							t.setName(name);
							technologyRepository.save(t);
						});
			}

			if (researchAreaRepository.count() == 0) {
				List.of("Machine Learning", "Software Engineering", "Biblical Hermeneutics",
						"Systematic Theology", "Interpersonal Relationships", "Clinical Psychology",
						"Constitutional Law", "Corporate Governance", "Pedagogy & Education",
						"Renewable Energy", "Epidemiology", "Microbiology", "Macroeconomics",
						"Fluid Dynamics", "Social Psychology").forEach(name -> {
							ResearchArea r = new ResearchArea();
							r.setName(name);
							researchAreaRepository.save(r);
						});
			}

			if (frameworkRepository.count() == 0) {
				List.of("Spring Boot", "Next.js", "Django", "TensorFlow",
						"PyTorch", "Flutter", "Laravel", "Angular", "Express.js").forEach(name -> {
							Framework f = new Framework();
							f.setName(name);
							frameworkRepository.save(f);
						});
			}

			if (databaseRepository.count() == 0) {
				List.of("PostgreSQL", "MySQL", "MongoDB", "SQLite",
						"Redis", "Oracle", "Cassandra", "Neo4j").forEach(name -> {
							Database d = new Database();
							d.setName(name);
							databaseRepository.save(d);
						});
			}

			if (programmingLanguageRepository.count() == 0) {
				List.of("Java", "Python", "JavaScript", "TypeScript",
						"R", "C++", "SQL", "MATLAB", "Kotlin", "Go").forEach(name -> {
							ProgrammingLanguage p = new ProgrammingLanguage();
							p.setName(name);
							programmingLanguageRepository.save(p);
						});
			}

			if (skillRepository.count() == 0) {
				List.of("Qualitative Analysis", "Statistical Modeling", "Academic Writing",
						"Project Management", "Data Science", "Laboratory Safety",
						"Financial Analysis", "Theological Exegesis", "Counseling Techniques",
						"Legal Research").forEach(name -> {
							Skill s = new Skill();
							s.setName(name);
							skillRepository.save(s);
						});
			}
		};
	}
}

