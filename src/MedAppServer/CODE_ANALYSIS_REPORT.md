# Comprehensive Code Analysis Report - MedApp REST API Server

**Date**: 2026-02-18  
**Analyst**: GitHub Copilot  
**Project**: MedApp - Medicine Organizer REST API  
**Technology Stack**: Spring Boot 3.4.1, Kotlin 2.1.0, PostgreSQL 15+, JPA/Hibernate

---

## Executive Summary

This report provides a comprehensive analysis of the MedApp codebase, focusing on best practices, common anti-patterns, edge cases, and potential issues. The project has 1,923 lines of production code and 1,597 lines of test code with 94 passing tests (100% pass rate).

### Overall Assessment

**Grade: B+ (Good with some improvements needed)**

✅ **Strengths:**
- Privacy-by-design architecture properly implemented
- Comprehensive test coverage with H2 in-memory database
- Proper use of JPA entity graphs to prevent N+1 queries
- Good separation of concerns (Controller → Service → Repository)
- Proper transaction management
- Helper methods for bidirectional relationships
- OpenAPI/Swagger documentation started

⚠️ **Areas for Improvement:**
- **CRITICAL**: Extensive use of `var` in Hibernate-managed entities (anti-pattern)
- Missing validation on some DTOs
- Incomplete controller test coverage
- Some entity design issues
- Limited use of immutability

---

## 1. Hibernate Entity Anti-Patterns

### 🔴 CRITICAL ISSUE: Inappropriate use of `var` in JPA Entities

**Problem**: All entity fields use `var` (mutable) instead of `val` (immutable) where appropriate.

#### Found Issues:

```kotlin
// ❌ BAD - Everything is var
@Entity
class Drug (
    var id: UUID = UUID.randomUUID(),          // Should be val
    var name: String,                          // Could be val
    var quantity: Double,                      // Needs to be var (changes)
    var quantityUnit: String,                  // Could be val
    var formType: String?,                     // Could be val
    var category: String?,                     // Could be val
    var manufacturer: String?,                 // Could be val
    var country: String?,                      // Could be val
    var description: String?,                  // Could be val
    var medKit: MedKit,                       // NOW var (needed for transfer)
    val usings: MutableSet<Using> = mutableSetOf(), // ✅ Correctly val
)
```

#### Why This Is A Problem:

1. **ID fields should ALWAYS be `val`**: Entity IDs should never change after creation
2. **Immutable fields should be `val`**: Fields that don't change (name, form, category) should be immutable
3. **Collections should be `val`**: The collection reference shouldn't change, only contents
4. **Business logic clarity**: `var` everywhere makes it unclear which fields are meant to be mutable

#### Recommended Fix:

```kotlin
// ✅ GOOD - Proper immutability
@Entity
class Drug (
    val id: UUID = UUID.randomUUID(),          // ✅ val - IDs never change
    val name: String,                          // ✅ val - name doesn't change
    var quantity: Double,                      // ✅ var - quantity changes
    val quantityUnit: String,                  // ✅ val - unit doesn't change
    val formType: String?,                     // ✅ val - form doesn't change
    val category: String?,                     // ✅ val - category doesn't change
    val manufacturer: String?,                 // ✅ val - manufacturer doesn't change
    val country: String?,                      // ✅ val - country doesn't change
    var description: String?,                  // ✅ var - description can be updated
    var medKit: MedKit,                       // ✅ var - drug can be moved
    val usings: MutableSet<Using> = mutableSetOf(), // ✅ val - reference is immutable
)
```

#### Impact:

- **All 4 entity classes** (User, Drug, MedKit, Using) have this issue
- **22 fields** unnecessarily use `var` instead of `val`
- Increases risk of accidental mutations
- Makes code harder to reason about
- Goes against Kotlin best practices

---

## 2. Entity Design Issues

### 2.1 Missing Fields in Entities vs. Requirements

**Analysis**: According to the requirements document (section 4.1.1.1.1), drugs should have additional fields:

**Required but MISSING:**
- ❌ `expiryDate` (срок годности) - **CRITICAL for functionality**
- ❌ Last use tracking

**Optional but MISSING:**
- Single dose amount (однократная доза) - Should be in Drug entity
- Purchase date (дата покупки)
- Price (цена)  
- Notes/remarks field (заметки до 200 символов)
- Opening date (дата вскрытия упаковки)

