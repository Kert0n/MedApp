# Comprehensive Testing Goals Analysis

## Executive Summary

This document provides a comprehensive analysis of testing goals for the MedApp REST API server. It covers:
1. **Normal Usage Simulation** - Common workflows and happy paths
2. **Edge Cases** - Boundary conditions and unusual scenarios  
3. **Typical Bugs** - Common pitfalls in REST APIs, JPA/Hibernate, Spring applications

## 1. Normal Usage Simulation

### 1.1 Drug Management Workflow
**Typical User Journey:**
1. User creates a medkit
2. User adds drugs to medkit
3. User searches for drugs
4. User updates drug quantity
5. User consumes drug (quantity decreases)
6. User moves drug between medkits
7. User deletes drug

**Test Scenarios:**
- Create drug with all required fields
- Update drug quantity (increase/decrease)
- Search drugs by name
- Filter drugs by medkit
- Consume drug successfully
- Move drug to another medkit
- Delete drug successfully

### 1.2 Shared Medkit Workflow
**Typical User Journey:**
1. User A creates medkit
2. User A generates share code
3. User B joins medkit via code
4. Both users can see and modify drugs
5. User creates treatment plan (reserves drugs)
6. Other user sees reserved quantities
7. User leaves shared medkit

**Test Scenarios:**
- Generate share code
- Join medkit via share code
- Multiple users access same medkit
- Create treatment plan in shared medkit
- Reserved quantity calculations
- Leave shared medkit
- Last user deletion cascades

### 1.3 Treatment Plan Workflow
**Typical User Journey:**
1. User creates treatment plan for a drug
2. System reserves planned quantity
3. User receives intake reminder
4. User records intake
5. Planned quantity decreases, drug quantity decreases
6. Plan completes when all intakes recorded

**Test Scenarios:**
- Create treatment plan
- Update treatment plan
- Record intake (happy path)
- Plan auto-adjusts if drug quantity changes
- Delete treatment plan
- Get upcoming intakes

### 1.4 Search and Discovery Workflow
**Typical User Journey:**
1. User types partial drug name
2. System shows fuzzy search results from Vidal database
3. User selects template
4. System pre-fills drug fields

**Test Scenarios:**
- Fuzzy search with partial name
- Fuzzy search returns relevant results
- Select drug template
- Template pre-fills correctly

## 2. Edge Cases Analysis

### 2.1 Boundary Value Edge Cases

#### Quantity Boundaries
- **Zero quantity**: Drug with 0 quantity
- **Negative quantity**: Attempt to create/update with negative value
- **Very large quantity**: MAX_DOUBLE value
- **Decimal precision**: 0.001, 0.1, very small decimals
- **Exact consumption**: Consume exact quantity (should become 0)
- **Over-consumption**: Attempt to consume more than available

#### Date/Time Boundaries
- **Past dates**: Expiry date in the past
- **Far future**: Expiry date 100 years in future
- **Null dates**: Optional date fields as null
- **Today**: Expiry date is today (edge of expired/not expired)

#### String Boundaries
- **Empty string**: Name = ""
- **Very long string**: Name with 1000+ characters
- **Special characters**: Unicode, emojis, SQL injection attempts
- **Null vs empty**: Distinguish between null and ""
- **Whitespace only**: "   " as name

#### Collection Boundaries
- **Empty medkit**: Medkit with no drugs
- **Empty user list**: Medkit with no users (after all leave)
- **Single element**: Medkit with exactly 1 drug, 1 user
- **Many elements**: Medkit with 1000+ drugs

### 2.2 Authorization Edge Cases

#### User Access
- **Non-existent user**: UUID that doesn't exist in database
- **Wrong user**: User tries to access another user's resource
- **Shared resource**: User accesses shared medkit (authorized)
- **Removed access**: User removed from shared medkit tries to access
- **Expired token**: JWT token past expiration
- **Invalid token**: Malformed JWT token
- **No token**: Request without Authorization header

#### Resource Ownership
- **Drug belongs to different medkit**: User tries to access drug from unauthorized medkit
- **Medkit doesn't belong to user**: User tries to modify unauthorized medkit
- **Treatment plan for other user's drug**: Cross-user access attempt

