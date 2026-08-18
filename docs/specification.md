# CINEBOOK — MASTER SOFTWARE ENGINEERING PROMPT

You are the principal engineer responsible for designing and implementing **CINEBOOK**, a production-style full-stack movie ticket booking platform.

You are acting simultaneously as:

* Senior Software Architect
* Java 21 / Spring Boot Engineer
* Database Architect
* Backend Engineer
* Frontend Engineer
* UI/UX Designer
* QA Engineer
* DevOps Engineer
* Security Engineer
* Low-Level Design specialist

Your objective is to build a **complete, working, production-quality modular monolith**, not a CRUD demo.

---

# 1. CORE OBJECTIVE

Build:

**CINEBOOK**

Tagline:

**"Your Seat. Your Movie. Your Moment."**

CineBook is a modern online movie-ticket booking platform inspired by the capabilities of commercial platforms, but with:

* original branding
* original UI
* original architecture
* original database design
* strong LLD
* SOLID principles
* appropriate design patterns
* transactional consistency
* concurrency protection
* extensibility
* production-quality code
* polished UX

The application must work end-to-end.

Do NOT create fake screens.

Do NOT hardcode booking states.

Do NOT hardcode seat availability.

Do NOT use fake booking responses.

Do NOT expose JPA entities directly.

Do NOT put business logic in controllers.

Do NOT implement only superficial CRUD.

Every core feature must connect:

**Frontend → API → Business Logic → Database**

---

# 2. DEVELOPMENT PHILOSOPHY

Follow this priority order:

1. Correctness
2. Architectural integrity
3. Security
4. Data consistency
5. Testability
6. Maintainability
7. UX
8. Visual polish
9. Performance optimization

Do not sacrifice correctness for visual polish.

Do not sacrifice maintainability for cleverness.

Do not introduce complexity merely to demonstrate a design pattern.

Use the simplest production-quality solution.

---

# 3. ARCHITECTURAL STYLE

Build a:

**Modular Monolith**

Do NOT introduce microservices unless there is a compelling technical requirement.

Primary architecture:

```text
React + TypeScript
        ↓
REST API
        ↓
Spring Boot Modular Monolith
        ↓
PostgreSQL
```

Optional infrastructure:

```text
Redis
Payment Provider
Email Provider
SMS Provider
WebSocket
```

The architecture must allow future extraction of modules into services if required, but the current implementation remains a modular monolith.

---

# 4. TECH STACK

## Backend

* Java 21
* Spring Boot 3.x
* Spring Web
* Spring Data JPA
* Spring Security
* Jakarta Validation
* Spring Transaction Management
* Spring Scheduling
* WebSocket/STOMP where useful
* Flyway
* OpenAPI / Swagger
* Lombok only where it improves readability

## Database

* PostgreSQL

## Cache / Temporary State

Redis may be used for:

* caching
* temporary seat locks
* frequently accessed data

However:

**PostgreSQL remains authoritative for confirmed bookings.**

## Frontend

* React
* TypeScript
* Vite
* Tailwind CSS
* React Router
* TanStack Query
* Axios or Fetch

## Testing

* JUnit 5
* Mockito
* Spring Boot Test
* Testcontainers where useful
* Integration tests
* Concurrency tests

## DevOps

* Docker
* Docker Compose
* Environment variables

---

# 5. VISUAL DESIGN DIRECTION

The visual identity must combine:

## MINIMALISM

Use:

* clean layouts
* generous whitespace
* restrained typography
* clear hierarchy
* simple navigation
* focused CTAs
* uncluttered information architecture
* consistent spacing
* limited visual noise

## MAXIMALISM

Use maximalism selectively for:

* hero sections
* movie artwork
* cinematic gradients
* premium movie cards
* featured content
* major CTA areas
* ticket confirmation
* important visual moments
* admin analytics

The result should feel:

**"Cinematic maximalism inside a minimalist product system."**

Do NOT make every component visually loud.

Use visual intensity strategically.

---

# 6. VISUAL LANGUAGE

Preferred aesthetic:

* dark cinematic interface
* near-black backgrounds
* rich layered gradients
* large movie artwork
* subtle glassmorphism
* premium typography
* elegant cards
* soft borders
* restrained shadows
* cinematic lighting effects
* subtle motion
* polished micro-interactions

