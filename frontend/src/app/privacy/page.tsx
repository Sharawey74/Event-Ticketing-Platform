import Link from "next/link";
import { ArrowLeft } from "lucide-react";

export default function PrivacyPage() {
  return (
    <div className="pt-32 pb-section-gap px-edge-padding max-w-3xl mx-auto min-h-screen">
      <nav className="mb-8">
        <Link href="/" className="inline-flex min-h-11 items-center gap-1 rounded text-primary outline-none font-label-sm hover:underline focus-visible:ring-2 focus-visible:ring-primary/50">
          <ArrowLeft className="h-[18px] w-[18px]" aria-hidden="true" />
          Back to Home
        </Link>
      </nav>

      <h1 className="font-hero-headline text-hero-headline text-on-surface mb-4">Privacy Policy</h1>
      <p className="font-caption text-on-surface-variant mb-10">Last updated: September 2026</p>

      <div className="space-y-8 font-body text-body text-on-surface-variant leading-relaxed">
        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">1. Information We Collect</h2>
          <p>We collect information you provide when creating an account (name, email, password), booking tickets (event and ticket preferences), and communicating with us. We also collect usage data to improve the platform experience.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">2. How We Use Your Information</h2>
          <p>Your information is used to process bookings and payments, send booking confirmations and QR codes, communicate event updates, and improve our services. We do not sell your personal data to third parties.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">3. Payment Data</h2>
          <p>Payments are handled by Stripe. Eventora does not store credit or debit card information. Stripe&apos;s privacy policy governs how your payment data is processed.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">4. Data Retention</h2>
          <p>We retain your account and booking data for as long as your account is active or as required by law. You may request deletion of your account and associated data by contacting us.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">5. Cookies</h2>
          <p>We use cookies to maintain your session and authentication state. These are essential for the platform to function correctly and cannot be disabled while using the service.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">6. Third-Party Services</h2>
          <p>Eventora integrates with Stripe for payment processing. These services have their own privacy policies and we encourage you to review them.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">7. Your Rights</h2>
          <p>You have the right to access, correct, or delete your personal data. To exercise these rights, please contact us at <a
            href="mailto:privacy@eventora.com"
            className="rounded text-primary underline underline-offset-2 outline-none hover:text-primary-container focus-visible:ring-2 focus-visible:ring-primary/50"
          >
            privacy@eventora.com
          </a>.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">8. Changes to This Policy</h2>
          <p>We may update this privacy policy periodically. We will notify you of significant changes via email or a notice on the platform.</p>
        </section>
      </div>
    </div>
  );
}