### 2.3 Concurrency Edge Cases

#### Optimistic Locking
- **Concurrent updates**: Two users update same drug simultaneously
- **Stale version**: Update with old version number
- **Version mismatch**: OptimisticLockException handling

#### Race Conditions
- **Simultaneous consumption**: Two users consume same drug at same time
- **Reserve vs consume**: One user reserves drug while another consumes it
- **Delete while reading**: Drug deleted while being read
- **Share code expiry**: Join attempt exactly at expiry time

### 2.4 Cascade Delete Edge Cases

#### Drug Deletion
- **With treatment plans**: Delete drug that has active treatment plans
- **Cascade to usings**: Using entities should be deleted
- **Orphan check**: No orphaned usings remain

#### Medkit Deletion
- **With drugs, no transfer**: All drugs deleted
- **With drugs, with transfer**: All drugs moved to target medkit
- **Last user leaves**: Medkit auto-deleted
- **Multiple users**: Medkit persists when one user leaves

#### User Deletion
- **User with treatment plans**: Plans deleted
- **User in shared medkit**: Removed from all medkits
- **Last user in medkit**: Medkit deleted

### 2.5 Data Consistency Edge Cases

#### Bidirectional Relationships
- **User-MedKit sync**: Both sides updated correctly
- **Drug-MedKit reference**: FK updated when moving
- **Using-Drug relationship**: Composite key consistency

#### Reserved Quantity
- **Sum of all usings**: Reserved = sum(plannedAmount) for all users
- **Create plan**: Reserved increases
- **Delete plan**: Reserved decreases
- **Record intake**: Reserved decreases, quantity decreases
- **Multiple plans**: Correct summation across all users

### 2.6 Validation Edge Cases

#### DTO Validation
- **Missing required fields**: Null name, form, quantity
- **Invalid data types**: String instead of number
- **Constraint violations**: Negative quantity, empty name
- **Validation bypass**: Update without validation

#### Business Logic Validation
- **Exceeds available**: Plan requires more than available quantity
- **Plan already exists**: Duplicate plan for same user+drug
- **Invalid state transitions**: Update non-existent resource

## 3. Typical Bugs by Component Type

### 3.1 REST Controller Bugs

#### Common Controller Bugs
1. **Missing Authorization Check**
   - Endpoint accessible without authentication
   - Authorization check skipped for specific endpoint
   - **Test**: Call endpoint without/with wrong user token

2. **Incorrect HTTP Status Codes**
   - Returns 200 instead of 201 for creation
   - Returns 200 instead of 404 for not found
   - Returns 500 instead of 400 for validation errors
   - **Test**: Verify exact status codes for each scenario

3. **Missing Validation**
   - @Valid annotation missing on @RequestBody
   - Validation errors not returned to client
   - **Test**: Send invalid DTOs, expect 400 with errors

4. **Path Variable Errors**
   - Wrong parameter name (@PathVariable("id") vs actual path {drugId})
   - Missing @PathVariable annotation
   - Type mismatch (String instead of UUID)
   - **Test**: Call with invalid path variables

5. **Response Body Issues**
   - Null response when object exists
   - Missing response data
   - Sensitive data leaked in response
   - **Test**: Verify response structure and content

6. **Exception Handling**
   - Unhandled exceptions return 500
   - Wrong exception type thrown
   - Exception leaks implementation details
   - **Test**: Trigger exceptions, verify proper handling

### 3.2 Service Layer Bugs

#### Common Service Bugs
1. **N+1 Query Problem**
   - Missing JOIN FETCH in queries
   - Lazy loading triggers in loop
   - **Test**: Enable query logging, count queries

2. **Transaction Boundaries**
   - Missing @Transactional
   - Wrong propagation level
   - Read-only when should be writable
   - **Test**: Verify rollback on exceptions

3. **Null Pointer Exceptions**
   - Didn't check Optional.isPresent()
   - Assumed relationship always loaded
   - Null entity passed to method
   - **Test**: Pass null/empty, verify exceptions

4. **Wrong Exception Type**
   - Throws generic Exception instead of specific
   - ResponseStatusException with wrong status
   - **Test**: Verify exception types and status codes