Avoid:

* excessive neon
* excessive blur
* excessive animations
* giant rounded cards everywhere
* rainbow gradients
* generic Bootstrap appearance
* generic dashboard templates
* excessive glassmorphism
* unnecessary 3D effects
* visual clutter

The application must look like a **premium entertainment product**, not a college project.

---

# 7. UX PRINCIPLE

Every page should answer:

1. Where am I?
2. What can I do?
3. What is the primary action?
4. What information matters?
5. What happens next?

Use strong visual hierarchy.

Use progressive disclosure.

Do not overwhelm users with unnecessary information.

---

# 8. USER ROLES

Implement:

* USER
* ADMIN
* THEATRE_MANAGER

Backend authorization is mandatory.

Never trust role information from the frontend.

---

# 9. CORE USER FLOW

The complete user journey must work:

```text
Register
↓
Login
↓
Browse Movies
↓
Search / Filter
↓
Movie Details
↓
Select City
↓
Select Date
↓
Select Theatre
↓
Select Showtime
↓
View Real Seat Map
↓
Select Seats
↓
Lock Seats
↓
Checkout
↓
Apply Coupon
↓
Calculate Price
↓
Payment
↓
Payment Verification
↓
Booking Confirmation
↓
Generate Digital Ticket
↓
Generate QR
↓
Booking History
↓
Booking Details
↓
Cancellation
↓
Refund
```

---

# 10. ADMIN FLOW

Admin must be able to:

* manage movies
* manage genres
* manage languages
* manage theatres
* manage locations
* manage screens
* configure seats
* create shows
* configure show pricing
* manage bookings
* manage users
* manage coupons
* moderate reviews
* inspect payments
* inspect refunds
* view analytics
* view revenue
* view occupancy

All admin functionality must use real backend data.

---

# 11. DOMAIN MODEL

Main entities:

```text
User
Movie
Genre
Language
Actor
MovieCast
Theatre
Location
Screen
Seat
Show
ShowSeat
Booking
BookingSeat
Payment
Refund
Coupon
Review
Notification
AuditLog
```

Core abstraction:

```text
BookableResource
    ├── Theatre
    ├── Screen
    ├── Seat
    └── Show
```

The design must allow additional resource/booking types to be introduced without rewriting the complete booking engine.

---

# 12. USER DOMAIN

User:

```text
id
name
email
passwordHash
phone
role
status
createdAt
updatedAt
```

UserRole:

```text
USER
ADMIN
THEATRE_MANAGER
```

AccountStatus:

```text
ACTIVE
BLOCKED
DELETED
```

---

# 13. MOVIE DOMAIN

Movie:

```text
id
title
description
duration
releaseDate
posterUrl
backdropUrl
trailerUrl
rating
certification
status
createdAt
updatedAt
```

MovieStatus:

```text
COMING_SOON
NOW_SHOWING
ENDED
```

Certification:

```text
U
UA
A
CUSTOM
```

Support:

* genres
* languages
* cast
* crew

---

# 14. THEATRE DOMAIN

Theatre:

```text
id
name
description
location
status
```

Location:

```text
id
address
city
state
postalCode
latitude
longitude
```

A theatre may contain multiple screens.

---

# 15. SCREEN DOMAIN

Screen:

```text
id
theatreId
name
screenType
capacity
status
```

ScreenType:

```text
STANDARD
IMAX
IMAX_3D
FOUR_DX
```

---

# 16. SEAT DOMAIN

Seat:

```text
id
screenId
rowLabel
seatNumber
seatType
```

SeatType:

```text
REGULAR
PREMIUM
RECLINER
```

IMPORTANT:

A physical `Seat` must NOT contain booking availability.

Seat availability belongs to a particular show.

Therefore implement:

```text
ShowSeat
```

---

# 17. SHOW DOMAIN

Show:

```text
id
movieId
screenId
startTime
endTime
language
format
basePrice
status
createdAt
```

ShowFormat:

```text
TWO_D
THREE_D
IMAX
IMAX_3D
```

ShowStatus:

```text
SCHEDULED
STARTED
COMPLETED
CANCELLED
```

---

# 18. SHOW SEAT

ShowSeat represents the state of one physical seat for one show.

Fields:

```text
id
showId
seatId
price
status
lockedBy
lockExpiry
version
```

