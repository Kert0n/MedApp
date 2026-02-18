# MedApp REST API Documentation

## Overview
This document describes the REST API endpoints for the MedApp medicine organizer server.

All endpoints require JWT authentication. Include the JWT token in the `Authorization` header:
```
Authorization: Bearer <your-jwt-token>
```

---

## Base URL
```
http://your-server:port
```

---

## Endpoints

### 🔐 Authentication
See `AuthController` for authentication endpoints (implemented separately).

---

### 💊 Drug Management (`/drug`)

#### Get All Drugs
```http
GET /drug
```
Returns all drugs accessible to the authenticated user.

**Response:** `List<DrugDTO>`

---

#### Get Drug by ID
```http
GET /drug/{id}
```
Get details of a specific drug.

**Parameters:**
- `id` (path) - Drug UUID

**Response:** `DrugDTO`

---

#### Get Drug State (Lightweight)
```http
GET /drug/light/{id}
```
Get only quantity and planned quantity of a drug.

**Parameters:**
- `id` (path) - Drug UUID

**Response:**
```json
{
  "quantity": 100.0,
  "plannedQuantity": 30.0
}
```

---

#### Add Drugs
```http
POST /drug
```
Add new drugs to a medicine kit.

**Request Body:** `Set<DrugPostDTO>`
```json
[
  {
    "name": "Aspirin",
    "quantity": 100,
    "quantityUnit": "tablets",
    "formType": "tablet",
    "category": "Pain relief",
    "manufacturer": "Bayer",
    "country": "Germany",
    "description": "Pain reliever",
    "owner": "medkit-uuid"
  }
]
```

**Response:** `List<DrugDTO>`

---

#### Consume Drug
```http
PUT /drug/{id}/consume?quantity={amount}
```
Reduce drug quantity (single consumption).

**Parameters:**
- `id` (path) - Drug UUID
- `quantity` (query) - Amount to consume

**Response:**
```json
{
  "remainingQuantity": 95.0
}
```

---

#### Move Drug to Another Kit
```http
PUT /drug/{id}/move?targetMedKitId={kitId}
```
Move a drug to another medicine kit. Treatment plans are preserved.

**Parameters:**
- `id` (path) - Drug UUID
- `targetMedKitId` (query) - Target medicine kit UUID

**Response:** `DrugDTO`

---

#### Delete Drug
```http
DELETE /drug/{id}
```
Permanently delete a drug and all associated treatment plans.

**Parameters:**
- `id` (path) - Drug UUID

**Response:** `204 No Content`

---

### 📋 Treatment Plans (`/drug/plan`)

#### Create Treatment Plan
```http
POST /drug/plan?drugId={drugId}&plannedAmount={amount}
```
Create a new treatment plan (course) for a drug.

**Parameters:**
- `drugId` (query) - Drug UUID
- `plannedAmount` (query) - Planned amount to reserve

**Response:**
```json
{
  "message": "Treatment plan created successfully"
}
```

---

#### Update Treatment Plan
```http
PUT /drug/plan/{drugId}?newPlannedAmount={amount}
```
Update the planned amount for an existing treatment plan.

**Parameters:**
- `drugId` (path) - Drug UUID
- `newPlannedAmount` (query) - New planned amount

**Response:**
```json
{
  "message": "Treatment plan updated successfully"
}
```

---

#### Record Planned Intake
```http
PUT /drug/plan/{drugId}/intake?consumedAmount={amount}
```
Record a planned drug intake from a treatment plan.

**Parameters:**
- `drugId` (path) - Drug UUID
- `consumedAmount` (query) - Amount consumed

**Response:**
```json
{
  "remainingQuantity": 90.0
}
```

---

#### Delete Treatment Plan
```http
DELETE /drug/plan/{drugId}
```
Delete a treatment plan for a drug.

**Parameters:**
- `drugId` (path) - Drug UUID

**Response:** `204 No Content`

---

### 🔍 Drug Catalog Search (`/drug/template`)

#### Fuzzy Search Drug Templates
```http
GET /drug/template?searchTerm={term}
```
Search for drugs in the catalog using fuzzy matching (PostgreSQL trigrams).

**Parameters:**
- `searchTerm` (query) - Search text

**Response:**
```json
[
  {
    "id": "uuid",
    "name": "Aspirin",
    "formType": "Tablet",
    "manufacturer": "Bayer"
  }
]
```

---