**User's Note**: The user stated these fields will be stored on the client side only, not on the server. This is acceptable but should be documented in API documentation.

### 2.2 Entity Relationship Issues

**User (Good):**
```kotlin
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "user_med_kits",
    joinColumns = [JoinColumn(name = "user_id")],
    inverseJoinColumns = [JoinColumn(name = "med_kit_id")]
)
var medKits: MutableSet<MedKit> = mutableSetOf(),  // ✅ Owning side
```

**MedKit (Good):**
```kotlin
@ManyToMany(mappedBy = "medKits", fetch = FetchType.LAZY, 
    cascade = [CascadeType.PERSIST, CascadeType.MERGE])
var users: MutableSet<User> = mutableSetOf(),  // ✅ Inverse side
```

**Issue**: Previously was trying to sync both sides manually, now fixed with helper methods in User class.

### 2.3 Cascade Configuration Issues

**Drug usings cascade:**
```kotlin
@OneToMany(mappedBy = "drug", fetch = FetchType.LAZY, 
    cascade = [CascadeType.ALL], orphanRemoval = true)
val usings: MutableSet<Using> = mutableSetOf()
```
✅ **Correct** - When drug is deleted, all treatment plans should be deleted

**MedKit users cascade:**
```kotlin
@ManyToMany(mappedBy = "medKits", fetch = FetchType.LAZY, 
    cascade = [CascadeType.PERSIST, CascadeType.MERGE])
var users: MutableSet<User> = mutableSetOf()
```
✅ **Correct** - Don't cascade delete users when medkit is deleted

**MedKit drugs cascade:**
```kotlin
@OneToMany(mappedBy = "medKit", fetch = FetchType.LAZY, 
    cascade = [CascadeType.ALL], orphanRemoval = true)
var drugs: MutableSet<Drug> = mutableSetOf()
```
✅ **Correct** - When medkit is deleted, drugs can be deleted (or moved)

---

## 3. Service Layer Analysis

### 3.1 Transaction Management

✅ **GOOD**: All service methods properly annotated with `@Transactional`

```kotlin
@Service
class DrugService(...) {
    @Transactional(readOnly = true)  // ✅ Read-only for queries
    fun findById(drugId: UUID): Drug { ... }
    
    @Transactional                   // ✅ Read-write for mutations
    fun create(createDTO: DrugCreateDTO, userId: UUID): Drug { ... }
}
```

### 3.2 N+1 Query Prevention

✅ **GOOD**: Using JPQL with JOIN FETCH to prevent N+1 queries

```kotlin
// DrugRepository
@Query("SELECT d FROM Drug d JOIN FETCH d.medKit m JOIN FETCH m.users WHERE d.id = :id")
fun findByIdWithMedKit(id: UUID): Drug?
```

### 3.3 Validation Issues

⚠️ **ISSUE**: Some validations are missing

**UsingService - Recently Fixed:**
```kotlin
// ✅ NOW FIXED - Added validation
fun createTreatmentPlan(...) {
    if (createDTO.plannedAmount <= 0) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Planned amount must be positive")
    }
    // ...
}
```

**DrugService - Good:**
```kotlin
// ✅ Good validation
fun consumeDrug(drugId: UUID, quantity: Double, userId: UUID): Drug {
    if (quantity <= 0) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be positive")
    }
    // ...
}
```

### 3.4 Business Logic Issues

**MedKitService.delete with transfer:**
```kotlin
fun delete(medKitId: UUID, userId: UUID, transferToMedKitId: UUID?) {
    // ...
    transferToMedKitId?.let { targetId ->
        targetMedKit.drugs.addAll(medKit.drugs)  // ❌ POTENTIAL ISSUE
        medKit.drugs.forEach { drug ->
            drug.medKit = targetMedKit  // ✅ This is correct
        }
    }
}
```

⚠️ **Issue**: Adding to collection AND changing FK is redundant. JPA will manage collection based on FK change.

**Recommended:**
```kotlin
medKit.drugs.forEach { drug ->
    drug.medKit = targetMedKit  // Only this is needed
}
```

---

## 4. Repository Layer Analysis

### 4.1 Query Efficiency

✅ **GOOD**: Most queries use JOIN FETCH

