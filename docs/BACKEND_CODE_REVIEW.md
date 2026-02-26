# Backend Critical Code Review — `src/MedAppServer`

> **Scope**: Spring Boot 4 / Kotlin backend under `src/MedAppServer/src/main/kotlin/org/kert0n/medappserver`
> **Stack**: Kotlin 2.2, Spring Boot 4.0.2, Spring Data JPA / Hibernate, PostgreSQL, JWT (Nimbus), Caffeine cache (Aedile wrapper)

---

## Table of Contents

1. [Global Architecture Report](#1-global-architecture-report)
   1. [Package Layout & Layering](#11-package-layout--layering)
   2. [DDD Assessment](#12-ddd-assessment)
   3. [Single Responsibility Principle Assessment](#13-single-responsibility-principle-assessment)
   4. [Dependency Direction & Coupling](#14-dependency-direction--coupling)
   5. [Cross-Cutting Concerns](#15-cross-cutting-concerns)
2. [SQL / JPA / Hibernate / DB Performance](#2-sql--jpa--hibernate--db-performance)
   1. [Fetch Strategies & N+1 Problems](#21-fetch-strategies--n1-problems)
   2. [Query Design](#22-query-design)
   3. [Index Coverage](#23-index-coverage)
   4. [Locking & Concurrency](#24-locking--concurrency)
   5. [Schema Management](#25-schema-management)
3. [Individual File Analysis](#3-individual-file-analysis)
   1. [Application Entry Point](#31-application-entry-point)
   2. [Controllers](#32-controllers)
   3. [JPA Entities (db/model)](#33-jpa-entities-dbmodel)
   4. [Repositories (db/repository)](#34-repositories-dbrepository)
   5. [Services (services/models)](#35-services-servicesmodels)
   6. [Orchestrators (services/orchestrators)](#36-orchestrators-servicesorchestrators)
   7. [Security (services/security)](#37-security-servicessecurity)
   8. [Infrastructure (CacheService, OpenApiConfiguration)](#38-infrastructure-cacheservice-openapiconfiguration)
4. [Summary of Actionable Recommendations](#4-summary-of-actionable-recommendations)

---

## 1. Global Architecture Report

### 1.1 Package Layout & Layering

The current package structure is:

```
org.kert0n.medappserver
├── controller/          ← REST controllers + DTOs
├── db/
│   ├── model/           ← JPA entities
│   │   └── parsed/      ← VidalDrug reference-data entities
│   └── repository/      ← Spring Data JPA repositories
└── services/
    ├── models/          ← "core" services (DrugService, MedKitService, etc.)
    ├── orchestrators/   ← cross-entity workflow services
    └── security/        ← security config, JWT, rate-limiting
```

**Observations:**

- The architecture follows a **three-layer pattern** (Controller → Service → Repository) which is conventional for Spring Boot applications but falls short of DDD.
- **DTOs are defined inside controller files.** For example, `DrugController.kt` contains `DrugDTO`, `DrugCreateDTO`, `DrugUpdateDTO`, `DrugTemplateDTO`, `ConsumeRequest`, `MoveDrugRequest`, and `QuantityInfo` — all as top-level classes in the same file. This inflates file size and mixes HTTP-layer concerns with data transfer concerns.
- The distinction between `services/models/` and `services/orchestrators/` is a positive separation, indicating awareness of service composition vs. single-entity operations. However, naming is inconsistent: the package is called `models` but contains services, not domain models.
- There is **no dedicated domain layer**. Business rules are scattered between services, orchestrators, and even entities (`@Formula` in `Drug`).
- **No DTO/mapper layer** exists. Mapping between entities and DTOs is done inline inside services (`toDrugDTO`, `toMedKitDTO`, `toUsingDTO`), which couples the service layer to the controller-layer DTOs.

### 1.2 DDD Assessment

The codebase does **not** follow Domain-Driven Design. Key DDD gaps:

| DDD Concept | Current State | Recommendation |
|---|---|---|
| **Bounded Contexts** | Not defined. All entities share a single persistence unit. | Define at least two bounded contexts: *MedKit Management* (User, MedKit, Drug) and *Catalog/Reference* (VidalDrug, FormType, QuantityUnit). |
| **Aggregates & Aggregate Roots** | No explicit aggregates. `MedKit` is the natural aggregate root for `Drug` and `Using`, but this is not enforced — services access `Drug` and `Using` directly through their own repositories. | Make `MedKit` the aggregate root; access drugs only through `MedKit`. |
| **Value Objects** | None. `UsingKey` is an `@Embeddable` but is implemented as a mutable class rather than a proper value object. | Make `UsingKey` a Kotlin `data class` or at minimum make its fields `val`. |
| **Domain Events** | Not used. The `QuantityReductionService` performs side-effects (adjusting usings) synchronously and imperatively. | Use Spring `ApplicationEvent` for cross-aggregate side-effects. |
| **Repository pattern** | Spring Data repositories are used, which is fine, but they are accessed from multiple service layers, bypassing aggregate boundaries. `MedKitDrugServices` directly uses `DrugRepository`, `MedKitRepository`, and `UsingRepository`. | Repositories should be scoped to their aggregate roots. |
| **Anti-Corruption Layer** | The `VidalDrug` reference data shares the same JPA context as user data. | Isolate the catalog data behind a read-only service with its own repository. |

### 1.3 Single Responsibility Principle Assessment

| Component | SRP Violations | Details |
|---|---|---|
| `DrugController` | **High** | Handles CRUD for drugs, quantity info, drug consumption, drug movement between medkits, AND drug template search/retrieval. This is at least 3 distinct responsibilities. |
| `SecurityService` | **Medium** | Combines password hashing, JWT token generation/validation, rate-limit tracking, and generic key generation. |
| `CacheService` | **Low** | Defines two unrelated cache beans. Would be cleaner as separate `@Configuration` methods. |
| `MedKitDrugServices` | **Medium** | Combines drug creation in medkit, drug movement, user removal from medkit, medkit deletion with transfer, AND DTO mapping (`toMedKitDTO`). Naming also misleads — it is not "Services" (plural) but an orchestrator. |
| `UsingService` | **Medium** | Manages CRUD for treatment plans AND handles intake recording with drug quantity modification (side-effect on another entity). |
| `DrugService` | **Medium** | Manages Drug CRUD AND drug consumption AND DTO conversion. |
| `MedKitService` | **Medium** | Manages MedKit CRUD AND share key generation/joining (which is an access-control concern). |

### 1.4 Dependency Direction & Coupling

**Critical issue**: The repository layer (`MedKitRepository`) imports a controller-layer DTO (`MedKitSummaryDTO`):

```kotlin
// MedKitRepository.kt
import org.kert0n.medappserver.controller.MedKitSummaryDTO
```

This violates the layering rule that lower layers should never depend on upper layers. The `MedKitSummaryDTO` is constructed inside a JPQL `SELECT NEW` expression in the repository. This should use a projection interface or a dedicated query DTO in the repository/domain layer instead.

**Service-to-service coupling**: `DrugService` depends on `QuantityReductionService` (an orchestrator), and `UsingService` also depends on `QuantityReductionService`. Meanwhile `QuantityReductionService` depends on `UsingRepository` and `DrugRepository` directly. This creates a bi-directional dependency pattern between the service and orchestrator layers.

### 1.5 Cross-Cutting Concerns

- **Error handling**: Done via `ResponseStatusException` thrown from services. This is acceptable for small apps but tightly couples the service layer to HTTP semantics. A dedicated exception hierarchy (e.g., `EntityNotFoundException`, `InsufficientQuantityException`) mapped to HTTP status codes via `@ControllerAdvice` would be cleaner and more testable.
- **Validation**: Uses Jakarta Bean Validation on DTOs (`@Valid`, `@NotNull`, `@Size`, `@DecimalMin`), which is good. However, there is no validation at the domain/entity level beyond column constraints.
- **Logging**: Consistent use of SLF4J `LoggerFactory`, but some loggers are injected as constructor parameters (e.g., `MedKitController`, `UsingsController`, `MedKitDrugServices`) while others are created as class properties (e.g., `DrugController`, `UserController`). This inconsistency is cosmetic but should be standardized.
- **Transaction management**: `@Transactional` annotations are used on individual service methods. Some methods that should be transactional are not annotated (e.g., `MedKitService.generateMedKitShareKey` performs a read inside a cache write without a transaction).

---

## 2. SQL / JPA / Hibernate / DB Performance

### 2.1 Fetch Strategies & N+1 Problems

| Location | Issue | Severity |
|---|---|---|
| `Drug.totalPlannedAmount` uses `@Formula` | Executes a correlated subquery (`SELECT COALESCE(SUM(...))`) **every time** a `Drug` entity is loaded. When loading a list of drugs (e.g., `findAllByMedKitId`), this triggers N additional queries. | 🔴 **High** |
| `MedKitDrugServices.toMedKitDTO()` | Calls `drugRepository.findAllWithUsingsByMedKitId()` and then `drugService.toDrugDTO()` for each drug. `toDrugDTO` accesses `drug.medKit.id` — with `FetchType.LAZY` on `Drug.medKit`, this triggers a lazy load per drug if the medkit proxy is not initialized. | 🟡 **Medium** |
| `UserController.getAllDataForUser()` | Calls `medKitService.findAllByUser()` (loads medkits), then for each medkit calls `medKitDrugServices.toMedKitDTO()` which runs another query per medkit. This is O(N) queries for N medkits. | 🟡 **Medium** |
| `Using.user` and `Using.drug` are `FetchType.EAGER` | These are always loaded even when only the `Using` fields are needed (e.g., deletion, bulk update). | 🟡 **Medium** |
| `VidalDrug.formType` and `VidalDrug.quantityUnit` are `FetchType.EAGER` | For the `fuzzySearchByName` native query, Hibernate will issue separate queries to resolve these associations unless they are included in the SELECT. | 🟡 **Medium** |

### 2.2 Query Design

| Query | File | Issue |
|---|---|---|
| `findMedKitSummariesByUserId` | `MedKitRepository` | Uses a correlated subquery in the WHERE clause (`mk.id IN (SELECT m.id FROM MedKit m JOIN m.users us WHERE us.id = :userId)`) which could be replaced with a simpler JOIN. |
| `fuzzySearchByName` | `VidalDrugRepository` | The native query uses `similarity()` function (pg_trgm) combined with `ILIKE`. This is well-designed for fuzzy search, but the `LIMIT` is passed as a parameter rather than using `Pageable`, losing Spring Data pagination support. The query also computes `similarity()` twice (once in WHERE, once in ORDER BY). |
| `@Formula` on `Drug.totalPlannedAmount` | `Drug` entity | Native SQL formula `(SELECT COALESCE(SUM(u.planned_amount), 0) FROM usings u WHERE u.drug_id = id)` is evaluated on every entity load. Consider making this a derived/computed column at the DB level, or computing it on-demand via a repository method. |
| `findByIdAndUsingsUserIdWithUsing` | `DrugRepository` | Misleading name: it joins `medKit.users`, not `usings`, despite the name suggesting it filters by "usings user ID". |
| `reassignMedKit` | `MedKitRepository` | Bulk UPDATE via `@Modifying @Query`. This bypasses Hibernate's dirty checking and could leave the persistence context stale. No `clearAutomatically = true` is set. |

### 2.3 Index Coverage

Defined indices:

| Table | Index | Columns | Assessment |
|---|---|---|---|
| `users` | `ix_users_hashed_key` | `hashed_key` (unique) | ✅ Good — supports login lookups. |
| `user_drugs` | `ix_user_drugs_name` | `name` | 🟡 Unclear utility — no queries filter drugs by name alone. |
| `user_drugs` | `ix_user_drugs_med_kit_id` | `med_kit_id` | ✅ Good — supports `findAllByMedKitId`. |
| `usings` | `ix_usings_user_id` | `user_id` | ✅ Good — supports `findAllByUsingKeyUserId`. |
| `usings` | `ix_usings_drug_id` | `drug_id` | ✅ Good — supports the `@Formula` subquery and `findAllByUsingKeyDrugId`. |
| `parsed_drugs` | Multiple | `name`, `form_type_id`, `quantity_unit_id`, `active_substance`, `manufacturer` | ✅ Good for the fuzzy search and lookups. |

**Missing indices:**
- `user_med_kits` join table: no explicit indices defined for `user_id` or `med_kit_id`. JPA will create the join table, but Spring/Hibernate does not automatically index join table columns. This affects all queries that join `MedKit` through `User.medKits`.

### 2.4 Locking & Concurrency

- `findByIdAndMedKitUsersIdForUpdate` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` — good for preventing concurrent drug quantity modifications.
- However, `consumeDrug` in `DrugService` calls `findByIdForUser` (no lock) instead of `findByIdForUserForUpdate`, creating a **race condition** on concurrent consumption. Only `update` properly acquires the pessimistic lock.
- `recordIntake` in `UsingService` calls `findByUserAndDrug` (no lock), then modifies `drug.quantity`. Two concurrent intakes could read the same quantity and both succeed, resulting in **negative stock**.
- `QuantityReductionService.handleQuantityReduction` performs `findAllByUsingKeyDrugId` + save in a loop without explicit locking. If two concurrent quantity reductions occur, the proportional reduction could be applied twice.

### 2.5 Schema Management

- **Dev profile** uses `spring.jpa.hibernate.ddl-auto=update`. This is acceptable for development but dangerous for production — Hibernate's `update` strategy never drops columns or tables and can produce schema drift.
- No **Flyway or Liquibase** migration tool is configured. For production readiness, DDL should be managed by versioned migration scripts.
- The `@Formula` annotation uses raw SQL that references table/column names directly (`usings`, `planned_amount`, `drug_id`). This is fragile and will break if table/column names are changed.

---

## 3. Individual File Analysis

### 3.1 Application Entry Point

#### `MedAppServerApplication.kt`

- **Purpose**: Spring Boot entry point.
- **Issues**: None. Clean and minimal.
- **Recommendation**: None.

### 3.2 Controllers

#### `AuthController.kt`

- **Purpose**: Registration and JWT token issuance.
- **Strengths**: Clear separation of registration and login. Good use of Swagger annotations.
- **Issues**:
  1. `RegisterResponse` and other DTOs are declared as nested classes inside the controller. These should be in a separate file.
  2. The `registrationSecret` is compared with `!=` (string equality via reference). While Kotlin's `!=` for `String` delegates to `equals()`, this is a timing-attack-safe comparison concern — use `MessageDigest.isEqual()` for secret comparison.
  3. Uses `HttpStatus.GATEWAY_TIMEOUT` (504) for rate-limiting, which is semantically incorrect. `429 Too Many Requests` is the standard status code.
  4. The `register` endpoint is `@PostMapping` which is correct, but `login` is `@GetMapping`. GET endpoints should not be used for authentication actions that produce tokens, as URLs (including credentials) may be logged.
- **SRP**: Acceptable — single concern (authentication).

#### `DrugController.kt`

- **Purpose**: Drug CRUD, quantity info, consumption, movement, and template search.
- **Issues**:
  1. **SRP violation**: This controller handles at least 4 distinct functional areas. Template search (`/drug/template/search`, `/drug/template/{id}`) should be a separate `DrugTemplateController`. Drug movement (`/drug/move/{id}`) should be on `MedKitController` or a dedicated controller. Drug consumption (`/drug/consume/{id}`) could be on `UsingsController` since it relates to treatment plans.
  2. **Large file**: 370+ lines including 7 DTO classes defined inline. DTOs should be extracted to a `dto/` package.
  3. `consumeDrug` returns `DrugDTO?` (nullable). If the drug is deleted after consumption (quantity reaches 0), the response is `null`, which will serialize to an empty 200 response. This is confusing — a 204 No Content or a specific response would be clearer.
  4. The `searchDrugTemplates` endpoint performs DTO mapping inline in the controller instead of delegating to the service.
  5. `ConsumeRequest` allows `@DecimalMin("0.0")` — consuming zero units is valid input but semantically nonsensical.

#### `MedKitController.kt`

- **Purpose**: MedKit CRUD, sharing, and joining.
- **Issues**:
  1. `generateKeyToMedKit` accepts an `AddUserRequest` in the request body but never uses the `userId` from it. The endpoint generates a generic share key. The request body parameter is misleading and unused.
  2. DTOs (`MedKitDTO`, `MedKitSummaryDTO`, `MedKitCreatedResponse`, `AddUserRequest`, `JoinMedKitRequest`) are partially inside the class and partially outside, with inconsistent placement.
  3. Logger is injected via constructor with a default value. While this works, it's atypical and could cause issues with mocking in tests.
- **SRP**: Acceptable — focused on MedKit management.

#### `UserController.kt`

- **Purpose**: Returns a full user data snapshot for synchronization.
- **Issues**:
  1. The `getAllDataForUser` method loads ALL medkits and ALL drugs for a user in a single request. For users with many medkits/drugs, this could be very slow and memory-intensive. Consider pagination or lazy endpoints.
  2. The mapping `medKitService.findAllByUser(...).map { medKitDrugServices.toMedKitDTO(it) }` triggers N+1 queries (one `findAllWithUsingsByMedKitId` per medkit).
- **SRP**: Good — single purpose.

#### `UsingsController.kt`

- **Purpose**: Treatment plan CRUD and intake recording.
- **Issues**:
  1. `recordRegularUsing` returns `UsingDTO?` (nullable). Same issue as `DrugController.consumeDrug` — returning null for a completed plan is unclear API design.
  2. `getSpecificUsing` returns `UsingDTO?` (nullable). If the using does not exist, this returns null/200 instead of 404. But `findByUserAndDrug` actually throws 404. There's a mismatch — this method can never return null in practice.
  3. `IntakeRequest` allows `@DecimalMin("0.0")` — consuming zero is semantically meaningless.
- **SRP**: Good — focused on treatment plans.

### 3.3 JPA Entities (`db/model`)

#### `Drug.kt`

- **Issues**:
  1. `@Formula` for `totalPlannedAmount` causes a correlated subquery on every load. This is a significant performance concern (see Section 2.1).
  2. `quantity` is `Double`. Floating-point arithmetic is imprecise for quantities — consider `BigDecimal`.
  3. `description` column uses `Integer.MAX_VALUE` as length — this is effectively unbounded. Use `@Lob` or a reasonable limit.
  4. `medKit` is `FetchType.LAZY` (correct), but `usings` is also lazy with `CascadeType.ALL` and `orphanRemoval = true`. This means deleting a `Drug` cascades deletion to all `Using` records, which is correct, but moving a drug between medkits could accidentally delete usings if not handled carefully.
  5. The class uses mutable `var` fields, which is standard for JPA but anti-DDD (entities should protect their invariants).

#### `MedKit.kt`

- **Issues**:
  1. `users` is `@ManyToMany(mappedBy)` with `CascadeType.PERSIST, MERGE`. This means persisting a `MedKit` also persists/merges `User` objects. While intentional, cascading to `User` from `MedKit` can cause unexpected side effects.
  2. `drugs` has `CascadeType.ALL` and `orphanRemoval = true` — appropriate for aggregate root behavior.
  3. No business methods — it's a pure data holder (anemic model, anti-DDD).

#### `User.kt`

- **Issues**:
  1. Implements `UserDetails` directly on the JPA entity. This couples the persistence model to Spring Security's authentication contract. A better approach is to have a separate `UserDetails` adapter/wrapper.
  2. `getPassword()` returns `hashedKey` — this works because Spring Security's `DaoAuthenticationProvider` will compare the raw password against this hashed value, but the naming is confusing since `hashedKey` is already hashed.
  3. `medKits` uses `FetchType.LAZY` — correct.
  4. `usings` uses `FetchType.LAZY` — correct.

#### `Using.kt`

- **Issues**:
  1. `user` and `drug` are `FetchType.EAGER`. This means every `Using` query automatically loads the full `User` and `Drug` entities, including `Drug`'s `@Formula` subquery. For bulk operations (e.g., `findAllByUsingKeyDrugId`), this is very expensive.
  2. `UsingKey` fields are mutable `var`. As an embeddable key, they should be immutable.
  3. `UsingKey` uses `UUID(0, 0)` as a default value. This is a code smell — it means an `UsingKey` can be constructed in an invalid state.
  4. `plannedAmount` is `Double` — same precision concern as `Drug.quantity`.

#### `FormType.kt` / `QuantityUnit.kt`

- **Issues**:
  1. Use `open` classes and `open var` properties, which is required for JPA lazy proxying but makes the classes mutable. Since these are reference data, they should ideally be immutable.
  2. These are simple lookup tables. Consider using an `enum` mapped via `@Enumerated` if the set of values is small and stable.
- **Assessment**: Acceptable for reference data.

#### `VidalDrug.kt`

- **Issues**:
  1. `formType` and `quantityUnit` are `FetchType.EAGER`. For the fuzzy search which returns lists, this triggers N+1 queries for form types and quantity units (unless Hibernate batches them).
  2. `open class` with `open var` — same as `FormType`/`QuantityUnit`.
  3. No `@Column(length = ...)` on `description` with `Integer.MAX_VALUE` — same as `Drug`.
- **Assessment**: Acceptable but could benefit from eager JOIN FETCH in the search query.

### 3.4 Repositories (`db/repository`)

#### `DrugRepository.kt`

- **Issues**:
  1. Contains commented-out code (lines 57-64). Dead code should be removed.
  2. `findByIdAndUsingsUserIdWithUsing` — misleading name. The query joins `medKit.users`, not `usings.user`. Name suggests it filters by usings' user ID, but it filters by the medkit's users.
  3. `findAllWithUsingsByMedKitId` — relies on Spring Data method name derivation with `@EntityGraph`. The derived name `findAllWithUsingsByMedKitId` is somewhat unconventional; an explicit `@Query` would be clearer.
  4. `@Suppress("FunctionName")` at file level — this suppresses naming convention warnings. Better to follow conventions.

#### `MedKitRepository.kt`

- **Issues**:
  1. **Layering violation**: Imports `MedKitSummaryDTO` from the controller package. The repository should not know about controller-layer classes. Use an interface projection or a repository-layer DTO.
  2. `reassignMedKit` uses `@Modifying` without `clearAutomatically = true`. After this bulk update, the persistence context may contain stale entities. This can cause bugs in the same transaction.
  3. Multiple similar queries (`findByIdAndUserId`, `findByIdAndUsersIdWithUsers`, `findByIdAndUserIdForDeletion`) that differ only in their fetch graphs. Consider consolidating with `@EntityGraph` named graphs.

#### `UserRepository.kt`

- **Issues**: Minor. Clean interface. No significant issues.

#### `UsingRepository.kt`

- **Issues**:
  1. `deleteByUserIdAndMedKitId` uses `@Modifying @Query` — same stale persistence context concern.
  2. `findAllByUserIdWithDrug` fetches `drug` eagerly but `drug` already has `EAGER` fetch on its `Using` side. This could cause redundant fetching.

#### `VidalDrugRepository.kt`

- **Issues**:
  1. The native query is well-designed for fuzzy search.
  2. `LIMIT :limit` as a query parameter instead of `Pageable` — acceptable for this use case but loses Spring Data pagination support (total count, pages).

### 3.5 Services (`services/models`)

#### `DrugService.kt`

- **Issues**:
  1. **SRP concern**: Handles CRUD, consumption, and DTO mapping in a single class.
  2. `consumeDrug` does not acquire a pessimistic lock before modifying quantity. It calls `findByIdForUser` (no lock), creating a race condition for concurrent consumption.
  3. `consumeDrug` returns `Drug?` (nullable) — if the drug is consumed to zero, `QuantityReductionService.handleQuantityReduction` deletes it and returns `null`. This null propagates up to the controller as a null JSON response.
  4. `toDrugDTO` is marked `@Transactional(readOnly = true)` — a mapping method should not need a transaction. The `@Transactional` is likely there because `drug.medKit.id` triggers a lazy load. This is a symptom of the architecture not properly loading data before mapping.
  5. `findByIdForUserForUpdate` is marked `@Transactional(readOnly = true)` but acquires a `PESSIMISTIC_WRITE` lock. A read-only transaction with a write lock is contradictory and may not work correctly with some JDBC drivers/databases.
  6. `create` method takes both a `DrugCreateDTO` (controller layer) and a `MedKit` entity (persistence layer) — mixing abstraction levels.

#### `MedKitService.kt`

- **Issues**:
  1. Declared `open class` — unusual for a Spring `@Service` unless needed for CGLIB proxying of `@Transactional` methods. Since Kotlin classes are `final` by default, Spring's kotlin plugin normally handles this via `allOpen`. The explicit `open` is redundant.
  2. `generateMedKitShareKey` is not `@Transactional` but calls `findByIdForUser` (which is transactional). The access check and cache write are not atomic.
  3. `joinMedKitByKey` calls `addUserToMedKit` which starts a new transaction. But the cache lookup + the add should be atomic — if the cache entry is consumed but the add fails, the key is lost.
  4. `removeUserFromMedKit` has a log message with a placeholder for `deleteAllDrugs` parameter that doesn't exist in the method signature (leftover from refactoring).

#### `UserService.kt`

- **Issues**:
  1. Very thin — essentially a pass-through to the repository with password hashing. This is acceptable for the current scope.
  2. `findById` and `findAllByDrug` are not annotated with `@Transactional`.
  3. The `userId` extension property on `Authentication` is defined in this file. It's a useful utility but should be in a separate utility file for discoverability.
  4. `registerNewUser` is not `@Transactional`. The `save` and `registerIncrease` are not atomic — if `registerIncrease` fails, the user is still created.

#### `UsingService.kt`

- **Issues**:
  1. `recordIntake` modifies `using.drug.quantity` directly, bypassing `DrugService`. This breaks the encapsulation of the Drug entity and creates multiple code paths for quantity modification.
  2. The comment `// IMPORTANT! THIS MUST ALWAYS BE BEFORE QUANTITY REDUCTION` is a code smell — ordering dependencies between operations should be enforced by the architecture, not comments.
  3. `recordIntake` manually updates `using.drug.totalPlannedAmount -= quantityConsumed` to avoid reloading. This creates a risk of the in-memory value drifting from the database value (since `totalPlannedAmount` is computed by `@Formula`).
  4. `logger` is `val` (public) — should be `private`.
  5. `deleteAllByUserIdInMedkit` is not annotated with `@Transactional` despite executing a `@Modifying` query.

#### `VidalDrugService.kt`

- **Issues**:
  1. Clean and minimal. No significant issues.
  2. Missing `@Transactional(readOnly = true)` on both methods — not critical since the operations are read-only by nature, but explicit annotation is better practice.

### 3.6 Orchestrators (`services/orchestrators`)

#### `MedKitDrugServices.kt`

- **Issues**:
  1. **SRP violation**: Handles drug creation, drug movement, user removal, medkit deletion with drug transfer, AND DTO mapping. These are at least 4 distinct responsibilities.
  2. The `logger` is initialized with `DrugService::class.java` instead of `MedKitDrugServices::class.java` — likely a copy-paste error. Logs from this class will appear under the wrong logger name.
  3. `delete` method is complex (30+ lines) with multiple concerns: fetching, computing users with access, removing usings, reassigning drugs, unlinking users, and deleting the medkit. This should be broken into smaller methods.
  4. `delete` modifies `drug.medKit = targetMedKit` and `targetMedKit.drugs.add(drug)` in a loop, then clears `medKit.drugs`. This in-memory manipulation could be replaced with a bulk `UPDATE` (similar to `reassignMedKit`) for better performance.
  5. `removeUserFromMedKit` loads all drugs with usings via `findAllWithUsingsByMedKitId`, then filters usings in memory. For large medkits, this could be expensive. A single `DELETE FROM Using WHERE userId = :userId AND drug.medKit.id = :medKitId` query (which already exists in `UsingRepository`) would be more efficient.
  6. Direct access to `DrugRepository` and `MedKitRepository` bypasses the service layer, breaking layered architecture.

#### `QuantityReductionService.kt`

- **Issues**:
  1. `handleQuantityReduction` is not `@Transactional`. It modifies and saves entities (`drugRepository.delete`, `drugRepository.save`, `usingRepository.saveAll`) without its own transaction boundary. It relies on the caller's transaction, but this is not documented or enforced.
  2. `handleQuantityReduction` deletes the drug when `quantity == 0.0`. Comparing floating-point values with `==` is unreliable. Use `quantity <= 0.0` or compare with a tolerance.
  3. The proportional reduction (`reduceFactor = drug.quantity / drug.totalPlannedAmount`) distributes cuts equally across all treatment plans. This may not be the desired business behavior — e.g., some plans may be higher priority.
  4. The logger is initialized with `UsingService::class.java` — incorrect class reference (copy-paste error).
  5. The `// TODO FIREBASE NOTIFICATION` comment indicates incomplete functionality.

### 3.7 Security (`services/security`)

#### `SecurityConfiguration.kt`

- **Issues**:
  1. CSRF is disabled (`csrf.disable()`). This is acceptable for a stateless API using JWT, but should be explicitly documented.
  2. `httpBasic { }` is enabled alongside JWT (`oauth2ResourceServer`). This dual-authentication setup means the `/auth/login` endpoint uses HTTP Basic while all other endpoints use JWT Bearer. This is functional but potentially confusing — the Basic auth handler applies to ALL endpoints, meaning invalid Basic credentials will be rejected even on JWT-protected endpoints.
  3. No CORS configuration — if the API is accessed from a web frontend, this will need to be added.
- **Assessment**: Functional for the current use case.

#### `SecurityService.kt`

- **Issues**:
  1. `generateKey` uses `SecureRandom` — good.
  2. `hashToken` uses `SHA-256` for share keys. This is appropriate for one-time tokens but not for passwords (use bcrypt/scrypt/argon2 for passwords — which is handled by `hashPassword`).
  3. `validateRequest` and `registerIncrease` are not thread-safe for the cache operations. Two concurrent registrations from the same IP could both read `null` and both set the count to 1, bypassing the rate limit.
  4. The rate limit uses the raw IP address as the cache key. The `AuthController` calls `securityService.validateRequest(request.remoteAddr)` but `registerIncrease` is called after `userService.registerNewUser`. If `registerNewUser` fails, the rate limit is not incremented, which is correct. However, the ARCHITECTURE.md states IPs are stored "in hashed form" — in the actual code, `registerIncrease` and `validateRequest` use the raw IP, not a hash. This is a documentation-code mismatch.
  5. `check` method exists but is never called anywhere in the codebase — dead code.

#### `RsaKeyProperties.kt`

- **Assessment**: Clean data class for RSA key configuration. No issues.

### 3.8 Infrastructure

#### `CacheService.kt`

- **Issues**:
  1. Annotated as `@Service` but functions as a `@Configuration` — it only defines `@Bean` methods. Should be `@Configuration` for clarity.
  2. Both caches use `maximumSize(10_000)`. For a production application, these limits should be configurable.
  3. The Aedile/Caffeine cache is a good choice for local caching but does not support distributed scenarios (multi-instance deployments). If the application scales horizontally, share keys cached on one instance won't be visible to another.

#### `OpenApiConfiguration.kt`

- **Assessment**: Clean Swagger/OpenAPI configuration. No issues.

---

## 4. Summary of Actionable Recommendations

### Critical (Should Fix)

| # | Issue | Location | Impact |
|---|---|---|---|
| 1 | **Race condition in `consumeDrug`** — no pessimistic lock acquired before modifying quantity | `DrugService.consumeDrug` | Data corruption: concurrent consumption can result in negative stock |
| 2 | **Race condition in `recordIntake`** — no lock on drug entity during intake | `UsingService.recordIntake` | Data corruption: same as above |
| 3 | **`@Formula` N+1 queries** — `totalPlannedAmount` triggers a correlated subquery per entity load | `Drug.totalPlannedAmount` | Performance degradation proportional to number of drugs loaded |
| 4 | **Repository imports controller DTO** — layering violation | `MedKitRepository` → `MedKitSummaryDTO` | Architecture violation, tight coupling between layers |
| 5 | **`findByIdForUserForUpdate` marked `readOnly = true`** — contradicts `PESSIMISTIC_WRITE` lock | `DrugService` | May cause lock to not be acquired on some DB drivers |

### Important (Should Address)

| # | Issue | Location | Impact |
|---|---|---|---|
| 6 | Floating-point `Double` for quantities and monetary-like amounts | `Drug.quantity`, `Using.plannedAmount` | Precision errors in arithmetic |
| 7 | SRP violation — `DrugController` handles 4+ concerns | `DrugController` | Maintainability, testability |
| 8 | No `@Transactional` on mutating methods | `UserService.registerNewUser`, `UsingService.deleteAllByUserIdInMedkit`, `QuantityReductionService` | Potential partial writes |
| 9 | `User` implements `UserDetails` — coupling persistence to security | `User.kt` | Difficult to evolve independently |
| 10 | Wrong logger class in orchestrators | `MedKitDrugServices`, `QuantityReductionService` | Misleading log output |
| 11 | Incorrect HTTP status 504 for rate limiting | `AuthController.register` | Semantically wrong; should be 429 |
| 12 | `Using.user`/`Using.drug` are `EAGER` | `Using.kt` | Unnecessary data loading on bulk queries |
| 13 | `@Modifying` queries without `clearAutomatically = true` | `MedKitRepository.reassignMedKit`, `UsingRepository.deleteByUserIdAndMedKitId` | Stale persistence context |
| 14 | No migration tool (Flyway/Liquibase) | `application-dev.properties` | Schema drift risk in production |
| 15 | IP addresses stored in plain text in cache despite docs claiming hashed | `SecurityService.validateRequest` / `registerIncrease` | Privacy documentation mismatch |

### Minor (Nice to Have)

| # | Issue | Location | Impact |
|---|---|---|---|
| 16 | DTOs defined inline in controller files | All controllers | Code organization |
| 17 | Inconsistent logger initialization (constructor vs. companion) | Various | Code style |
| 18 | `CacheService` annotated `@Service` instead of `@Configuration` | `CacheService.kt` | Semantic clarity |
| 19 | Commented-out code in `DrugRepository` | `DrugRepository.kt` | Code cleanliness |
| 20 | `UsingKey` fields are mutable `var` with dummy defaults | `Using.kt` | Value object should be immutable |
| 21 | `SecurityService.check` method is unused | `SecurityService.kt` | Dead code |
| 22 | Timing-attack vulnerability in secret comparison | `AuthController.register` | Security hardening |
| 23 | `ConsumeRequest`/`IntakeRequest` allow zero quantity | `DrugController`, `UsingsController` | Input validation |
| 24 | `MedKitController.generateKeyToMedKit` has unused `AddUserRequest` body | `MedKitController` | API clarity |
