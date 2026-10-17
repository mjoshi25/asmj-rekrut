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
- Candidate registration with login account creation
- Public employer registration with admin approval workflow
- Public recruiter registration with admin approval workflow
- Admin review screen at `/admin/registrations`
- Sign-in / sign-out with authenticated navigation (Sign in and registration links are hidden after login)
- Role-based login redirects: admin → `/admin/dashboard`, recruiter → `/recruiter/dashboard`, employer → `/employer/dashboard`, candidate → `/portal`
- Pending employer/recruiter users are sent to `/pending-approval` until an administrator approves them
- Separate administrator, recruiter, employer and candidate workspaces
- Candidate and employer directories
- Employer creation and job publication
- Application pipeline and status updates
- Interview scheduling
- Placement and consultancy-fee tracking
- Summary reports
- Candidate portal application view

## Current MVP boundaries / recommended next hardening
- The current build creates candidate, employer and recruiter login accounts through Spring Security in-memory user management. These accounts reset when the application restarts, so implement a database-backed user repository before production.
- Employer and recruiter accounts remain restricted to pending roles until an administrator approves them.
- Resume upload, email verification, password reset, candidate profile editing, full CRUD/audit screens, and persistent user credential storage are not yet implemented.
- Replace demo credentials and use HTTPS, secure secret management, rate limiting, and an appropriate privacy/retention policy before production.
- The persistence adapter stores business records as JSON payloads in H2/MongoDB; this enables environment switching for the MVP, but production may benefit from typed schema collections and migration/versioning.


## Registration flows
- Candidate: `/register/candidate` creates a candidate profile and login immediately.
- Employer: `/register/employer` stores a pending application; admin approves at `/admin/registrations`.
- Recruiter: `/register/recruiter` stores a pending application; admin approves at `/admin/registrations`.
- Default development admin: `admin@asmj.co.in` / `Admin@123`. Change this before any shared deployment.
- All POST forms use Thymeleaf `th:action` to allow Spring Security CSRF tokens to be included.


## Docker
The project includes a root-level `Dockerfile`. Build with `docker build -t asmj-recruitment .` and run with `docker run --rm -p 8080:8080 asmj-recruitment`. The demo user manager is in-memory, so newly registered login credentials do not survive a restart; use a persistent account repository before production.


## Role-aware selection dropdowns (latest update)

- Recruiters and administrators select an approved employer from a dropdown when posting a job. The published job stores the employer ID, company name and employer email so it remains linked to the selected company.
- Employer accounts only see and post jobs for their own approved employer record.
- Candidate registration uses dropdowns for qualification and experience bands. Employer registration uses an industry dropdown; recruiter registration uses recruitment-experience and hiring-sector dropdowns.
- Interview scheduling selects an existing candidate and an open job. Candidate email and job/company details are copied from the selected records rather than retyped.
- Placement recording selects an existing candidate, employer and job, keeping the saved details consistent.
- If there are no approved employers, job posting remains disabled until an administrator adds or approves an employer.


## Employer-to-recruiter authorization workflow

1. An employer registers and must be approved by the ASMJ administrator.
2. A recruiter registers and must also be approved by the administrator.
3. The employer signs in and opens **Employer dashboard → Recruiter authorizations** (`/employer/authorizations`).
4. The employer selects an approved recruiter and grants authorization. The authorization is saved as a `recruiterAuthorization` record with active status and job-posting permission.
5. The recruiter signs in and opens **Post a job** (`/jobs/new`). The employer dropdown contains only companies that have granted active authorization to that recruiter.
6. The server checks authorization again on job submission; changing the HTML form or submitting another employer ID cannot bypass the permission.
7. The employer can revoke authorization at any time. A revoked recruiter cannot create new jobs for that employer.

Authorization records are stored through the configured `RecordStore` adapter, so they persist with the selected H2 or MongoDB backend.


## Candidate profiles, resume generation, Excel and commissions

- Candidates can open `/candidate/profile`, edit their profile, upload a photo, and import an Excel profile. Download the supported workbook at `/candidate/profile/template.xlsx`; use the first row for headers and the second row for the candidate's details. The profile's generated resume at `/candidates/{candidateId}/resume` always reads the latest saved details and can be printed or saved as PDF from the browser.
- Candidate profile photos use Cloudinary. Configure `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET` in the deployment environment; never commit these secrets. The upload is limited to 5 MB.
- Employers, recruiters and administrators can search candidate profiles at `/candidate-search`, using skill, qualification, experience and location filters. Access should be granted only to approved hiring users; resume routes enforce signed-in role access.
- Admins can configure a fixed or salary-percentage commission at `/admin/commission`. Changing an application to `SELECTED` or `HIRED` creates a commission ledger entry; a numeric salary is required for percentage calculations. The ledger tracks PENDING, INVOICED, PAID and WAIVED and does not collect or transfer money.
- The admin relationship audit at `/admin/relationships` shows application and placement links between candidate, employer, recruiter and job.
- MongoDB Atlas is used with the `test` or `prod` profile via `MONGODB_URI`; the `dev` profile uses local H2. Login accounts are stored through the same record adapter, so production accounts persist when `SPRING_PROFILES_ACTIVE=prod` and MongoDB is configured. Development H2 data resets when the app restarts. Demo credentials are for development only and must be changed before production.

## Candidate profile photo and account settings

- Signed-in candidates can upload or replace a profile photo from **My profile**. Photos are uploaded to Cloudinary and the resulting `photoUrl` is stored with the candidate profile record; configure `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET` in the deployment environment.
- The candidate profile and generated resume use map-safe Thymeleaf access for optional profile fields, including `photoUrl`, so absent optional values do not cause a template property-resolution error.
- All authenticated roles have a **Settings** navigation link. The settings page displays the signed-in account and roles and supports password changes after verifying the current password. New passwords must be at least eight characters and match confirmation.


### Detailed candidate Excel import

Candidates can download **ASMJ_Detailed_Candidate_Profile_Template.xlsx** from Candidate Profile. The workbook has these worksheets: Personal Information, Education, Projects, Technology Experience, Languages, Employment, Certifications & Other, and Instructions. Each worksheet contains a yellow sample row that demonstrates the expected format. Replace the sample values and clear `SAMPLE` from the final `Import Row - clear SAMPLE to import` column, or delete the sample row; rows marked `SAMPLE` are intentionally ignored. For additional records, add one record per row and leave the marker column blank. Dates should use `YYYY-MM` (for example `2024-07`); actual Excel date cells are also accepted and normalized to `YYYY-MM`. Enter `Present` for a current job or use `Yes` in Currently Working for an active project. Importing saves the structured data into the signed-in candidate's profile; review it and use Save Profile to regenerate the resume. Keep the first row as the column headers.

Recruiter candidate search supports experience filters such as `3` (3 years up to but not including 4), `3-5` (from 3 years through exactly 5 years), `3 to 5`, and `5+` (at least 5 years). Experience values may be stored as a number, decimal years, or text such as `3 years 6 months`.