```kotlin
@Query("SELECT u FROM User u JOIN FETCH u.medKits WHERE u.id = :id")
fun findByIdWithMedKits(id: UUID): User?
```

✅ **GOOD**: PostgreSQL native query for fuzzy search

```kotlin
@Query(
    value = """
        SELECT * FROM parsed_drugs 
        WHERE similarity(name, :name) > 0.3 
        ORDER BY similarity(name, :name) DESC 
        LIMIT :limit
    """,
    nativeQuery = true
)
fun findByNameFuzzy(name: String, limit: Int): List<VidalDrug>
```

### 4.2 Index Configuration

✅ **GOOD**: Appropriate indexes defined

```kotlin
@Table(
    name = "user_drugs",
    indexes = [
        Index(name = "ix_user_drugs_name", columnList = "name"),
        Index(name = "ix_user_drugs_med_kit_id", columnList = "med_kit_id")
    ]
)
```

**Missing Indexes:**
- ⚠️ Consider adding index on `users.hashed_key` for authentication lookup (already present ✅)
- ⚠️ Consider compound index on `(med_kit_id, name)` for filtered searches

---

## 5. Controller Layer Analysis

### 5.1 Swagger Documentation

✅ **STARTED**: DrugController has Swagger annotations
❌ **INCOMPLETE**: Other controllers lack Swagger documentation

```kotlin
@Tag(name = "Drug Management", description = "Endpoints for managing drugs")
@RestController
@RequestMapping("/drugs")
class DrugController {
    
    @Operation(summary = "Get drug by ID", description = "Returns drug with calculated planned quantity")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Drug found"),
        ApiResponse(responseCode = "403", description = "User doesn't have access"),
        ApiResponse(responseCode = "404", description = "Drug not found")
    ])
    @GetMapping("/{drugId}")
    fun getDrug(...) { ... }
}
```

**TODO**: Add Swagger annotations to:
- MedBoxController (MedKitController)
- UserController
- TreatmentPlanController

### 5.2 Validation

✅ **GOOD**: Request DTOs use Bean Validation

```kotlin
data class DrugCreateDTO(
    @field:NotNull
    @field:Size(min = 1, max = 300)
    val name: String,
    
    @field:NotNull
    @field:DecimalMin("0.0")
    val quantity: Double,
    // ...
)
```

### 5.3 Exception Handling

✅ **GOOD**: Using ResponseStatusException with proper HTTP status codes

```kotlin
throw ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found")
throw ResponseStatusException(HttpStatus.FORBIDDEN, "User doesn't have access")
throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid quantity")
```

⚠️ **Missing**: Global exception handler (@ControllerAdvice) for consistent error responses

---

## 6. Security Analysis

### 6.1 Privacy-by-Design ✅

**Excellent implementation:**

1. **No personal data stored**: Only hashed keys, no names/emails/phones
2. **User identification**: Auto-generated UUID keys
3. **Console-only logging**: No persistent logs (privacy requirement)

```kotlin
class User(
    val id: UUID = UUID.randomUUID(),
    var hashedKey: String,  // Only identifier
    // No name, email, phone, or other PII
)
```

### 6.2 Authentication

✅ **GOOD**: JWT-based authentication with RSA keys

```properties
jwt.public-key-location=classpath:certs/public.pem
jwt.private-key-location=classpath:certs/private.pem
```

### 6.3 Authorization

✅ **GOOD**: Service layer checks user access to resources

```kotlin
if (!drug.medKit.users.any { it.id == userId }) {
    throw ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have access")
}
```

⚠️ **ISSUE**: Authorization logic is duplicated across services. Consider extracting to helper methods.

---

## 7. Test Coverage Analysis

### 7.1 Test Statistics

**Total Tests**: 94  
**Pass Rate**: 100% ✅  
**Test Code**: 1,597 lines

**Breakdown:**
- DrugServiceTestComprehensive: 30 tests ✅
- MedKitServiceTest: 37 tests ✅
- UsingServiceTest: 23 tests ✅
- Integration Tests: 3 tests ✅
- Application Tests: 1 test ✅

### 7.2 Coverage by Layer

| Layer | Coverage | Status |
|-------|----------|--------|
| **Services** | Excellent (≥5 tests per method) | ✅ |
| **Repositories** | Limited (only integration tests) | ⚠️ |
| **Controllers** | None | ❌ |
| **Integration** | Basic | ⚠️ |

