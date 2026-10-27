# MediMatch — Blood Donation Coordination

A Java and web application prototype connecting donors, recipients, and hospitals through blood-request workflows and location-aware matching.

[Developer profile](https://github.com/Vishwaranjan0001)

## Engineering highlights

- Spring Boot REST API with MongoDB persistence.
- JWT authentication, BCrypt password hashing, and Spring Security filtering.
- Donor availability and blood-request coordination.
- Blood-group compatibility logic and Haversine distance calculations.
- Email-service integration and separate donor, recipient, hospital, and NGO web pages.

**Stack:** Java 17 · Spring Boot 3.2 · Spring Security · MongoDB · Maven · HTML · CSS · JavaScript.

## Source map

| Path | Responsibility |
| --- | --- |
| `MediMatch/backend/src/medimatch/Main.java` | Application startup, security configuration, geospatial helpers |
| `MediMatch/backend/src/medimatch/Controllers.java` | Authentication and application endpoints |
| `MediMatch/backend/src/medimatch/Models.java` | Models and repositories |
| `MediMatch/backend/src/medimatch/JwtUtil.java` | JWT generation and verification |
| `MediMatch/frontend/` | Role-specific HTML interfaces |

## Local evaluation

Requires Java 17, Maven, and MongoDB. Review the backend configuration in `MediMatch/backend/src/application.properties` and supply your own development database and email settings through your local environment. Set JWT_SECRET to a private signing secret, plus SMTP_USER and SMTP_PASS for email. The defaults do not include credentials.

```bash
git clone https://github.com/Vishwaranjan0001/MediMatch.git
cd MediMatch/MediMatch/backend
mvn spring-boot:run
```

The configured backend port is documented by `server.port`; the startup code targets http://localhost:8080. The frontend is a separate collection of HTML pages in `MediMatch/frontend/`.

## Scope

An educational prototype for demonstrating application workflows and matching logic. Use fictional records for evaluation. Matching logic is illustrative and is not clinical decision support. No deployment or build verification is implied by these setup instructions.
