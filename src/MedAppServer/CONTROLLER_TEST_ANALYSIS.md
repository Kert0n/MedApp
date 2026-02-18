# Controller Testing Analysis

## Testing Goals

### 1. Normal Usage Simulation (Happy Path)
- Test standard CRUD operations with valid data
- Test authenticated user accessing their own resources
- Test data retrieval with correct authorization
- Test successful creation/update/deletion flows
- Test pagination and filtering where applicable

### 2. Edge Cases
- **Boundary Values**: Zero quantities, maximum values, empty strings
- **Empty Results**: GET requests returning empty lists
- **Null Values**: Optional parameters not provided
- **Concurrent Access**: Multiple users accessing shared resources
- **Data Consistency**: CASCADE operations, relationship integrity
- **Resource Limits**: Maximum string lengths, precision limits

### 3. Typical Bugs in REST Controllers

#### Authorization Bugs
- Missing authorization checks (unauthorized access)
- Incorrect authorization (accessing other users' resources)
- Missing authentication on protected endpoints
- Authorization bypass through parameter manipulation

#### Validation Bugs
- Missing validation on required fields
- Accepting invalid data types
- Not enforcing business rules (e.g., negative quantities)
- SQL injection through unvalidated inputs
- Path traversal through ID manipulation

#### State Management Bugs
- Race conditions in concurrent operations
- Stale data after updates
- Orphaned records after deletions
- Incorrect CASCADE behavior
- Transaction rollback failures

#### Data Integrity Bugs
- N+1 query problems
- Missing foreign key checks
- Violating unique constraints
- Incorrect data transformations (DTO mapping)
- Loss of precision in numeric fields

#### Error Handling Bugs
- Returning 500 instead of appropriate HTTP codes
- Exposing internal errors to clients
- Not handling exceptions properly
- Missing error messages
- Inconsistent error response formats

#### Business Logic Bugs
- Incorrect quantity calculations
- Wrong permission checks on shared resources
- Invalid state transitions
- Missing conflict resolution
- Incorrect date/time handling

## Test Structure

### Per Endpoint Test Suite (Minimum 5 Tests)

1. **Success Test** - Happy path with valid data
2. **Authorization Test** - Unauthorized/forbidden access
3. **Validation Test** - Invalid input data
4. **Not Found Test** - Resource doesn't exist
5. **Business Logic Test** - Edge case or special condition

### Additional Tests (Where Applicable)

6. **Conflict Test** - Resource conflicts (e.g., duplicate, concurrent update)
7. **Cascade Test** - Verify cascade operations work correctly
8. **Permission Test** - Shared resource access control
9. **Boundary Test** - Min/max values, empty data
10. **Integration Test** - Multiple operations in sequence

## Test Data Strategy

### Using Existing Test Builders
- `UserBuilder` - Create test users with unique hashedKeys
- `MedKitBuilder` - Create test medicine kits
- `DrugBuilder` - Create test drugs
- `UsingBuilder` - Create test treatment plans
- `FormTypeBuilder`, `QuantityUnitBuilder` - Create reference data

### Test Isolation
- Each test gets fresh data (rollback after test)
- Use unique UUIDs for each test resource
- Don't rely on database state from other tests
- Clean up relationships properly

## Controller-Specific Test Scenarios

### DrugController
- **Authorization**: User can only access drugs in their medkits
- **Validation**: Name, quantity, expiry date format
- **Business Logic**: Quantity cannot go negative, treatment plan reservations
- **Edge Cases**: Moving drug between medkits, consuming more than available

### MedKitController
- **Authorization**: User can only access their medkits or shared ones
- **Validation**: Valid user IDs when sharing
- **Business Logic**: Last user leaving deletes medkit, drug transfer on delete
- **Edge Cases**: Sharing with same user twice, circular sharing

### TreatmentPlanController
- **Authorization**: User can only manage their own treatment plans
- **Validation**: Planned amount must be positive, drug must exist
- **Business Logic**: Cannot exceed available drug quantity
- **Edge Cases**: Recording intake without plan, negative quantities

### UserController
- **Authorization**: User can only see their own data
- **Business Logic**: Returns all medkits (owned and shared)
- **Privacy**: No PII exposure

### AuthController
- **Validation**: Correct registration secret required
- **Business Logic**: Generates unique credentials
- **Security**: Token generation and expiration
- **Edge Cases**: Invalid credentials, expired tokens

## Expected Test Count

| Controller | Endpoints | Min Tests | Expected Total |
|------------|-----------|-----------|----------------|
| DrugController | 9 | 45 | 50-60 |
| MedKitController | 6 | 30 | 35-40 |
| TreatmentPlanController | 6 | 30 | 35-40 |
| UserController | 1 | 5 | 8-10 |
| AuthController | 2 | 10 | 12-15 |
| **TOTAL** | **24** | **120** | **140-165** |

## Test File Organization

```
src/test/kotlin/org/kert0n/medappserver/controller/
├── drug/
│   ├── GetDrugByIdTest.kt
│   ├── CreateDrugTest.kt
│   ├── UpdateDrugTest.kt
│   ├── DeleteDrugTest.kt
│   ├── GetDrugQuantityTest.kt
│   ├── ConsumeDrugTest.kt
│   ├── MoveDrugTest.kt
│   ├── SearchDrugTemplatesTest.kt
│   └── GetDrugTemplateTest.kt
├── medkit/
│   ├── CreateMedKitTest.kt
│   ├── GetMedKitByIdTest.kt
│   ├── GetAllMedKitsTest.kt
│   ├── ShareMedKitTest.kt
│   ├── LeaveMedKitTest.kt
│   └── DeleteMedKitTest.kt
├── treatmentplan/
│   ├── GetAllTreatmentPlansTest.kt
│   ├── GetTreatmentPlanForDrugTest.kt
│   ├── CreateTreatmentPlanTest.kt
│   ├── UpdateTreatmentPlanTest.kt
│   ├── RecordIntakeTest.kt
│   └── DeleteTreatmentPlanTest.kt
├── user/
│   └── GetUserDataTest.kt
└── auth/
    ├── RegisterTest.kt
    └── LoginTest.kt
```

## Testing Framework

- **Spring Boot Test**: `@WebMvcTest` for controller layer testing
- **MockMvc**: For HTTP request simulation
- **Mockito**: For service layer mocking
- **H2**: In-memory database for integration tests
- **JUnit 5**: Test runner and assertions
- **AssertJ**: Fluent assertions

## HTTP Status Codes to Verify

- **200 OK**: Successful GET/PUT requests
- **201 CREATED**: Successful POST requests
- **204 NO CONTENT**: Successful DELETE requests
- **400 BAD REQUEST**: Validation errors
- **401 UNAUTHORIZED**: Missing authentication
- **403 FORBIDDEN**: Insufficient permissions
- **404 NOT FOUND**: Resource doesn't exist
- **409 CONFLICT**: Resource conflict
- **500 INTERNAL SERVER ERROR**: Should not happen in tests