### 7.3 Test Quality

✅ **GOOD**: Comprehensive edge case coverage in service tests
```kotlin
@Test
fun `consumeDrug - exactly all quantity - sets to zero`()

@Test
fun `consumeDrug - exceeds available - throws exception`()

@Test
fun `consumeDrug - negative quantity - throws exception`()
```

✅ **GOOD**: Using H2 in-memory database for realistic testing

✅ **GOOD**: Test data builders for maintainable tests

```kotlin
fun drugCreateDTOBuilder() = DrugCreateDTOBuilder()
fun userBuilder() = UserBuilder()
```

---

## 8. Configuration & Deployment

### 8.1 Docker Configuration

✅ **GOOD**: Multi-stage Docker build

```dockerfile
FROM gradle:8.5-jdk21 AS build
# Build stage

FROM eclipse-temurin:21-jre
# Runtime stage
```

✅ **GOOD**: Docker Compose with PostgreSQL

### 8.2 Application Properties

✅ **GOOD**: Environment-specific configurations

```properties
# application.properties - production
spring.datasource.url=jdbc:postgresql://localhost:5432/medapp

# application-test.properties - testing
spring.datasource.url=jdbc:h2:mem:testdb
```

⚠️ **ISSUE**: Logging configuration should explicitly disable file logging

**Recommended:**
```properties
# Ensure no file logging for privacy
logging.file.name=
logging.file.path=
```

---

## 9. Common Problems by Component Type

### 9.1 Entity Classes

**Common Problems:**
1. ❌ Overuse of `var` instead of `val`
2. ❌ ID fields being mutable
3. ⚠️ Collection fields being `var` instead of `val`
4. ⚠️ Missing documentation on why client-side fields aren't persisted

**Edge Cases:**
- Circular references in bidirectional relationships (handled correctly)
- Lazy loading issues (handled with JOIN FETCH)
- Cascade delete chains (configured correctly)

### 9.2 Service Classes

**Common Problems:**
1. ⚠️ Duplicated authorization logic
2. ⚠️ Manual collection management instead of letting JPA handle it
3. ✅ Transaction boundaries (handled correctly)

**Edge Cases Found and Tested:**
- Division by zero (not applicable)
- Empty collections (tested ✅)
- Negative quantities (tested ✅)
- Exceeding available quantity (tested ✅)
- Unauthorized access (tested ✅)
- Non-existent resources (tested ✅)

### 9.3 Repository Classes

**Common Problems:**
1. ✅ N+1 queries (prevented with JOIN FETCH)
2. ✅ Unnecessary JPQL (using standard methods where possible)
3. ✅ Missing indexes (added)

**Edge Cases:**
- Empty result sets (handled ✅)
- Null returns vs exceptions (handled correctly)
- PostgreSQL-specific functions (pg_trgm for fuzzy search ✅)

### 9.4 Controller Classes

**Common Problems:**
1. ❌ Missing comprehensive tests
2. ⚠️ Incomplete Swagger documentation
3. ⚠️ Missing global exception handler
4. ✅ Validation (using Bean Validation correctly)

**Edge Cases:**
- Invalid UUIDs (Spring handles ✅)
- Missing request body (Spring validates ✅)
- Invalid JSON (Spring handles ✅)

---

## 10. Edge Cases Analysis

### 10.1 Concurrency Issues

⚠️ **POTENTIAL PROBLEM**: Shared medkit updates

**Scenario**: Two users simultaneously update the same drug quantity

```kotlin
// User A reads drug.quantity = 100
// User B reads drug.quantity = 100
// User A consumes 50 -> saves quantity = 50
// User B consumes 30 -> saves quantity = 70  // ❌ Wrong! Should be 20
```

**Solution**: Use optimistic locking

```kotlin
@Entity
class Drug {
    @Version  // Add this
    var version: Long = 0
    // ...
}
```

### 10.2 Treatment Plan Conflicts

✅ **HANDLED**: Code checks available quantity before creating treatment plans

```kotlin
val currentPlanned = drug.usings.sumOf { it.plannedAmount }
val availableQuantity = drug.quantity - currentPlanned
if (createDTO.plannedAmount > availableQuantity) {
    throw ResponseStatusException(...)
}
```

### 10.3 MedKit Sharing Edge Cases

✅ **HANDLED**: Deleting medkit with multiple users