5. **Authorization Logic**
   - Missing authorization check
   - Wrong authorization logic (OR instead of AND)
   - Bypass via method call ordering
   - **Test**: Call with unauthorized user

6. **Data Consistency**
   - Bidirectional relationship not synced
   - Cached data not invalidated
   - Stale reference returned
   - **Test**: Verify both sides of relationships

### 3.3 Repository Layer Bugs

#### Common Repository Bugs
1. **Missing JOIN FETCH**
   - Returns entities with lazy collections
   - N+1 queries when accessing relationships
   - **Test**: Verify single query with joins

2. **Wrong Query Logic**
   - AND instead of OR in WHERE clause
   - Wrong join type (INNER vs LEFT)
   - Missing WHERE conditions
   - **Test**: Verify returned data matches criteria

3. **Pagination Errors**
   - Wrong page calculation (0-indexed vs 1-indexed)
   - Missing total count
   - Incorrect sorting
   - **Test**: Request specific pages, verify results

4. **Native Query Issues**
   - SQL syntax errors
   - PostgreSQL-specific syntax not portable
   - Parameter binding errors
   - **Test**: Execute queries with various parameters

5. **Fuzzy Search Problems**
   - Trigram index not used
   - Search too slow
   - Relevance sorting incorrect
   - **Test**: Search with partial matches

### 3.4 JPA/Hibernate Bugs

#### Common JPA Bugs
1. **LazyInitializationException**
   - Accessing lazy collection outside transaction
   - Entity detached from session
   - **Test**: Access relationships after method return

2. **Optimistic Locking**
   - Missing @Version field
   - Concurrent updates not detected
   - **Test**: Concurrent modification scenarios

3. **Cascade Configuration**
   - Wrong cascade type (ALL instead of specific)
   - Missing orphanRemoval
   - Unexpected deletion
   - **Test**: Delete parent, verify children

4. **Entity State Issues**
   - Transient entity passed to persist()
   - Detached entity not merged
   - **Test**: Entity lifecycle scenarios

5. **Bidirectional Relationship**
   - Only one side updated
   - Inconsistent state in memory
   - **Test**: Verify both sides after operations

### 3.5 Security Bugs

#### Common Security Bugs
1. **Broken Access Control**
   - User can access other user's data
   - Missing ownership checks
   - **Test**: Cross-user access attempts

2. **Authentication Bypass**
   - Endpoint not protected
   - Token validation skipped
   - **Test**: Call without authentication

3. **Information Disclosure**
   - Error messages reveal internal details
   - Stack traces in responses
   - **Test**: Trigger errors, check response content

4. **Mass Assignment**
   - Update DTO allows changing ID
   - User can escalate privileges
   - **Test**: Send unexpected fields in DTO

## 4. Test Matrix by Endpoint

### 4.1 DrugController Tests (80+ tests)

#### GET /api/drugs (getAllForUser)
1. Empty result - user has no drugs
2. Single drug - returns correctly
3. Multiple drugs - all returned
4. Pagination - page 1, 2, last page
5. Sorting - by name, quantity, expiry
6. Filtering - by medkit
7. Unauthorized user - 403
8. Non-existent user - 403/404
9. Large dataset - performance
10. SQL injection attempt - safe

#### GET /api/drugs/{id} (getById)
1. Existing drug - returns correctly
2. Non-existent drug - 404
3. Drug in authorized medkit - 200
4. Drug in unauthorized medkit - 403
5. Invalid UUID format - 400
6. Null ID - 400
7. Drug with relationships loaded
8. Deleted drug - 404
9. Optimistic locking version included
10. Response structure validation

#### POST /api/drugs (create)
1. Valid drug - created successfully (201)
2. Missing required fields - 400
3. Invalid medkit - 404
4. Unauthorized medkit - 403
5. Negative quantity - 400
6. Zero quantity - allowed or not
7. Very long name - validation
8. Special characters in name
9. Duplicate drug - allowed
10. Creation timestamps set

#### PUT /api/drugs/{id} (update)
1. Valid update - 200
2. Non-existent drug - 404
3. Unauthorized drug - 403
4. Missing required fields - 400
5. Negative quantity - 400
6. Optimistic lock conflict - 409
7. Version incremented
8. Update timestamps changed
9. Partial update (only some fields)
10. No changes - idempotent

