# ASMJ Recruitment

A Spring Boot + Thymeleaf recruitment consultancy portal with public job browsing and candidate applications, recruiter workflows, and role-based access.

## Technology
- Java 17
- Spring Boot 3.3.5
- Thymeleaf, Spring Security, Bootstrap-free custom responsive CSS
- H2 in-memory persistence for development
- MongoDB Atlas persistence for `test` and `prod` profiles
- A small `RecordStore` persistence port keeps workflow logic independent of storage.

## Run locally (Windows PowerShell)
1. Install Java 17 and Maven 3.9+.
2. Extract this project and open a terminal in the project folder.
3. Run `mvn spring-boot:run`
4. Open http://localhost:8080

The default profile is `dev`; H2 is in-memory and records are reset after restart. H2 console: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:asmjrecruitment`, username `sa`, blank password).

## Demo accounts
These are development-only accounts; change/remove them before any shared or production deployment.

| Role | Email / username | Password |
|---|---|---|
| Admin | admin@asmj.co.in | Admin@123 |
| Recruiter | recruiter@asmj.co.in | Recruit@123 |
| Employer | employer@asmj.co.in | Employer@123 |
| Candidate | candidate@asmj.co.in | Candidate@123 |

## Test profile with MongoDB Atlas
1. Create a MongoDB Atlas database and database user.
2. Set the environment variable `MONGODB_URI` to your URL-encoded connection URI, including database name, e.g. `mongodb+srv://USER:PASSWORD@CLUSTER/asmj_recruitment?retryWrites=true&w=majority`.
3. Set `SPRING_PROFILES_ACTIVE=test`.
4. Run `mvn spring-boot:run`.

PowerShell:
```powershell
$env:SPRING_PROFILES_ACTIVE="test"
$env:MONGODB_URI="mongodb+srv://USER:PASSWORD@CLUSTER/asmj_recruitment?retryWrites=true&w=majority"
mvn spring-boot:run
```
Do not commit real database credentials. The `test` profile excludes JDBC auto-configuration and activates the Mongo persistence adapter.

## Implemented screens
- Public landing page and searchable job board
- Job detail and public application submission
- Candidate registration
- Sign-in / sign-out
- Dashboard and workflow quick links
- Candidate and employer directories
- Employer creation and job publication
- Application pipeline and status updates
- Interview scheduling
- Placement and consultancy-fee tracking
- Summary reports
- Candidate portal application view

## Current MVP boundaries / recommended next hardening
- Candidate registration and public job applications are functional, but the initial demo authentication accounts are intentionally seeded in Spring Security memory; self-registration does not yet create a login credential or link the candidate record to an authenticated account.
- Resume upload, email verification, password reset, employer self-service registration, candidate profile editing, and full CRUD/audit screens should be added in the next iteration.
- Replace demo credentials and use HTTPS, secure secret management, rate limiting, and an appropriate privacy/retention policy before production.
- The persistence adapter stores typed records as JSON payloads in H2/MongoDB; this enables environment switching for the MVP, but a production system may benefit from typed schema collections and migration/versioning.
