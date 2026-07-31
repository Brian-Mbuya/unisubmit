package com.unisubmit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.unisubmit.domain.*;
import com.unisubmit.repository.*;
import com.unisubmit.service.UserService;
import java.security.SecureRandom;
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

	/** The well-known password shared by the demo accounts. Demo profile ONLY. */
	private static final String DEMO_PASSWORD = "password123";

	/**
	 * Creates a core account only if its username is not already taken.
	 * Students route through {@code createStudent} so seeded demo accounts follow the
	 * same admission-number + phone identity rule as real ones — otherwise a seeded
	 * student would be the only account in the system with no phone.
	 */
	private static void ensureUser(UserService userService, String username, String name,
								   Role role, String studentId, String staffId, String password) {
		try {
			if (role == Role.STUDENT) {
				userService.createStudent(studentId, "0700000000", password, name,
						null, 1, 1);
			} else {
				userService.createUser(username, password, name, role, studentId, staffId);
			}
		} catch (Exception alreadyExists) {
			// Username/ID already present — leave the existing account untouched.
		}
	}

	/**
	 * Bootstraps the accounts needed to sign in, WITHOUT ever creating a known-password
	 * account outside the demo profile.
	 * <p>
	 * This used to unconditionally create admin/lecturer/student with
	 * {@value #DEMO_PASSWORD} in every environment, which meant a live
	 * {@code admin}/{@value #DEMO_PASSWORD} login on any real deployment. Now the trio is
	 * gated behind {@code unisubmit.seed.demo-accounts} (on only in the local profile),
	 * and a production database instead gets exactly one admin whose password comes from
	 * {@code ADMIN_INITIAL_PASSWORD} — or, if that is unset, is generated and logged once
	 * so the operator can claim it (the same approach Spring Boot itself takes for its
	 * default user).
	 */
	private static void bootstrapAccounts(UserService userService, UserRepository userRepository,
										  boolean seedDemoAccounts, String adminInitialPassword) {
		if (seedDemoAccounts) {
			ensureUser(userService, "admin", "System Administrator", Role.ADMIN, null, null, DEMO_PASSWORD);
			ensureUser(userService, "lecturer", "Dr. Smith", Role.LECTURER, null, "L001", DEMO_PASSWORD);
			ensureUser(userService, "student", "John Doe", Role.STUDENT, "S001", null, DEMO_PASSWORD);
			log.warn("Demo accounts seeded with the well-known password '{}'. "
					+ "NEVER set unisubmit.seed.demo-accounts=true in production.", DEMO_PASSWORD);
			return;
		}

		// Already bootstrapped — never touch an existing admin's password.
		if (userRepository.findByUsername("admin").isPresent()) {
			return;
		}

		boolean generated = adminInitialPassword == null || adminInitialPassword.isBlank();
		String password = generated ? generateStrongPassword() : adminInitialPassword;
		ensureUser(userService, "admin", "System Administrator", Role.ADMIN, null, null, password);

		if (generated) {
			log.warn("""

					===============================================================
					 FIRST BOOT — created the 'admin' account with a generated
					 password:

					     {}

					 This is printed ONCE and cannot be recovered. Sign in and
					 change it now. Set ADMIN_INITIAL_PASSWORD to choose your own.
					===============================================================
					""", password);
		} else {
			log.info("Created the 'admin' account using ADMIN_INITIAL_PASSWORD.");
		}
	}

	/**
	 * 20 chars from an ambiguity-free alphabet (no O/0, l/1/I) — this password gets read
	 * off a terminal log and retyped by a human, so lookalike glyphs are a real hazard.
	 */
	private static String generateStrongPassword() {
		final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder(20);
		for (int i = 0; i < 20; i++) {
			sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return sb.toString();
	}

	@Bean
	public CommandLineRunner seedData(
			UserService userService,
			UserRepository userRepository,
			@Value("${unisubmit.seed.demo-accounts:false}") boolean seedDemoAccounts,
			@Value("${unisubmit.admin.initial-password:}") String adminInitialPassword,
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

			// Sign-in accounts — idempotent per-account, so they exist regardless of
			// seeder ordering. Demo trio only when explicitly enabled; otherwise a
			// single admin with a non-guessable password. See bootstrapAccounts.
			bootstrapAccounts(userService, userRepository, seedDemoAccounts, adminInitialPassword);

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