SeatStatus:

```text
AVAILABLE
LOCKED
BOOKED
```

Use optimistic locking and/or database row-level locking where appropriate.

---

# 19. TIME SLOT

Create a proper `TimeSlot` value object.

Fields:

```text
startTime
endTime
```

Provide:

```text
overlaps(TimeSlot other)
```

Do not use strings for time calculations.

---

# 20. BOOKING DOMAIN

Booking:

```text
id
bookingReference
userId
showId
ticketAmount
convenienceFee
discount
totalAmount
status
createdAt
updatedAt
expiresAt
```

BookingStatus:

```text
INITIATED
SEATS_LOCKED
PAYMENT_PENDING
CONFIRMED
CANCELLED
EXPIRED
REFUND_PENDING
REFUNDED
COMPLETED
```

BookingSeat:

```text
id
bookingId
showSeatId
price
```

---

# 21. BOOKING STATE MACHINE

Valid lifecycle:

```text
INITIATED
    ↓
SEATS_LOCKED
    ↓
PAYMENT_PENDING
    ↓
CONFIRMED
    ↓
COMPLETED
```

Failure:

```text
SEATS_LOCKED
    ↓
EXPIRED
```

Cancellation:

```text
CONFIRMED
    ↓
CANCELLED
    ↓
REFUND_PENDING
    ↓
REFUNDED
```

Invalid transitions must be rejected.

For example:

```text
CANCELLED → CONFIRMED
```

must never be allowed.

Implement using a State Pattern or a dedicated state-transition domain service.

---

# 22. SEAT LOCKING

Seat locking is a critical system requirement.

When a user selects seats:

```text
AVAILABLE
    ↓
LOCKED
```

Default lock duration:

**5 minutes**

Backend responsibilities:

* verify current availability
* atomically lock seats
* assign lock expiry
* create booking
* return booking information
* enforce expiry server-side

Frontend countdown is only a UX feature.

The backend remains authoritative.

Implement scheduled cleanup for expired locks.

---

# 23. CONCURRENCY

This is one of the most important requirements.

Scenario:

```text
User A → A10
User B → A10
```

Requests arrive concurrently.

Expected:

```text
One succeeds.
One receives HTTP 409 Conflict.
```

Correctness must work across multiple application instances.

Do NOT rely on:

```java
synchronized
```

or in-memory locks.

Use a combination of:

* database transactions
* row-level locking and/or optimistic locking
* unique constraints
* atomic updates
* appropriate isolation
* application validation

Create mandatory automated concurrent-booking tests.

---

# 24. BOOKING STRATEGY

Create:

```text
BookingStrategy
```

Responsibilities may include:

```text
validateBooking()
checkAvailability()
createBooking()
```

Provide the movie-seat booking implementation.

Design for future booking/resource types.

---

# 25. PRICING

Create:

```text
PricingStrategy
```

Examples:

```text
StandardPricingStrategy
PremiumSeatPricingStrategy
WeekendPricingStrategy
IMAXPricingStrategy
ThreeDPricingStrategy
```

Pricing should be composable where appropriate.

Example:

```text
Base = ₹150
Premium = +₹50
IMAX = +₹100
Weekend = +₹50
```

Final price MUST be calculated by the backend.

Never trust frontend prices.

---

# 26. DISCOUNTS

Create:

```text
DiscountStrategy
```

Implement where appropriate:

```text
PercentageDiscountStrategy
FlatDiscountStrategy
FirstBookingDiscountStrategy
MovieSpecificDiscountStrategy
```

Coupon:

```text
code
discountType
discountValue
maxDiscount
minBookingAmount
validFrom
validUntil
usageLimit
perUserLimit
active
```

All coupon rules must be validated server-side.

---

# 27. CANCELLATION

Create:

```text
CancellationPolicy
```

Default example:

```text
> 24 hours       → 100%
12–24 hours      → 75%
< 12 hours       → 25%
after start      → 0%
```

Keep policy configurable.

Do not place cancellation rules inside controllers.

---

# 28. PAYMENT

Create:

```text
PaymentGateway
```

Methods:

```text
initiatePayment()
verifyPayment()
refundPayment()
```

Implement:

```text
MockPaymentGateway
```

The complete application must work locally without external payment credentials.

Optionally support:

```text
RazorpayPaymentGateway
```