#### POST /api/drugs/{id}/consume (consumeDrug)
1. Valid consumption - quantity decreases
2. Exact quantity - becomes 0
3. Over consumption - 400
4. Negative quantity - 400
5. Zero consumption - 400 or ignored
6. Unauthorized drug - 403
7. Non-existent drug - 404
8. Concurrent consumption
9. After consumption - usings adjusted
10. Reserved quantity check

#### POST /api/drugs/{id}/move (moveDrug)
1. Valid move - drug changes medkit
2. Source medkit unauthorized - 403
3. Target medkit unauthorized - 403
4. Target medkit non-existent - 404
5. Same medkit - no-op or error
6. Move with active usings - allowed
7. Reserved quantity maintained
8. Relationships updated
9. Transaction rollback on error
10. Multiple drugs move

#### GET /api/drugs/search (searchTemplates)
1. Fuzzy match - returns similar
2. Exact match - top result
3. No matches - empty list
4. Partial match - relevant results
5. Special characters - handled
6. Empty query - validation
7. Very long query - handled
8. Trigram search used
9. Limit results - pagination
10. Performance - < 1s

#### DELETE /api/drugs/{id} (delete)
1. Valid deletion - 204
2. Non-existent drug - 404
3. Unauthorized drug - 403
4. With active usings - cascade delete
5. With reservations - allowed
6. Optimistic lock check
7. Medkit drugs list updated
8. Orphan usings removed
9. Cannot undelete
10. Transaction rollback on error

### 4.2 MedKitController Tests (60+ tests)

#### GET /api/medkits (getAllForUser)
1. Empty list - new user
2. Single medkit - returns correctly
3. Multiple medkits - all returned
4. Includes shared medkits
5. Excludes unauthorized medkits
6. Pagination support
7. Sorting options
8. Unauthorized user - 403
9. Performance test
10. Users count loaded

#### GET /api/medkits/{id} (getById)
1. Existing medkit - 200
2. Non-existent medkit - 404
3. Authorized user - 200
4. Unauthorized user - 403
5. Shared medkit - both users can access
6. Invalid UUID - 400
7. Drugs loaded
8. Users loaded
9. Correct drug count
10. Response structure

#### POST /api/medkits (create)
1. Valid medkit - 201
2. Missing name - 400
3. Empty name - 400
4. Very long name - validation
5. User auto-added
6. Bidirectional sync
7. Special characters in name
8. Multiple medkits per user
9. Creation timestamp
10. Returns created medkit

#### PUT /api/medkits/{id} (update)
1. Valid update - 200
2. Non-existent medkit - 404
3. Unauthorized user - 403
4. Empty name - 400
5. Update location field
6. Partial update
7. Bidirectional relationships maintained
8. Update timestamp changed
9. No changes - idempotent
10. Concurrent update handling

#### DELETE /api/medkits/{id} (delete)
1. Delete without transfer - drugs deleted
2. Delete with transfer - drugs moved
3. Last user - medkit deleted
4. Shared medkit - user removed only
5. Non-existent medkit - 404
6. Unauthorized user - 403
7. Transfer to non-existent medkit - 404
8. Transfer to unauthorized medkit - 403
9. Cascade to drugs and usings
10. Transaction rollback on error

#### POST /api/medkits/{id}/share (generateShareCode)
1. Valid generation - returns code
2. Unauthorized user - 403
3. Non-existent medkit - 404
4. Code format validation
5. Code expiry (15 min)
6. Multiple codes - new replaces old
7. QR code option
8. Text code option
9. Code stored in database
10. Privacy warning shown

### 4.3 UserController Tests (10+ tests)

#### GET /api/users/me (getCurrentUser)
1. Authenticated user - returns data
2. No authentication - 401
3. Invalid token - 401
4. Expired token - 401
5. User data complete
6. No PII exposed
7. Security key not revealed
8. Medkits count included
9. Drugs count included
10. Treatment plans count included

### 4.4 TreatmentPlanController Tests (50+ tests)