```kotlin
if (medKit.users.size > 1) {
    user.removeMedKit(medKit)  // Only remove current user
} else {
    medKitRepository.delete(medKit)  // Delete if last user
}
```

### 10.4 UUID Edge Cases

⚠️ **POTENTIAL ISSUE**: UUID collision (extremely unlikely but possible)

**Risk**: `UUID.randomUUID()` uses UUID v4 (random)
**Probability**: 1 in 2^122 (negligible)
**Mitigation**: Not needed for this application scale

---

## 11. Performance Concerns

### 11.1 Query Performance

✅ **GOOD**: Using JPQL JOIN FETCH prevents N+1
✅ **GOOD**: Indexes on foreign keys
✅ **GOOD**: Native PostgreSQL query for fuzzy search

### 11.2 Memory Usage

⚠️ **CONCERN**: Loading entire collections

```kotlin
@OneToMany(mappedBy = "drug", fetch = FetchType.LAZY)
val usings: MutableSet<Using> = mutableSetOf()
```

**For large datasets**, consider pagination:
```kotlin
@Query("SELECT u FROM Using u WHERE u.drug.id = :drugId")
fun findByDrugId(drugId: UUID, pageable: Pageable): Page<Using>
```

### 11.3 Connection Pool

⚠️ **MISSING**: No explicit HikariCP configuration

**Recommended addition:**
```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

---

## 12. Code Quality Metrics

### 12.1 Complexity

- **Cyclomatic Complexity**: Low-Medium (most methods < 10)
- **Method Length**: Good (most methods < 30 lines)
- **Class Size**: Reasonable (largest service ~160 lines)

### 12.2 Code Smells

1. **Duplicated Code**: Authorization checks duplicated ⚠️
2. **Long Parameter Lists**: Some DTOs could use builder pattern ⚠️
3. **Magic Numbers**: None found ✅
4. **God Classes**: None ✅

### 12.3 Best Practices Violations

1. **var in entities**: 22 instances ❌
2. **Missing @ControllerAdvice**: 1 instance ⚠️
3. **Missing @Version for optimistic locking**: 4 entities ⚠️
4. **Missing test coverage**: Controllers ❌

---

## 13. Recommendations by Priority

### 🔴 HIGH PRIORITY (Fix Immediately)

1. **Fix `var` usage in entities**
   - Change ID fields to `val`
   - Change immutable fields to `val`
   - Keep collection references as `val`
   - Estimated effort: 2 hours
   - Impact: Code quality, maintainability

2. **Add optimistic locking to entities**
   ```kotlin
   @Version
   var version: Long = 0
   ```
   - Prevents concurrent update issues
   - Estimated effort: 30 minutes
   - Impact: Data consistency

3. **Add controller tests**
   - Minimum 10 tests per endpoint
   - Estimated effort: 4-6 hours
   - Impact: Test coverage, confidence

### 🟡 MEDIUM PRIORITY (Fix Soon)

4. **Add global exception handler**
   ```kotlin
   @ControllerAdvice
   class GlobalExceptionHandler {
       @ExceptionHandler(ResponseStatusException::class)
       fun handleResponseStatus(ex: ResponseStatusException): ResponseEntity<ErrorResponse>
   }
   ```
   - Estimated effort: 1 hour
   - Impact: API consistency

5. **Complete Swagger documentation**
   - Add to all controllers
   - Add examples to DTOs
   - Estimated effort: 2 hours
   - Impact: API documentation

6. **Extract authorization helper**
   ```kotlin
   object AuthorizationHelper {
       fun checkDrugAccess(drug: Drug, userId: UUID)
       fun checkMedKitAccess(medKit: MedKit, userId: UUID)
   }
   ```
   - Estimated effort: 1 hour
   - Impact: Code reusability

### 🟢 LOW PRIORITY (Nice to Have)

7. **Add repository tests**
   - Test custom queries
   - Test fuzzy search
   - Estimated effort: 2 hours

8. **Add connection pool configuration**
   - Configure HikariCP
   - Estimated effort: 15 minutes

9. **Add API versioning**
   - `/api/v1/drugs`
   - Estimated effort: 1 hour

---

## 14. Security Checklist

- [x] No SQL injection (using JPA/JPQL)
- [x] No hardcoded secrets
- [x] JWT authentication
- [x] Authorization checks
- [x] Input validation
- [x] No personal data logging
- [ ] Rate limiting (not implemented)
- [ ] CORS configuration (check application.yml)
- [x] HTTPS ready (depends on deployment)

---

## 15. Conclusion

### Summary

The MedApp codebase demonstrates **good architectural decisions** with proper separation of concerns, privacy-by-design implementation, and comprehensive service-layer testing. However, there are several **critical issues** that need attention, particularly the inappropriate use of `var` in Hibernate entities, which goes against both Kotlin and JPA best practices.

### Key Achievements

1. ✅ **Privacy-by-design**: Properly implemented, no PII stored
2. ✅ **Test coverage**: Excellent service layer coverage (94 tests, 100% pass rate)
3. ✅ **N+1 prevention**: Proper use of JOIN FETCH
4. ✅ **Transaction management**: Correctly applied
5. ✅ **Bidirectional relationships**: Fixed with helper methods

### Critical Issues to Fix

1. ❌ **var abuse in entities**: 22 fields using var incorrectly
2. ❌ **Missing controller tests**: 0 controller tests
3. ⚠️ **Missing optimistic locking**: Potential concurrent update issues
4. ⚠️ **Incomplete Swagger**: Only DrugController documented

### Final Grade: B+

**Breakdown:**
- Architecture & Design: A-
- Code Quality: B (due to var issues)
- Test Coverage: B+ (services excellent, controllers missing)
- Security: A
- Documentation: C+
- Performance: A-

**Recommendation**: Address the critical issues (var usage, controller tests, optimistic locking) to achieve an A grade. The foundation is solid and the architecture is sound.

---

## Appendix A: Kotlin/JPA Best Practices

### Entity Class Guidelines

```kotlin
// ✅ GOOD Example
@Entity
class GoodEntity(
    val id: UUID = UUID.randomUUID(),          // val - never changes
    val name: String,                          // val - immutable
    var status: String,                        // var - changes
    val collection: MutableSet<Other> = mutableSetOf()  // val reference, mutable content
) {
    @Version
    var version: Long = 0  // Always add for entities that can be updated concurrently
}