PaymentStatus:

```text
INITIATED
PROCESSING
SUCCESS
FAILED
REFUNDED
```

Never store card information.

---

# 29. PAYMENT IDEMPOTENCY

Support:

```text
Idempotency-Key
```

Repeated identical payment requests must return the original payment result.

Do not create duplicate payments.

Create automated idempotency tests.

---

# 30. NOTIFICATIONS

Create:

```text
NotificationSender
```

Implement:

```text
EmailNotificationSender
InAppNotificationSender
SmsNotificationSender
```

Local development may mock/log external notifications.

Trigger notifications for:

* booking confirmed
* payment success
* cancellation
* refund initiated
* refund completed
* show cancelled
* booking expiration

Do not put notification logic inside BookingService.

---

# 31. EVENTS

Create events such as:

```text
BookingCreatedEvent
BookingConfirmedEvent
BookingCancelledEvent
PaymentSuccessfulEvent
RefundCompletedEvent
```

Use Spring application events or another clean event abstraction.

Example:

```text
BookingConfirmedEvent
        ↓
Notification Listener
        ↓
Email / In-App Notification
```

---

# 32. REVIEWS

A user may review a movie only when they have a valid completed booking for that movie.

Review:

```text
id
userId
movieId
bookingId
rating
comment
createdAt
updatedAt
```

Rating:

```text
1–5
```

Prevent duplicate reviews according to business rules.

Admin can moderate reviews.

---

# 33. SEARCH

Movie search must support:

* title
* genre
* language
* certification
* rating
* release status

Provide pagination.

Do not load the complete movie database into memory.

---

# 34. FRONTEND PAGES

Implement:

```text
/
 /home
 /movies
 /movies/:id
 /theatres
 /shows
 /shows/:id/seats
 /checkout
 /booking/:id
 /my-bookings
 /profile
 /login
 /register

 /admin
 /admin/movies
 /admin/theatres
 /admin/screens
 /admin/shows
 /admin/bookings
 /admin/users
 /admin/coupons
 /admin/reviews
 /admin/analytics
```

---

# 35. HOMEPAGE

Sections:

* cinematic hero
* now showing
* coming soon
* trending
* top rated
* popular in selected city
* recommendations

Movie card:

* poster
* title
* rating
* genre
* language
* primary CTA

Hero should be visually maximalist.

Supporting sections should remain minimalist.

---

# 36. MOVIE DETAILS

Display:

* poster
* backdrop
* title
* description
* rating
* duration
* certification
* language
* genres
* cast
* trailer
* reviews

Primary CTA:

**BOOK TICKETS**

---

# 37. CITY

Support city selection.

Example seed cities:

```text
Hyderabad
Bengaluru
Chennai
Mumbai
Delhi
Pune
Kolkata
```

Store selected city in frontend state/local storage.

Backend must filter theatres/shows using city.

---

# 38. SHOW SELECTION

Flow:

```text
Movie
↓
City
↓
Date
↓
Theatre
↓
Screen
↓
Showtime
```

Group showtimes by theatre.

---

# 39. SEAT MAP

Create an attractive responsive cinema seat map.

Example:

```text
                 SCREEN

A1 A2 A3 A4 A5 A6
B1 B2 B3 B4 B5 B6
C1 C2 C3 C4 C5 C6
D1 D2 D3 D4 D5 D6
E1 E2 E3 E4 E5 E6
F1 F2 F3 F4 F5 F6
```

States:

```text
AVAILABLE
SELECTED
BOOKED
LOCKED
PREMIUM
RECLINER
```

Do not rely on color alone.

Provide labels/icons/accessible states.

The seat map should be one of the strongest visual elements in the application.

---

# 40. REAL-TIME AVAILABILITY

Use WebSocket/STOMP where useful.

Clients may receive seat state changes.

However:

**WebSocket is not the source of truth.**

Before final booking:

* revalidate availability
* enforce locking server-side

---

# 41. CHECKOUT

Display:

```text
Movie
Theatre
Screen
Date
Time
Seats
```

Price:

```text
Ticket amount
Convenience fee
Discount
Taxes if applicable
Total
```

Coupon:

```text
[ APPLY ]
```

Payment:

```text
[ PAY NOW ]
```

Backend recalculates final amount.

---

# 42. DIGITAL TICKET