#### GET /api/treatment-plans (getAllForUser)
1. Empty list - no plans
2. Single plan - returned
3. Multiple plans - all returned
4. Only user's plans (not others)
5. Pagination
6. Sorting by start date
7. Filter by drug
8. Filter by date range
9. Unauthorized user - 403
10. Performance test

#### POST /api/treatment-plans (create)
1. Valid plan - 201
2. Missing required fields - 400
3. Negative planned amount - 400
4. Exceeds available quantity - 400
5. Drug not accessible - 403
6. Plan already exists - 409
7. Reserved quantity updated
8. Timestamps set
9. Composite key created
10. Drug relationship loaded

#### PUT /api/treatment-plans/{userId}/{drugId} (update)
1. Valid update - 200
2. Non-existent plan - 404
3. Unauthorized user - 403
4. Negative amount - 400
5. Exceeds available - 400
6. Reserved quantity recalculated
7. Update timestamp changed
8. Partial update
9. Concurrent update handling
10. No changes - idempotent

#### POST /api/treatment-plans/{userId}/{drugId}/intake (recordIntake)
1. Valid intake - 200
2. Quantity decreases
3. Planned amount decreases
4. Exact amount - becomes 0
5. Over planned amount - 400
6. Negative consumption - 400
7. Zero consumption - 400
8. Plan completed - auto-delete
9. Non-existent plan - 404
10. Unauthorized user - 403

#### DELETE /api/treatment-plans/{userId}/{drugId} (delete)
1. Valid deletion - 204
2. Non-existent plan - 404
3. Unauthorized user - 403
4. Reserved quantity decreased
5. Drug quantity unchanged
6. Cannot undelete
7. Cascade from drug deletion
8. Composite key handling
9. Transaction rollback on error
10. Multiple plans deletion

### 4.5 Repository Tests (20+ tests)

#### DrugRepository
1. findByIdWithMedKit - single query
2. findByMedKitId - all drugs returned
3. findByMedKitIdAndNameContaining - search works
4. Complex query performance
5. JOIN FETCH prevents N+1

#### MedKitRepository
1. findByIdWithUsers - loads users
2. findByIdWithUsersAndDrugs - loads both
3. findByUsersContaining - shared medkits
4. Performance with many relationships

#### UserRepository
1. findByIdWithMedKits - loads medkits
2. findBySecurityKey - unique lookup
3. Security key uniqueness

#### UsingRepository
1. findAllByUserId - filters correctly
2. findAllByDrugId - filters correctly
3. findByUserIdAndDrugId - composite key lookup
4. sumPlannedAmount - aggregation works

#### VidalDrugRepository
1. searchByName - fuzzy search works
2. Trigram index used
3. Relevance ranking
4. Performance with large dataset
5. Special characters handled

## 5. Test Execution Strategy

### 5.1 Test Order
1. **Unit Tests First**: Services (fastest, no Spring context)
2. **Integration Tests**: Repositories with H2
3. **Controller Tests**: MockMvc with security
4. **End-to-End Tests**: Full scenarios

### 5.2 Performance Expectations
- Service tests: < 100ms each
- Repository tests: < 500ms each
- Controller tests: < 1s each
- Integration tests: < 5s each

### 5.3 Test Data Strategy
- Use builders for consistency
- Realistic data (actual drug names, dates)
- Avoid magic numbers
- Clear test data setup

### 5.4 Assertion Strategy
- Verify status codes
- Verify response bodies
- Verify database state
- Verify relationships
- Verify exception types

## 6. Success Criteria

### Coverage Goals
- **Line Coverage**: > 80%
- **Branch Coverage**: > 70%
- **Method Coverage**: > 90%
- **Class Coverage**: > 95%

### Quality Goals
- **All tests pass**: 100%
- **No flaky tests**: Consistent results
- **Fast execution**: < 5 minutes total
- **Clear failures**: Meaningful error messages

### Documentation Goals
- Each test method has clear name
- Complex scenarios have comments
- Test data builders documented
- Edge cases explained

## Conclusion

This comprehensive testing strategy ensures:
1. **Normal usage** works correctly
2. **Edge cases** are handled properly
3. **Typical bugs** are prevented
4. **Security** is maintained
5. **Performance** is acceptable
6. **Data consistency** is guaranteed

Target: **~310 total tests** with **100% pass rate** covering all layers of the application.
