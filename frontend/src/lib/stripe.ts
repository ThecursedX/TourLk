import { loadStripe } from '@stripe/stripe-js'

// Shared across CheckoutPage and PaymentMethodsPage so Stripe.js is only loaded once.
const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY)

export default stripePromise