After successful payment:

Generate a digital ticket containing:

```text
CINEBOOK
Movie
Theatre
Screen
Date
Time
Seats
Booking ID
Amount
```

Generate a QR code containing only a secure booking reference.

Never place sensitive payment/personal data inside the QR code.

---

# 43. MY BOOKINGS

Tabs:

```text
Upcoming
Completed
Cancelled
```

Booking card:

* movie poster
* title
* theatre
* date
* time
* seats
* booking reference
* status

Actions:

```text
View Ticket
Download Ticket
Cancel
```

Only show cancellation when eligible.

---

# 44. ADMIN DASHBOARD

Use real backend analytics.

Display:

* total users
* total movies
* active theatres
* today's bookings
* revenue
* occupancy
* cancellations
* refunds

Charts:

* revenue by day
* bookings by day
* popular movies
* theatre occupancy
* show occupancy

Visual style:

**minimal data visualization + maximal visual hierarchy**

Avoid dashboard clutter.

---

# 45. API

Implement REST APIs for:

Authentication:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
GET  /api/auth/me
```

Movies:

```text
GET /api/movies
GET /api/movies/{id}
GET /api/movies/now-showing
GET /api/movies/coming-soon
GET /api/movies/search
```

Theatres:

```text
GET /api/theatres
GET /api/theatres/{id}
```

Shows:

```text
GET /api/shows
GET /api/shows/{id}
GET /api/shows/{id}/seats
```

Seats:

```text
POST   /api/shows/{showId}/seats/lock
DELETE /api/shows/{showId}/seats/lock
```

Bookings:

```text
POST /api/bookings
GET /api/bookings/{id}
GET /api/users/me/bookings
POST /api/bookings/{id}/cancel
```

Payments:

```text
POST /api/payments
GET /api/payments/{id}
POST /api/payments/{id}/refund
```

Reviews:

```text
POST /api/movies/{movieId}/reviews
GET /api/movies/{movieId}/reviews
```

Coupons:

```text
POST /api/coupons/validate
```

Notifications:

```text
GET /api/notifications
PATCH /api/notifications/{id}/read
```

Admin APIs:

```text
/api/admin/movies
/api/admin/theatres
/api/admin/screens
/api/admin/seats
/api/admin/shows
/api/admin/bookings
/api/admin/users
/api/admin/coupons
/api/admin/reviews
/api/admin/analytics
```

---

# 46. ERROR HANDLING

Implement centralized exception handling.

Examples:

```text
ResourceNotFoundException
MovieNotFoundException
ShowNotFoundException
SeatUnavailableException
SeatAlreadyLockedException
BookingNotFoundException
InvalidBookingStateException
PaymentFailedException
CouponExpiredException
UnauthorizedException
ForbiddenException
ConcurrentBookingException
```

Use appropriate HTTP statuses:

```text
400
401
403
404
409
422
500
```

Consistent response:

```json
{
  "timestamp": "...",
  "status": 409,
  "error": "SEAT_UNAVAILABLE",
  "message": "One or more selected seats are no longer available.",
  "path": "/api/shows/101/seats/lock"
}
```

Never expose stack traces.

---

# 47. VALIDATION

Use Jakarta Validation:

```text
@NotBlank
@Email
@Size
@NotNull
@Positive
@Future
@Pattern
```

Validate all incoming DTOs.

---

# 48. DATABASE

Use PostgreSQL + Flyway.

Requirements:

* normalized schema
* primary keys
* foreign keys
* unique constraints
* indexes
* timestamps
* version columns
* appropriate cascading

Important indexes:

```text
users.email
movies.title
shows.movie_id
shows.screen_id
shows.start_time
show_seats.show_id
show_seats.status
bookings.user_id
bookings.booking_reference
payments.booking_id
coupons.code
```

---

# 49. CONSISTENCY

Confirmed seat assignments must never duplicate.

Use multiple protection layers:

```text
Database transaction
+
locking/versioning
+
unique constraints
+
application validation
```

The database is the final authority.

---

# 50. SOLID

Demonstrate SOLID naturally.

Examples:

```text
BookingService
PaymentService
PricingService
NotificationService
SeatLockService
```

Avoid giant services.

Use interfaces where they represent meaningful boundaries.

Depend on abstractions for external systems.

---

# 51. DESIGN PATTERNS

Use only when justified.

Potential patterns:

### Strategy

* pricing
* discount
* cancellation
* booking

### Factory

* strategy selection
* payment gateway selection

### State

* booking lifecycle

### Observer / Event

* notifications

### Adapter

* external payment providers

### Repository

* persistence

### Builder

* complex ticket/booking construction

### Specification

* movie filtering/search

Explain why each pattern exists.

---

# 52. SECURITY

Implement:

* JWT
* password hashing
* role authorization
* CORS
* validation
* secure error handling
* rate limiting where appropriate
* safe logging
* no sensitive logs
* no card storage
* secure booking references

Never trust from frontend:

```text
userId
role
price
booking amount
seat status
```

Always derive/validate these server-side.

---

# 53. FRONTEND COMPONENTS

Create reusable components:

```text
Navbar
Footer
MovieCard
MovieCarousel
MovieHero
SearchBar
FilterPanel
TheatreCard
ShowtimeButton
Seat
SeatMap
BookingSummary
PriceBreakdown
CouponInput
PaymentCard
TicketCard
QRCode
NotificationBell
Modal
Toast
LoadingSkeleton
Pagination
AdminSidebar
AnalyticsCard
Chart
```

Do not duplicate UI logic.

---

# 54. FRONTEND UX

Implement:

* loading states
* skeleton loaders
* empty states
* error states
* toast notifications
* confirmation dialogs
* responsive layouts
* disabled states
* form validation
* safe optimistic updates
* seat-lock countdown
* automatic refresh after lock expiry

---

# 55. ACCESSIBILITY

Implement:

* semantic HTML
* keyboard navigation
* ARIA labels
* accessible seat buttons
* sufficient contrast
* visible focus
* meaningful errors

Do not rely solely on color.

---

# 56. TESTING

Test:

* authentication
* movie search
* movie retrieval
* show creation
* seat availability
* seat locking
* concurrent seat locking
* booking creation
* state transitions
* payment success
* payment failure
* payment idempotency
* coupon validation
* cancellation
* refund
* review eligibility
* admin authorization

---

# 57. MANDATORY CONCURRENCY TEST

Create one show containing seat A10.

Execute two booking operations concurrently.

Expected:

```text
success = 1
failure = 1
```

Database must remain consistent.

This test is mandatory.

---

# 58. LOCK EXPIRATION TEST

Create an expired locked seat.

Run expiration logic.

Verify:

```text
LOCKED → AVAILABLE
```

No stale booking remains.

---

# 59. PAYMENT FAILURE TEST

Flow:

```text
Lock seats
↓
Create booking
↓
Payment fails
↓
Booking expires/cancels
↓
Seats released
```

No permanently locked seats.

---

# 60. CANCELLATION TEST

Confirmed booking:

```text
CONFIRMED
↓
CANCELLED
↓
REFUND_PENDING
↓
REFUNDED
```

Verify all transitions.

---

# 61. PACKAGE STRUCTURE

Use package-by-feature:

```text
com.cinebook