#### Get Drug Template by ID
```http
GET /drug/template/{id}
```
Get complete information about a drug template.

**Parameters:**
- `id` (path) - Template UUID

**Response:** `VidalDrug` (full template data)

---

### 🏥 Medicine Kit Management (`/med-kit`)

#### Get All Medicine Kits
```http
GET /med-kit
```
Get all medicine kits accessible to the user.

**Response:** `List<MedKitDTO>`

---

#### Get Medicine Kit by ID
```http
GET /med-kit/{id}
```
Get details of a specific medicine kit.

**Parameters:**
- `id` (path) - Medicine kit UUID

**Response:** `MedKitDTO`

---

#### Get Drugs in Medicine Kit
```http
GET /med-kit/{id}/drugs
```
Get all drugs in a specific medicine kit.

**Parameters:**
- `id` (path) - Medicine kit UUID

**Response:** `List<DrugDTO>`

---

#### Create Medicine Kit
```http
POST /med-kit
```
Create a new medicine kit.

**Response:** `MedKitDTO`

---

#### Join Shared Medicine Kit
```http
POST /med-kit/{id}/join
```
Join a shared medicine kit using its ID (from QR code or text code).

**Parameters:**
- `id` (path) - Medicine kit UUID to join

**Response:**
```json
{
  "message": "Successfully joined medicine kit"
}
```

---

#### Delete Medicine Kit
```http
DELETE /med-kit/{id}?targetMedKitId={targetId}
```
Delete a medicine kit. Optionally move drugs to another kit.

**Parameters:**
- `id` (path) - Medicine kit UUID to delete
- `targetMedKitId` (query, optional) - Target kit UUID for drug migration

**Response:** `204 No Content`

**Note:** If no target kit is specified, all drugs will be permanently deleted.

---

### 👤 User Data (`/user`)

#### Get All User Data
```http
GET /user
```
Get complete user data including all medicine kits and drugs.

**Response:** `UserDto`
```json
{
  "id": "user-uuid",
  "medKits": [
    {
      "drugs": [...]
    }
  ]
}
```

---

#### Get All Treatment Plans
```http
GET /user/plans
```
Get all treatment plans for the user.

**Response:**
```json
{
  "plans": [
    {
      "drugId": "uuid",
      "drugName": "Aspirin",
      "plannedAmount": 30.0,
      "lastUsed": "2024-01-01T12:00:00Z",
      "createdAt": "2024-01-01T10:00:00Z"
    }
  ]
}
```

---

## Data Transfer Objects (DTOs)

### DrugDTO
```json
{
  "id": "uuid",
  "name": "string",
  "quantity": 100.0,
  "plannedQuantity": 30.0,
  "quantityUnit": "string",
  "formType": "string",
  "category": "string",
  "manufacturer": "string",
  "country": "string",
  "description": "string",
  "medKit": "medkit-uuid"
}
```

### MedKitDTO
```json
{
  "drugs": [
    // Array of DrugDTO objects
  ]
}
```

### UserDto
```json
{
  "id": "uuid",
  "medKits": [
    // Array of MedKitDTO objects
  ]
}
```

---

## Error Responses

All endpoints return standard HTTP status codes:

- `200 OK` - Request successful
- `201 Created` - Resource created successfully
- `204 No Content` - Resource deleted successfully
- `400 Bad Request` - Invalid request parameters
- `401 Unauthorized` - Authentication required
- `403 Forbidden` - Access denied
- `404 Not Found` - Resource not found
- `409 Conflict` - Resource already exists

Error response body:
```json
{
  "timestamp": "2024-01-01T12:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Error description",
  "path": "/drug/123"
}
```

---

## Features

### Conflict Resolution
When drug quantity becomes less than total planned quantity across all users:
- System automatically reduces all planned amounts proportionally
- Users are notified of changes (implement notification mechanism separately)

### Shared Medicine Kits
- Multiple users can access the same medicine kit
- Treatment plans are user-specific
- Drug consumption affects all users
- Conflict resolution applies across all users

### Fuzzy Search
- Uses PostgreSQL trigram similarity (`pg_trgm` extension)
- Returns results ordered by similarity score
- Threshold: similarity > 0.3

---

## Notes

1. All UUIDs must be valid UUID v4 format
2. Double values for quantities support decimal amounts
3. Treatment plans are tied to specific drugs and users
4. Moving drugs preserves treatment plans
5. Deleting drugs removes all associated treatment plans
6. Security is handled by Spring Security JWT authentication
