/* eslint-disable @next/next/no-img-element */
"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Plus, Trash2, ChevronRight, ChevronLeft, CheckCircle } from "lucide-react";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

type Category = { id: number; name: string };
type Venue = { id: number; name: string; city: string };

type TierRow = {
  tierName: string;
  description: string;
  basePrice: string;
  totalCapacity: string;
  maxPerBooking: string;
};

type FormData = {
  title: string;
  description: string;
  categoryId: string;
  coverImageUrl: string;
  venueId: string;
  startDate: string;
  endDate: string;
  salesOpenDate: string;
  salesCloseDate: string;
  ticketTiers: TierRow[];
};

const blankTier = (): TierRow => ({
  tierName: "",
  description: "",
  basePrice: "",
  totalCapacity: "",
  maxPerBooking: "4",
});

const STEPS = ["Basic Info", "Schedule", "Tickets", "Review"];

function toIso(local: string): string | undefined {
  if (!local) return undefined;
  return new Date(local).toISOString();
}

const inputClass =
  "w-full rounded-lg border border-outline-variant bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent py-3 px-4 font-body text-sm";
const labelClass = "block font-label-sm text-on-surface mb-1.5";

export default function CreateEventPage() {
  const router = useRouter();
  const { token, userRole } = useAuthStore();

  const [step, setStep] = useState(1);
  const [categories, setCategories] = useState<Category[]>([]);
  const [venues, setVenues] = useState<Venue[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [stepError, setStepError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [minDateTime] = useState(() => {
    const d = new Date(Date.now() + 10 * 60 * 1000);
    const pad = (n: number) => n.toString().padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  });

  const [form, setForm] = useState<FormData>({
    title: "",
    description: "",
    categoryId: "",
    coverImageUrl: "",
    venueId: "",
    startDate: "",
    endDate: "",
    salesOpenDate: "",
    salesCloseDate: "",
    ticketTiers: [blankTier()],
  });

  useEffect(() => {
    if (!token || userRole !== "ORGANIZER") {
      router.push("/auth/login");
      return;
    }
    api.get("/api/categories", { params: { page: 0, size: 50 } })
      .then((r) => setCategories(r.data?.data?.content ?? []))
      .catch(() => {});
    api.get("/api/venues", { params: { page: 0, size: 100 } })
      .then((r) => setVenues(r.data?.data?.content ?? []))
      .catch(() => {});
  }, [token, userRole, router]);

  const set = (field: keyof Omit<FormData, "ticketTiers">, value: string) =>
    setForm((f) => ({ ...f, [field]: value }));

  const setTier = (i: number, field: keyof TierRow, value: string) =>
    setForm((f) => {
      const tiers = [...f.ticketTiers];
      tiers[i] = { ...tiers[i], [field]: value };
      return { ...f, ticketTiers: tiers };
    });

  const addTier = () =>
    setForm((f) => ({ ...f, ticketTiers: [...f.ticketTiers, blankTier()] }));

  const removeTier = (i: number) =>
    setForm((f) => ({ ...f, ticketTiers: f.ticketTiers.filter((_, idx) => idx !== i) }));

  const handleSubmit = async () => {
    setSubmitError(null);
    if (form.startDate && new Date(form.startDate) <= new Date()) {
      setSubmitError("The start date is now in the past. Please go back and select a later date.");
      return;
    }
    if (form.endDate && form.startDate && new Date(form.endDate) <= new Date(form.startDate)) {
      setSubmitError("End date must be after the start date.");
      return;
    }
    setIsSubmitting(true);
    try {
      await api.post(
        "/api/events",
        {
          title: form.title,
          description: form.description,
          categoryId: Number(form.categoryId),
          venueId: Number(form.venueId),
          coverImageUrl: form.coverImageUrl || undefined,
          startDate: toIso(form.startDate),
          endDate: toIso(form.endDate),
          salesOpenDate: toIso(form.salesOpenDate),
          salesCloseDate: toIso(form.salesCloseDate),
          ticketTiers: form.ticketTiers.map((t) => ({
            tierName: t.tierName,
            description: t.description || undefined,
            basePrice: Number(t.basePrice),
            totalCapacity: Number(t.totalCapacity),
            maxPerBooking: Number(t.maxPerBooking) || 4,
          })),
        },
        { headers: { Authorization: `Bearer ${token}` } }
      );
      setSubmitSuccess(true);
      setTimeout(() => router.push("/organizer/events"), 2500);
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setSubmitError((err as any)?.response?.data?.message ?? "Failed to create event.");
    } finally {
      setIsSubmitting(false);
    }
  };

  const progressWidthClass: Record<number, string> = { 1: "w-0", 2: "w-1/3", 3: "w-2/3", 4: "w-full" };

  const selectedCategory = categories.find((c) => String(c.id) === form.categoryId);
  const selectedVenue = venues.find((v) => String(v.id) === form.venueId);

  return (
    <main className="grow pt-[104px] pb-section-gap px-edge-padding max-w-[800px] mx-auto w-full min-h-screen">
      <div className="max-w-3xl mx-auto">

        <nav className="mb-6">
          <Link href="/organizer/events" className="inline-flex items-center gap-1 text-primary hover:opacity-80 transition-opacity font-label-sm text-sm">
            <ChevronLeft className="h-4 w-4" />
            Cancel
          </Link>
        </nav>

        <div className="mb-8 text-center">
          <h1 className="text-3xl font-bold text-on-background">Create New Event</h1>
          <p className="text-on-surface-variant mt-1">Step {step} of {STEPS.length} — {STEPS[step - 1]}</p>
        </div>

        {/* Progress bar */}
        <div className="relative mb-8 flex justify-between items-center">
          <div className="absolute top-4 left-0 w-full h-0.5 bg-surface-container-highest z-0" />
          <div
            className={`absolute top-4 left-0 h-0.5 bg-primary z-0 transition-all duration-300 ${progressWidthClass[step] ?? "w-0"}`}
          />
          {STEPS.map((label, idx) => {
            const s = idx + 1;
            const active = s === step;
            const done = s < step;
            return (
              <div key={s} className="relative z-10 flex flex-col items-center gap-1">
                <div
                  className={`w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold shadow-sm transition-colors ${
                    done
                      ? "bg-primary text-on-primary"
                      : active
                      ? "bg-primary text-on-primary ring-4 ring-primary/20"
                      : "bg-surface-container-highest text-on-surface-variant"
                  }`}
                >
                  {done ? "✓" : s}
                </div>
                <span className={`text-xs font-medium ${active ? "text-primary" : "text-on-surface-variant"}`}>
                  {label}
                </span>
              </div>
            );
          })}
        </div>

        <div className="bg-surface-container-lowest rounded-xl shadow-md p-8 border border-outline-variant/50">
          {submitSuccess && (
            <div className="flex flex-col items-center justify-center py-16 gap-4 text-center">
              <CheckCircle className="h-16 w-16 text-emerald-500" />
              <h2 className="text-xl font-bold text-on-surface">Event Created!</h2>
              <p className="text-on-surface-variant font-medium">{form.title}</p>
              <p className="text-sm text-on-surface-variant">Redirecting to your events…</p>
            </div>
          )}

          {!submitSuccess && (<>

          {/* ── Step 1: Basic Info ─────────────────────────────────────────── */}
          {step === 1 && (
            <div className="space-y-5">
              <h2 className="text-xl font-semibold text-on-surface mb-2">Basic Information</h2>

              <div>
                <label className={labelClass}>Event Title *</label>
                <input
                  type="text"
                  required
                  value={form.title}
                  onChange={(e) => set("title", e.target.value)}
                  placeholder="E.g., Amr Diab Live in Cairo"
                  className={inputClass}
                />
              </div>

              <div>
                <label className={labelClass}>Description *</label>
                <textarea
                  required
                  rows={4}
                  value={form.description}
                  onChange={(e) => set("description", e.target.value)}
                  placeholder="Describe what makes your event special..."
                  className={`${inputClass} resize-y`}
                />
              </div>

              <div>
                <label className={labelClass}>Category *</label>
                {categories.length === 0 ? (
                  <p className="text-sm text-on-surface-variant">Loading categories…</p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {categories.map((cat) => (
                      <button
                        key={cat.id}
                        type="button"
                        onClick={() => set("categoryId", String(cat.id))}
                        className={`rounded-full border px-4 py-2 text-sm font-medium transition-all ${
                          form.categoryId === String(cat.id)
                            ? "bg-primary text-on-primary border-primary shadow-sm"
                            : "border-outline-variant text-on-surface-variant hover:border-primary hover:text-primary"
                        }`}
                      >
                        {cat.name}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div>
                <label className={labelClass}>Cover Image URL</label>
                <input
                  type="url"
                  value={form.coverImageUrl}
                  onChange={(e) => set("coverImageUrl", e.target.value)}
                  placeholder="https://images.unsplash.com/photo-..."
                  className={inputClass}
                />
                {form.coverImageUrl && (
                  <img
                    src={form.coverImageUrl}
                    alt="Preview"
                    className="mt-2 w-full max-h-48 object-cover rounded-lg border border-outline-variant"
                    onError={(e) => (e.currentTarget.style.display = "none")}
                  />
                )}
              </div>
            </div>
          )}

          {/* ── Step 2: Schedule ────────────────────────────────────────────── */}
          {step === 2 && (
            <div className="space-y-5">
              <h2 className="text-xl font-semibold text-on-surface mb-2">Schedule & Venue</h2>

              <div>
                <label className={labelClass}>Venue *</label>
                {venues.length === 0 ? (
                  <p className="text-sm text-on-surface-variant">Loading venues…</p>
                ) : (
                  <select
                    required
                    aria-label="Venue"
                    value={form.venueId}
                    onChange={(e) => set("venueId", e.target.value)}
                    className={inputClass}
                  >
                    <option value="">Select a venue…</option>
                    {venues.map((v) => (
                      <option key={v.id} value={v.id}>
                        {v.name} — {v.city}
                      </option>
                    ))}
                  </select>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className={labelClass}>Start Date & Time *</label>
                  <input
                    type="datetime-local"
                    required
                    aria-label="Start Date and Time"
                    value={form.startDate}
                    min={minDateTime}
                    onChange={(e) => { set("startDate", e.target.value); setStepError(null); }}
                    className={inputClass}
                  />
                </div>
                <div>
                  <label className={labelClass}>End Date & Time *</label>
                  <input
                    type="datetime-local"
                    required
                    aria-label="End Date and Time"
                    value={form.endDate}
                    min={form.startDate || minDateTime}
                    onChange={(e) => { set("endDate", e.target.value); setStepError(null); }}
                    className={inputClass}
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className={labelClass}>Sales Open (optional)</label>
                  <input
                    type="datetime-local"
                    aria-label="Sales Open Date"
                    value={form.salesOpenDate}
                    onChange={(e) => set("salesOpenDate", e.target.value)}
                    className={inputClass}
                  />
                </div>
                <div>
                  <label className={labelClass}>Sales Close (optional)</label>
                  <input
                    type="datetime-local"
                    aria-label="Sales Close Date"
                    value={form.salesCloseDate}
                    onChange={(e) => set("salesCloseDate", e.target.value)}
                    className={inputClass}
                  />
                </div>
              </div>
            </div>
          )}

          {/* ── Step 3: Tickets ─────────────────────────────────────────────── */}
          {step === 3 && (
            <div className="space-y-5">
              <h2 className="text-xl font-semibold text-on-surface mb-2">Ticket Tiers</h2>

              {form.ticketTiers.map((tier, i) => (
                <div key={i} className="border border-outline-variant rounded-xl p-5 space-y-4 relative">
                  {form.ticketTiers.length > 1 && (
                    <button
                      type="button"
                      onClick={() => removeTier(i)}
                      className="absolute top-4 right-4 text-on-surface-variant hover:text-error transition-colors"
                      aria-label="Remove tier"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                  <p className="text-xs uppercase tracking-wider text-on-surface-variant font-semibold">
                    Tier {i + 1}
                  </p>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className={labelClass}>Tier Name *</label>
                      <input
                        type="text"
                        required
                        value={tier.tierName}
                        onChange={(e) => setTier(i, "tierName", e.target.value)}
                        placeholder="E.g., General Admission"
                        className={inputClass}
                      />
                    </div>
                    <div>
                      <label className={labelClass}>Base Price (EGP) *</label>
                      <input
                        type="number"
                        required
                        min="0"
                        step="0.01"
                        value={tier.basePrice}
                        onChange={(e) => setTier(i, "basePrice", e.target.value)}
                        placeholder="0.00"
                        className={inputClass}
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                      <label className={labelClass}>Total Capacity *</label>
                      <input
                        type="number"
                        required
                        min="1"
                        value={tier.totalCapacity}
                        onChange={(e) => setTier(i, "totalCapacity", e.target.value)}
                        placeholder="500"
                        className={inputClass}
                      />
                    </div>
                    <div>
                      <label className={labelClass}>Max Per Booking</label>
                      <input
                        type="number"
                        min="1"
                        max="20"
                        aria-label="Maximum tickets per booking"
                        placeholder="4"
                        value={tier.maxPerBooking}
                        onChange={(e) => setTier(i, "maxPerBooking", e.target.value)}
                        className={inputClass}
                      />
                    </div>
                  </div>

                  <div>
                    <label className={labelClass}>Description (optional)</label>
                    <input
                      type="text"
                      value={tier.description}
                      onChange={(e) => setTier(i, "description", e.target.value)}
                      placeholder="E.g., Front-row seating with complimentary refreshments"
                      className={inputClass}
                    />
                  </div>
                </div>
              ))}

              <button
                type="button"
                onClick={addTier}
                className="flex items-center gap-2 text-sm text-primary hover:opacity-80 transition-opacity font-medium"
              >
                <Plus className="h-4 w-4" />
                Add Another Tier
              </button>
            </div>
          )}

          {/* ── Step 4: Review + Submit ─────────────────────────────────────── */}
          {step === 4 && (
            <div className="space-y-6">
              <h2 className="text-xl font-semibold text-on-surface mb-2">Review & Publish</h2>

              {submitError && (
                <div className="bg-error/10 border border-error/20 rounded-lg p-3 text-sm text-error">
                  {submitError}
                </div>
              )}

              <div className="space-y-4 text-sm">
                <section className="border border-outline-variant rounded-xl p-5">
                  <p className="text-xs uppercase tracking-wider text-on-surface-variant font-semibold mb-3">Basic Info</p>
                  <p className="font-semibold text-on-surface text-base">{form.title}</p>
                  {form.description && <p className="text-on-surface-variant mt-1 line-clamp-3">{form.description}</p>}
                  <p className="text-on-surface-variant mt-2">Category: <span className="text-on-surface">{selectedCategory?.name ?? "—"}</span></p>
                  {form.coverImageUrl && (
                    <img
                      src={form.coverImageUrl}
                      alt="Cover"
                      className="mt-3 w-full max-h-32 object-cover rounded-lg"
                      onError={(e) => (e.currentTarget.style.display = "none")}
                    />
                  )}
                </section>

                <section className="border border-outline-variant rounded-xl p-5">
                  <p className="text-xs uppercase tracking-wider text-on-surface-variant font-semibold mb-3">Schedule & Venue</p>
                  <p className="text-on-surface-variant">Venue: <span className="text-on-surface">{selectedVenue ? `${selectedVenue.name} — ${selectedVenue.city}` : "—"}</span></p>
                  <p className="text-on-surface-variant mt-1">Start: <span className="text-on-surface">{form.startDate ? new Date(form.startDate).toLocaleString() : "—"}</span></p>
                  <p className="text-on-surface-variant mt-1">End: <span className="text-on-surface">{form.endDate ? new Date(form.endDate).toLocaleString() : "—"}</span></p>
                  {form.salesOpenDate && <p className="text-on-surface-variant mt-1">Sales open: <span className="text-on-surface">{new Date(form.salesOpenDate).toLocaleString()}</span></p>}
                  {form.salesCloseDate && <p className="text-on-surface-variant mt-1">Sales close: <span className="text-on-surface">{new Date(form.salesCloseDate).toLocaleString()}</span></p>}
                </section>

                <section className="border border-outline-variant rounded-xl p-5">
                  <p className="text-xs uppercase tracking-wider text-on-surface-variant font-semibold mb-3">Ticket Tiers</p>
                  <div className="space-y-3">
                    {form.ticketTiers.map((t, i) => (
                      <div key={i} className="flex justify-between items-start">
                        <div>
                          <p className="font-medium text-on-surface">{t.tierName || "Unnamed"}</p>
                          {t.description && <p className="text-on-surface-variant text-xs mt-0.5">{t.description}</p>}
                        </div>
                        <div className="text-right shrink-0 ml-4">
                          <p className="font-semibold text-primary">EGP {Number(t.basePrice || 0).toLocaleString()}</p>
                          <p className="text-on-surface-variant text-xs">{t.totalCapacity || 0} seats · max {t.maxPerBooking || 4}/booking</p>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              </div>
            </div>
          )}

          {/* ── Navigation buttons ──────────────────────────────────────────── */}
          {step === 2 && stepError && (
            <div className="mt-4 bg-error/10 border border-error/20 rounded-lg p-3 text-sm text-error">
              {stepError}
            </div>
          )}

          <div className="flex justify-between mt-8 pt-6 border-t border-outline-variant/30">
            {step > 1 ? (
              <button
                type="button"
                onClick={() => { setStep((s) => s - 1); setStepError(null); }}
                className="flex items-center gap-1.5 text-sm text-on-surface-variant hover:text-on-surface transition-colors font-medium px-4 py-2.5 rounded-full border border-outline-variant hover:border-outline"
              >
                <ChevronLeft className="h-4 w-4" />
                Back
              </button>
            ) : (
              <div />
            )}

            {step < 4 ? (
              <button
                type="button"
                onClick={() => {
                  setStepError(null);
                  if (step === 1 && (!form.title.trim() || !form.categoryId)) return;
                  if (step === 2) {
                    if (!form.venueId) { setStepError("Please select a venue."); return; }
                    if (!form.startDate) { setStepError("Please set a start date and time."); return; }
                    if (!form.endDate) { setStepError("Please set an end date and time."); return; }
                    if (new Date(form.startDate) <= new Date()) {
                      setStepError("Start date must be in the future. Please select a later time."); return;
                    }
                    if (new Date(form.endDate) <= new Date(form.startDate)) {
                      setStepError("End date must be after the start date."); return;
                    }
                  }
                  if (step === 3 && form.ticketTiers.some((t) => !t.tierName || !t.basePrice || !t.totalCapacity)) return;
                  setStep((s) => s + 1);
                }}
                className="flex items-center gap-1.5 bg-primary text-on-primary rounded-full px-6 py-2.5 text-sm font-semibold hover:shadow-lg transition-all"
              >
                Next
                <ChevronRight className="h-4 w-4" />
              </button>
            ) : (
              <button
                type="button"
                onClick={handleSubmit}
                disabled={isSubmitting}
                className="flex items-center gap-1.5 bg-primary text-on-primary rounded-full px-8 py-2.5 text-sm font-semibold hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? "Creating…" : "Create Event"}
              </button>
            )}
          </div>
          </>)}
        </div>
      </div>
    </main>
  );
}