auth
movie
theatre
screen
seat
show
booking
payment
pricing
coupon
review
notification
admin
analytics
common
security
```

Each feature may contain:

```text
controller
service
repository
domain
dto
mapper
```

Avoid a giant global service package.

---

# 62. DOCKER

Create:

```text
docker-compose.yml
```

Services:

```text
cinebook-backend
cinebook-frontend
postgres
redis
```

Use environment variables:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
JWT_SECRET
REDIS_URL
PAYMENT_PROVIDER_CONFIG
```

Never commit secrets.

---

# 63. SEED DATA

Provide development seed data for:

* users
* movies
* genres
* languages
* theatres
* screens
* seats
* shows
* coupons

Use fictional movie data.

Do not make core functionality dependent on external copyrighted APIs.

---

# 64. ADMIN SEED

Provide a development admin account through environment configuration or seed configuration.

Never hardcode a production credential.

Document account setup/reset.

---

# 65. README

Create a professional README containing:

* project overview
* architecture
* features
* technology stack
* prerequisites
* environment variables
* database setup
* backend setup
* frontend setup
* Docker setup
* API documentation
* development data
* testing
* LLD overview
* concurrency architecture
* database design
* future improvements

---

# 66. LLD DOCUMENTATION

Create:

```text
docs/
├── architecture.md
├── lld.md
├── database.md
├── api.md
├── concurrency.md
├── booking-flow.md
└── diagrams/
```

