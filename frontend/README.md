# TourLK Frontend

React + TypeScript frontend for TourLK, built with Vite. This is the shared
foundation (auth flow, layout, routing skeleton) that every module team builds
their own pages on top of.

## Stack

- Vite + React + TypeScript
- React Router v6
- Axios
- Tailwind CSS
- Zustand (auth state)

## Getting started

```bash
cd frontend
npm install
cp .env.example .env
npm run dev
```

The app runs at `http://localhost:5173`. It expects the Spring Boot backend
(see repo root) running at the URL in `VITE_API_BASE_URL` (default
`http://localhost:8080/api`) — start that first with `mvn spring-boot:run`.

## Environment variables

| Variable                      | Description                        | Default                        |
|--------------------------------|-------------------------------------|---------------------------------|
| `VITE_API_BASE_URL`            | Base URL for backend API calls      | `http://localhost:8080/api`     |
| `VITE_STRIPE_PUBLISHABLE_KEY`  | Stripe **test-mode** publishable key (starts with `pk_test_`) | none — see [Payments (Stripe) setup](#payments-stripe-setup) |

Copy `.env.example` to `.env` and adjust as needed. `.env` is gitignored —
never commit real values there.

## Project structure

```
src/
 ├── api/             # axios instance + per-module API call functions
 ├── auth/            # auth state (Zustand), ProtectedRoute, RoleGate
 ├── components/
 │    ├── layout/     # Navbar, Layout — shared page chrome
 │    └── ui/         # shared Button/Input/Select/Card primitives
 ├── pages/
 │    ├── auth/       # LoginPage, RegisterPage
 │    └── ...         # DashboardPage, NotFoundPage
 └── types/           # shared TypeScript types (mirrors backend DTOs)
```

## Adding your module's pages

Same "stay in your own folder" convention as the backend (see root
[`CONTRIBUTING.md`](../CONTRIBUTING.md)), adapted for a feature-first frontend:

- Pages: create `src/pages/<module>/`, e.g. `src/pages/booking/BookingListPage.tsx`.
- Components specific to your module: `src/components/<module>/`, e.g.
  `src/components/booking/BookingCard.tsx`.
- API calls: add `src/api/<module>Api.ts`, e.g. `src/api/bookingApi.ts`, using
  the shared `axiosClient` from `src/api/axiosClient.ts` (it already attaches
  the JWT and handles 401s — don't create a second axios instance).
- Types: add `src/types/<module>.ts` for your module's request/response
  shapes, matching your backend DTOs exactly (see `src/types/auth.ts` for the
  pattern).
- Wire your pages into the router in `src/App.tsx`. Wrap protected routes with
  `<ProtectedRoute>` (optionally pass `roles={[...]}` to restrict by role —
  see `src/auth/ProtectedRoute.tsx`), and use `RoleGate` to conditionally show
  UI within a page based on `user.role`.
- Reuse the primitives in `src/components/ui/` (`Button`, `Input`, `Select`,
  `Card`) instead of writing new ones, unless your module genuinely needs
  something they don't cover.

Branch per module (`feature/<module-name>`), same as the backend. If you need
to touch shared code (`src/api/axiosClient.ts`, `src/auth/`,
`src/components/layout/`), call that out explicitly in your PR description.

## Payments (Stripe) setup

The Payment & Invoicing module uses **Stripe in test mode** — sandbox only,
no real money moves, no real card details ever touch our server (Stripe.js
collects and tokenizes those directly in the browser).

### 1. Get your test API keys

1. Sign up / log in at [dashboard.stripe.com](https://dashboard.stripe.com).
2. Make sure you're in **Test mode** (toggle in the top-right of the dashboard).
3. Go to **Developers → API keys**.
4. Copy the two test-mode keys:
   - **Publishable key** (starts with `pk_test_...`) → put this in
     `frontend/.env` as `VITE_STRIPE_PUBLISHABLE_KEY`.
   - **Secret key** (starts with `sk_test_...`) → put this in your backend
     environment (or `application-local.yml`, same gitignored pattern as
     `JWT_SECRET`/`MAIL_*`) as `STRIPE_SECRET_KEY`. **Never commit this or
     put it in the frontend** — it's server-only.

### 2. Test the webhook locally with the Stripe CLI

The backend needs Stripe to call `POST /api/payments/webhook` when a payment
succeeds or fails. Locally, your machine isn't reachable from the internet,
so use the [Stripe CLI](https://docs.stripe.com/stripe-cli) to forward events:

```bash
stripe login
stripe listen --forward-to localhost:8080/api/payments/webhook
```

This prints a webhook signing secret like `whsec_...` — put that in your
backend environment as `STRIPE_WEBHOOK_SECRET`. Keep the `stripe listen`
command running in a terminal while you test payments locally; it forwards
real Stripe test-mode events to your local backend and re-signs them with
that secret, which is what `PaymentController` verifies against before
trusting anything in the webhook body.

### 3. Test cards

Use any of [Stripe's test card numbers](https://docs.stripe.com/testing) —
`4242 4242 4242 4242`, any future expiry, any 3-digit CVC, any postal code —
to simulate a successful payment on the Checkout page. Other numbers in that
list simulate declines, requires-authentication flows, etc.
