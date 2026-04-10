# Transactions API

## Quick Start
 
Runs on `http://localhost:8080`
 
### With Docker
 
```bash
docker-compose up
```
 
---
 
## Design
 
- Although the exercise mentions that an in-memory database could be used, I preferred to have persistence to make it as close as possible to a production environment, opting for **DynamoDB**.
  - A **GSI (Global Secondary Index)** was added to retrieve all transactions given a type, necessary for the `GET /transactions/types/{type}` endpoint.
- An in-memory map to represent a graph structure was considered but discarded in favor of a solution using a persistence layer.
- Thinking about a production environment with high throughput, I decided not to perform the recursive sum operation synchronously when processing the `/sum` request. Instead, I went with an **async solution** (implemented using `@Async` for the sake of this exercise). In a production environment it would be worth using **CDC and a serverless process** to recalculate the necessary data (i.e. the sum amount in this exercise).

---
 
## Implementation
 
The majority of the code was generated leveraging **Claude Code**. My primary background is in **Go**, not Java, so Claude Code was used as a force multiplier to navigate Spring Boot idioms, project structure, and Java-specific patterns once I defined the architecture decisions, data modeling, and overall design.

---
 
## Endpoints
 
### `POST /transactions`
Creates a transaction.
 
#### Request Parameters
 
| Parameter   | Required | Description                        |
|-------------|----------|------------------------------------|
| `amount`    | ✅        | Transaction amount                 |
| `type`      | ✅        | Category/type of the transaction   |
| `parent_id` | ❌        | Links the transaction to a parent  |
 
#### Request Example
 
```json
{
  "amount": 20.00,
  "type": "cars"
}
```
 
#### Notes
 
1. `POST` was used instead of `PUT` since it better reflects the semantics of the operation.
2. The transaction ID is generated internally using UUID and not received as path parameter.
 
#### Response Example
 
```json
{
  "transaction_id": "9f362e46..."
}
```
 
---
 
### `GET /transactions/types/{type}`
Returns all transaction IDs for a given type.
 
#### Response Example
 
```json
[
  "08e6ce8f...",
  "9f362e46...",
  "13378424...",
  "7fc6ebf3..."
]
```
 
---
 
### `GET /transactions/sum/{id}`
Returns the precalculated sum amount from a given node to its leaves.
 
#### Response Example
 
```json
{
  "sum": 120.00
}
```
<img width="974" height="797" alt="image" src="https://github.com/user-attachments/assets/5b685de8-006a-41f0-b890-71dcdbecdcbb" />