// ❌ BAD Example (current code)
@Entity
class BadEntity(
    var id: UUID = UUID.randomUUID(),          // ❌ var
    var name: String,                          // ❌ var (if immutable)
    var status: String,                        // ✅ var (if mutable)
    var collection: MutableSet<Other> = mutableSetOf()  // ❌ var reference
)
```

### Why val for Collections?

```kotlin
// ✅ CORRECT
val items: MutableSet<Item> = mutableSetOf()
items.add(newItem)  // ✅ Modify contents

// ❌ WRONG
var items: MutableSet<Item> = mutableSetOf()
items = mutableSetOf(newItem)  // ❌ Replace entire collection
```

With `var`, you could accidentally replace the entire collection, breaking Hibernate's change tracking.

---

## Appendix B: Test Coverage Matrix

| Component | Method | Tests | Status |
|-----------|--------|-------|--------|
| **DrugService** | | | |
| | findById | 5 | ✅ |
| | findByIdForUser | 5 | ✅ |
| | create | 5 | ✅ |
| | update | 5 | ✅ |
| | consumeDrug | 5 | ✅ |
| | getPlannedQuantity | 5 | ✅ |
| **MedKitService** | | | |
| | createNew | 5 | ✅ |
| | findById | 5 | ✅ |
| | findByIdForUser | 5 | ✅ |
| | findAllByUser | 5 | ✅ |
| | addUserToMedKit | 5 | ✅ |
| | removeUserFromMedKit | 5 | ✅ |
| | delete | 7 | ✅ |
| **UsingService** | | | |
| | createTreatmentPlan | 5 | ✅ |
| | updateTreatmentPlan | 5 | ✅ |
| | recordIntake | 5 | ✅ |
| | deleteTreatmentPlan | 4 | ✅ |
| | Query methods | 4 | ✅ |
| **Controllers** | | | |
| | DrugController | 0 | ❌ |
| | MedKitController | 0 | ❌ |
| | UserController | 0 | ❌ |
| | TreatmentPlanController | 0 | ❌ |
| **Repositories** | | | |
| | Custom queries | 0 | ❌ |
| | Fuzzy search | 0 | ❌ |

---

**END OF REPORT**