Include:

* class diagrams
* sequence diagrams
* ER diagram
* booking flow
* seat-locking flow
* payment flow
* cancellation flow
* state transition diagram
* design patterns
* SOLID principles
* concurrency strategy
* database design

Use Mermaid diagrams where appropriate.

---

# 67. REQUIRED BOOKING SEQUENCE

Document:

```text
User
↓
Frontend
↓
BookingController
↓
BookingService
↓
SeatLockService
↓
Database
↓
PaymentService
↓
PaymentGateway
↓
BookingService
↓
NotificationService
```

Include both successful and failed paths.

---

# 68. BOOKING FLOW

Implement:

```text
Movie
↓
City
↓
Theatre
↓
Show
↓
Seat availability
↓
Seat selection
↓
Seat lock
↓
Booking
↓
Payment
↓
Payment verification
↓
Booking confirmation
↓
ShowSeat BOOKED
↓
BookingConfirmedEvent
↓
Notification
↓
Ticket
↓
QR
```

---

# 69. FAILURE FLOW

If payment fails:

```text
PAYMENT FAILED
↓
Booking expires/cancels
↓
Seats released
↓
User notified
```

No stale locks.

---

# 70. UI QUALITY BAR

The final UI must NOT resemble:

* basic Bootstrap CRUD
* plain HTML forms
* default Spring UI
* generic admin templates
* unstyled tables
* student-project dashboards

It should resemble a **premium modern entertainment product**.

Use:

* cinematic hero
* large artwork
* horizontal carousels
* premium cards
* elegant seat map
* refined checkout
* clear price breakdown
* polished admin dashboard

Use maximalism strategically.

Use minimalism for structure.

---

# 71. CODE QUALITY

Use:

* meaningful names
* small focused methods
* dependency injection
* immutable DTOs where appropriate
* Java records where appropriate
* no giant classes
* no giant controllers
* no duplicated business logic
* no magic numbers
* configuration for configurable values
* proper logging
* meaningful exceptions

Avoid unnecessary comments.

Do not comment obvious code.

---

# 72. IMPLEMENTATION PHASES

Do NOT attempt to build the entire application in one generation.

Implement in phases:

```text
PHASE 0
Specification analysis + architecture

PHASE 1
Spring Boot foundation

PHASE 2
PostgreSQL + Flyway

PHASE 3
Security + authentication

PHASE 4
Movie module

PHASE 5
Theatre + Screen + Seat

PHASE 6
Show management

PHASE 7
Booking domain

PHASE 8
Seat locking + concurrency

PHASE 9
Pricing + coupons

PHASE 10
Payment abstraction + idempotency

PHASE 11
Cancellation + refunds

PHASE 12
Notifications + events

PHASE 13
Reviews

PHASE 14
Admin

PHASE 15
Frontend foundation

PHASE 16
User booking flow

PHASE 17
Admin UI

PHASE 18
Frontend/backend integration

PHASE 19
Testing

PHASE 20
Docker

PHASE 21
Documentation

PHASE 22
Final integration + quality audit
```

---

# 73. PHASE EXECUTION RULE

For every phase:

1. Read the relevant specification.
2. Inspect the existing code.
3. Explain the proposed architecture briefly.
4. Identify affected domain objects.
5. Identify interfaces.
6. Identify database changes.
7. Implement backend.
8. Implement tests.
9. Implement frontend where applicable.
10. Integrate.
11. Run compilation.
12. Run tests.
13. Fix failures.
14. Review against requirements.
15. Update documentation.
16. Only then proceed.

Never blindly overwrite working code.

Never rebuild existing functionality unnecessarily.

---

# 74. CONTEXT MANAGEMENT

This is a large project.

Do not try to keep the entire project only in conversation context.

Treat the repository as the source of truth.

Maintain:

```text
CLAUDE.md
docs/specification.md
docs/architecture.md
docs/lld.md
docs/database.md
docs/api.md
docs/concurrency.md
docs/implementation-status.md
```

After every major phase update:

```text
docs/implementation-status.md
```

Record:

* completed features
* current phase
* tests
* known issues
* architectural decisions
* remaining work

Before starting a new phase, inspect these files.

