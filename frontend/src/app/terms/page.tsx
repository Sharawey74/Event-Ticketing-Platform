import Link from "next/link";
import { ArrowLeft } from "lucide-react";

export default function TermsPage() {
  return (
    <div className="pt-32 pb-section-gap px-edge-padding max-w-3xl mx-auto min-h-screen">
      <nav className="mb-8">
        <Link href="/" className="inline-flex min-h-11 items-center gap-1 rounded text-primary outline-none font-label-sm hover:underline focus-visible:ring-2 focus-visible:ring-primary/50">
          <ArrowLeft className="h-[18px] w-[18px]" aria-hidden="true" />
          Back to Home
        </Link>
      </nav>

      <h1 className="font-hero-headline text-hero-headline text-on-surface mb-4">Terms of Service</h1>
      <p className="font-caption text-on-surface-variant mb-10">Last updated: September 2026</p>

      <div className="space-y-8 font-body text-body text-on-surface-variant leading-relaxed">
        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">1. Acceptance of Terms</h2>
          <p>By accessing or using Eventora, you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use our platform.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">2. Use of the Platform</h2>
          <p>Eventora is an event ticketing platform that allows users to discover events, purchase tickets, and manage bookings. Organizers may create and manage events subject to approval. You agree not to misuse the platform or attempt to circumvent any security measures.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">3. Ticket Purchases</h2>
          <p>All ticket purchases are final unless a refund is requested within the eligible window. Full refunds are available up to 7 days before the event. Partial (50%) refunds are available between 3–6 days before the event. No refunds are issued within 3 days of the event.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">4. Organizer Responsibilities</h2>
          <p>Event organizers are solely responsible for the accuracy of event details, venue arrangements, and delivery of the event experience. Eventora is not liable for event cancellations, changes, or quality disputes.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">5. Payments</h2>
          <p>Payments are processed securely through Stripe. Eventora does not store your payment card details. All prices are listed in Egyptian Pounds (EGP).</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">6. Account Security</h2>
          <p>You are responsible for maintaining the confidentiality of your account credentials. Please notify us immediately if you suspect unauthorized access to your account.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">7. Changes to Terms</h2>
          <p>We may update these terms from time to time. Continued use of the platform after changes constitutes acceptance of the updated terms.</p>
        </section>

        <section>
          <h2 className="font-section-heading text-section-heading text-on-surface mb-3">8. Contact</h2>
          <p>For questions about these terms, please contact us at <a
            href="mailto:support@eventora.com"
            className="rounded text-primary underline underline-offset-2 outline-none hover:text-primary-container focus-visible:ring-2 focus-visible:ring-primary/50"
          >
            support@eventora.com
          </a>.</p>
        </section>
      </div>
    </div>
  );
}
