# Contributing to TourLK

TourLK is a layer-first Spring Boot backend. Each of the 8 modules
(Booking, Payment, Guide, Driver, Hotel, Review, Notification, Admin —
adjust to your actual module list) is owned by one person end-to-end,
built on its own branch, and merged back via PR. This document explains
the conventions that let us all work in the same folders without
constantly colliding.

## 1. Folder & naming convention

The project is **layer-first**, not feature-first:

```
com.tourlk
 ├── config/        # app-wide configuration (Security, CORS, OpenAPI, JPA auditing)
 ├── controller/     # one file per module, e.g. BookingController.java
 ├── dto/            # request/response DTOs, prefixed by module, e.g. BookingRequestDto
 ├── entity/         # JPA entities; every entity extends AuditableEntity
 ├── enums/          # shared + module-specific enums, e.g. BookingStatus
 ├── repo/           # Spring Data repositories, e.g. BookingRepository
 ├── service/        # BookingService (interface) + BookingServiceImpl
 ├── security/       # shared auth infrastructure — most modules won't touch this
 ├── exception/      # shared GlobalExceptionHandler + custom exceptions
 └── util/           # shared helpers
```

Because everyone works in the same folders (`controller/`, `service/`,
etc.) rather than in per-module packages, **filenames must be prefixed
with your module name** to avoid collisions:

- `BookingController.java`, `BookingService.java`, `BookingServiceImpl.java`
- `PaymentController.java`, `PaymentService.java`, `PaymentServiceImpl.java`
- DTOs: `BookingRequestDto`, `BookingResponseDto`
- Entities: `Booking.java`, `Payment.java`
- Enums: `BookingStatus.java`, `PaymentStatus.java`

If two modules genuinely need to share a class (e.g. a `Money` value
object), put it in `util/` or `dto/common/` and flag it in your PR
description so the team knows it's shared, not module-owned.

## 2. Git workflow

- `main` is protected — no direct pushes.
- Branch per module: `feature/<module-name>`, e.g. `feature/booking`,
  `feature/payment`, `feature/hotel-partner`.
- Rebase (or merge) `main` into your branch periodically so you catch
  collisions early rather than at PR time.
- Open a PR to merge back into `main`. At least one other teammate
  should review before merging.
- Keep PRs scoped to your module. If you need to touch shared code
  (`security/`, `exception/`, `config/`), call that out explicitly in
  the PR description — those changes affect everyone.

## 3. Adding a new module

When you start your module (e.g. "Booking"), create:

| Layer      | File(s)                                                              |
|------------|-----------------------------------------------------------------------|
| entity     | `entity/Booking.java` — **extend `AuditableEntity`**, don't add your own createdAt/updatedAt |
| enums      | `enums/BookingStatus.java` (only if your module needs new enums)     |
| repo       | `repo/BookingRepository.java extends JpaRepository<Booking, Long>`   |
| dto        | `dto/BookingRequestDto.java`, `dto/BookingResponseDto.java`           |
| service    | `service/BookingService.java` (interface) + `service/BookingServiceImpl.java` |
| controller | `controller/BookingController.java`                                   |

Rules of thumb:
- Every new entity **must** extend `entity/AuditableEntity` so it
  automatically gets `createdAt`/`updatedAt`.
- Reference the shared `User` entity by its `id` (a `Long userId` /
  `@ManyToOne User`), don't duplicate user fields into your entity.
- Validation errors, not-found errors, and generic 500s are already
  handled centrally by `exception/GlobalExceptionHandler` — throw
  `ResourceNotFoundException` / `BadRequestException` (or add a new
  custom exception + a handler method if your module needs a new error
  type) rather than writing your own try/catch-and-format logic.
- Lock down endpoints with `@PreAuthorize("hasRole('...')")` on
  controller or service methods using the roles in `enums/Role.java`.
  Everything is authenticated-by-default already; you're adding
  *role* restrictions on top, not authentication itself.

## 4. Local setup

**Database (SQL Server / SSMS 22):**

1. Create a local database named `tourlk` in SSMS.
2. Enable SQL Server authentication (or use your Windows auth setup)
   and make sure TCP/IP is enabled on port `1433` (SQL Server
   Configuration Manager).
3. Copy `src/main/resources/application.yml` to
   `application-local.yml` in the same folder (this file is
   gitignored — never commit real credentials) and fill in:
   - `DB_USERNAME` / `DB_PASSWORD` (or hardcode them directly in your
     local copy)
   - `JWT_SECRET` if you want a different one locally (must be a
     Base64-encoded string, 256+ bits, for HS256)
   - `MAIL_USERNAME` / `MAIL_PASSWORD` if you're testing email-sending
     features
4. Run with your local profile active, e.g.:
   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```
   or just `mvn spring-boot:run` if you've edited `application.yml`
   directly with your own values (not recommended — prefer the
   gitignored local file).

**Running the app:**

```
mvn clean install
mvn spring-boot:run
```

The app starts on `http://localhost:8080`. With
`spring.jpa.hibernate.ddl-auto=update`, tables are created/updated
automatically from your entities — no manual DDL needed while we're
in this phase of the project.

## 5. Swagger UI is the shared API contract

Once the app is running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

**Check it before you design your own endpoints.** It reflects every
module's controllers, so it's the fastest way to:
- avoid two people independently building a `GET /api/bookings/{id}`-shaped
  path under different names,
- see what response shapes other modules use, so yours stay consistent,
- confirm your own endpoints show up correctly once your module is
  registered as a Spring bean.

If you're about to add a path that looks like it might already exist
in a slightly different form, ask in the team channel before building
it — it's much cheaper to rename now than after two modules depend on
conflicting conventions.