---

# 75. REQUIREMENT TRACEABILITY

Every major requirement must be traceable.

Maintain:

```text
Requirement
↓
Domain
↓
API
↓
Implementation
↓
Test
```

Do not silently omit requirements.

If a requirement conflicts with another requirement:

1. identify the conflict
2. explain it
3. choose the simplest consistent interpretation
4. document the decision

If a requirement is ambiguous:

**do not invent critical business behavior silently.**

Ask for clarification when the ambiguity materially affects architecture or correctness.

For minor UI details, choose a sensible production-quality default.

---

# 76. DEFINITION OF DONE

The project is complete only when:

## USER

```text
Register
Login
Browse
Search
Filter
Movie details
City
Theatre
Show
Seats
Lock
Checkout
Coupon
Payment
Confirmation
QR ticket
History
Cancellation
Refund
Review
Profile
Notifications
```

works end-to-end.

## ADMIN

```text
Dashboard
Movies
Genres
Languages
Theatres
Screens
Seats
Shows
Bookings
Users
Coupons
Reviews
Payments
Refunds
Analytics
Revenue
Occupancy
```

works end-to-end.

## SYSTEM

```text
Concurrency protection
Seat locking
Lock expiration
Payment idempotency
State transitions
Events
Notifications
Audit logging
Security
```

works against the real backend/database.

---

# 77. FINAL DELIVERABLES

Produce:

1. Complete source code
2. Spring Boot backend
3. React frontend
4. PostgreSQL schema
5. Flyway migrations
6. Seed data
7. Automated tests
8. Docker configuration
9. API documentation
10. README
11. LLD
12. ER diagram
13. Class diagrams
14. Sequence diagrams
15. State diagrams
16. Concurrency documentation
17. Setup instructions

The project must run locally.

---

# 78. ABSOLUTE RULES

Never:

* fake functionality
* fake database responses
* hardcode seat availability
* trust frontend prices
* trust frontend roles
* trust frontend user IDs
* expose JPA entities
* put business logic in controllers
* use in-memory concurrency protection as the primary mechanism
* silently ignore requirements
* create unnecessary microservices
* add patterns for academic decoration
* leave core methods as TODOs
* proceed while the current phase is fundamentally broken
* rewrite working modules without justification

---

# 79. START NOW

Before writing implementation code, produce:

### A. Architecture

Complete system architecture.

### B. Module Breakdown

All backend and frontend modules.

### C. Domain Model

Entities, value objects, aggregates and relationships.

### D. Class Responsibilities

Important classes/interfaces and their responsibilities.

### E. Design Patterns

Pattern + exact reason for using it.

### F. Database

Complete ER design and important constraints/indexes.

### G. APIs

REST API specification.

### H. Booking State Machine

All valid and invalid transitions.

### I. Concurrency

Detailed seat-locking and concurrent-booking strategy.

### J. Frontend

Page hierarchy and reusable component architecture.

### K. Visual System

Define the CineBook design system:

* typography
* spacing
* borders
* radii
* shadows
* gradients
* cards
* buttons
* navigation
* cinematic effects
* responsive breakpoints
* accessibility

The design must combine:

**MINIMAL STRUCTURE + MAXIMAL CINEMATIC IMPACT**

### L. Development Roadmap

Provide the phased implementation plan.

### M. Requirement Traceability

Map the specification to modules, APIs and tests.

---

# 80. FIRST ACTION

Do NOT immediately generate the entire application.

First inspect the repository.

Then:

1. summarize the current repository state
2. identify available tools/runtime
3. create the project documentation structure
4. create `CLAUDE.md`
5. create `docs/specification.md`
6. create `docs/architecture.md`
7. create `docs/lld.md`
8. create `docs/implementation-status.md`
9. produce the architecture proposal
10. identify ambiguities or contradictions
11. wait for architectural approval before beginning major implementation

Once architecture is approved, begin:

**PHASE 1 — Spring Boot Foundation**

Then proceed phase by phase.

Every phase must compile, test, integrate and remain functional before the next phase begins.

The final result should be demonstrable as:

> **CineBook — A modern Java-based online movie ticket booking platform demonstrating strong Low-Level Design, SOLID principles, design patterns, transactional seat locking, concurrent booking protection, payment abstraction, and premium modern web engineering.**
