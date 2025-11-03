# Qliina Management System  
Laundry Business Management Platform  

## Overview

Qliina Management System is an enterprise-grade laundry business management platform built with Spring Boot. It provides end-to-end capabilities for managing laundry operations, customers, employees, inventory, payments, reporting, and compliance within a modular, multi-tenant architecture.

---

## Architecture

The application follows a modular structure with clear separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                     REST API Layer                          │
│  Controllers (Order, Customer, Employee, Inventory, etc.)  │
├─────────────────────────────────────────────────────────────┤
│                     Service Layer                           │
│  Business Logic, Validation, Orchestration                  │
├─────────────────────────────────────────────────────────────┤
│                     Repository Layer                        │
│  JPA Data Access, Specifications, Custom Queries            │
├─────────────────────────────────────────────────────────────┤
│                     Model Layer                             │
│  Entities, DTOs, Enums                                      │
├─────────────────────────────────────────────────────────────┤
│                  Database (PostgreSQL)                      │
└─────────────────────────────────────────────────────────────┘
```

---

## Features

### Core Modules

| Module | Description |
|--------|-------------|
| Order Management | Complete order lifecycle from receipt to delivery |
| Customer Management | Customer profiles, loyalty programs, RFM segmentation |
| Employee Management | Shifts, attendance, performance tracking |
| Inventory Management | Stock control, suppliers, purchase orders |
| Payment Processing | Multiple payment methods, refunds, cash drawer management |
| Quality Control | Checklists, defect tracking, employee scorecards |
| Reporting & Analytics | Revenue reports, P&L statements, aging reports |
| Audit & Compliance | Comprehensive audit logging, data retention policies |
| Notification System | Email, SMS, push notifications with templates |

---

## Technology Stack

- Java 21  
- Spring Boot 4.x  
- Spring Security with JWT  
- Spring Data JPA  
- PostgreSQL  
- Lombok  
- MapStruct  
- Swagger / OpenAPI  
- Maven  

---

## Project Structure

```
├── audit/               # Audit logging and compliance
├── common/              # Shared utilities and base classes
├── customer/            # Customer management
├── employee/            # Employee and shift management
├── identity/            # Authentication, authorization, users
├── inventory/           # Inventory and supplier management
├── notification/        # Email, SMS, push notifications
├── order/               # Order processing
├── payment/             # Payment processing
├── quality/             # Quality control
└── reporting/           # Reports and analytics
```

---

## Installation and Setup

### Prerequisites

- Java 21 or higher  
- PostgreSQL 13 or higher  
- Maven 3.8+  
- Git  

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/qliina-management.git
cd qliina-management
```

### 2. Create PostgreSQL Database

```sql
CREATE DATABASE qliina_db;
CREATE USER qliina_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE qliina_db TO qliina_user;
```

### 3. Configure Application Properties

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

Edit `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/qliina_db
    username: qliina_user
    password: your_password

jwt:
  secret: your_jwt_secret_key_at_least_32_chars
```

### 4. Build the Application

```bash
mvn clean package
```

### 5. Run the Application

```bash
java -jar target/qliina-management-*.jar
# or
mvn spring-boot:run
```

### 6. Access the Application

- API: http://localhost:8080  
- Swagger UI: http://localhost:8080/swagger-ui.html  
- API Docs: http://localhost:8080/api-docs  

---

## Authentication and Authorization

The system uses JWT-based authentication with role-based access control.

### Default Credentials

After first run:

- Username: admin  
- Password: Admin@123  

### Permission Structure

- Global permissions – platform-wide access  
- Business permissions – business-level access  
- Shop permissions – shop-level access  

---

## Functional Modules

### Order Management

- Create, update, and track orders  
- Status workflow:  
  `RECEIVED → WASHING → IRONING → QUALITY_CHECK → READY_FOR_PICKUP → COMPLETED`  
- Item-level tracking  
- Delivery management  
- Notes and attachments  

### Customer Management

- Customer profiles with multiple addresses  
- Loyalty tiers: BRONZE, SILVER, GOLD  
- RFM segmentation (Recency, Frequency, Monetary)  
- Notes and preferences  

### Employee Management

- Shift scheduling  
- Clock in/out and break tracking  
- Performance metrics  
- Attendance reports  

### Inventory Management

- Stock tracking across shops  
- Low stock alerts  
- Supplier management  
- Purchase orders  
- Stock transfers  

### Payment Processing

- Payment methods: CASH, CARD, MOBILE  
- Split payments  
- Refunds  
- Cash drawer management  
- Corporate accounts and invoicing  

### Quality Control

- Quality checklists  
- Defect tracking  
- Employee quality scorecards  
- Rework rate analysis  

### Reporting

- Revenue reports (daily, weekly, monthly, custom)  
- Profit and Loss statements  
- Accounts receivable aging  
- Tax reports  
- Employee performance reports  
- Export to CSV, Excel, PDF  

---

## Testing

```bash
# Unit tests
mvn test

# Integration tests
mvn verify

# Test coverage
mvn clean test jacoco:report
```

---

## Performance Optimization

- HikariCP connection pooling  
- Pagination for list endpoints  
- Indexed database queries  
- Caching where applicable  
- Asynchronous notification processing  

---

## Security Features

- JWT-based authentication  
- Argon2 password encoding  
- Two-factor authentication support  
- Rate limiting  
- Audit logging for critical operations  
- Encryption for sensitive data  

---

## API Documentation

Swagger UI is available at:

```
/swagger-ui.html
```

Accessible when the application is running.

---

## Deployment

### Docker

```bash
docker build -t qliina-management .
docker run -p 8080:8080 qliina-management
```

### Docker Compose

```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/qliina_db
      - SPRING_DATASOURCE_USERNAME=qliina_user
      - SPRING_DATASOURCE_PASSWORD=your_password
    depends_on:
      - db

  db:
    image: postgres:13
    environment:
      - POSTGRES_DB=qliina_db
      - POSTGRES_USER=qliina_user
      - POSTGRES_PASSWORD=your_password
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

---

## Development Guidelines

### Code Style

- Follow Google Java Style Guide  
- Use Lombok to reduce boilerplate  
- Write clear Javadoc  
- Keep methods focused and cohesive  

### Branch Strategy

- `main` – production-ready  
- `develop` – integration branch  
- `feature/*` – new features  
- `bugfix/*` – bug fixes  
- `release/*` – release preparation  

### Commit Convention

```
type(scope): description

[optional body]

[optional footer]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

---

## Contributing

1. Fork the repository  
2. Create a feature branch  
3. Commit changes  
4. Push to your branch  
5. Open a Pull Request  

---

## License

Proprietary software owned by Jjenus. All rights reserved.

---

## Current Status

The application is under active development.

### Pending Fixes

- Fix import statements in multiple files  
- Add missing repository methods  
- Resolve builder pattern issues in DTOs  
- Complete notification service implementations  
- Add missing EncryptionService dependencies