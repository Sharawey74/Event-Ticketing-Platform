/* eslint-disable @next/next/no-img-element */
"use client";

import { useEffect, useState } from "react";

import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, CheckCircle } from "lucide-react";

import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

type Category = { id: number; name: string };
type Venue = { id: number; name: string; city: string };

type EditForm = {
  title: string;
  description: string;
  categoryId: string;
  venueId: string;
  startDate: string;
  endDate: string;
  salesOpenDate: string;
  salesCloseDate: string;
  coverImageUrl: string;
};

function toLocalDatetime(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return "";
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function toIso(local: string): string | null {
  if (!local) return null;
  return new Date(local).toISOString();
}

const inputClass =
  "w-full rounded-lg border border-outline-variant bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent py-3 px-4 font-body text-sm";
const labelClass = "block font-label-sm text-on-surface mb-1.5";

export default function EditEventPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { token, userRole } = useAuthStore();

  const [isClient, setIsClient] = useState(false);
  const [isLoadingEvent, setIsLoadingEvent] = useState(true);
  const [categories, setCategories] = useState<Category[]>([]);
  const [venues, setVenues] = useState<Venue[]>([]);
  const [form, setForm] = useState<EditForm>({
    title: "",
    description: "",
    categoryId: "",
    venueId: "",
    startDate: "",
    endDate: "",
    salesOpenDate: "",
    salesCloseDate: "",
    coverImageUrl: "",
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState(false);
  const [eventTitle, setEventTitle] = useState("");

  useEffect(() => {
    setIsClient(true);
  }, []);

  // Auth guard
  useEffect(() => {
    if (!isClient) return;
    if (!token) {
      router.push("/auth/login");
      return;
    }
    if (userRole !== "ORGANIZER") {
      router.push("/dashboard/bookings");
    }
  }, [isClient, token, userRole, router]);

  // Load catalogs + event data in parallel
  useEffect(() => {
    if (!isClient || !token || !id) return;

    Promise.all([
      api.get("/api/categories"),
      api.get("/api/venues"),
      api.get(`/api/events/${id}`),
    ])
      .then(([catRes, venueRes, eventRes]) => {
        setCategories(catRes.data?.data?.content ?? []);
        setVenues(venueRes.data?.data?.content ?? []);

        const ev = eventRes.data?.data ?? eventRes.data;
        setEventTitle(ev.title ?? "");
        setForm({
          title: ev.title ?? "",
          description: ev.description ?? "",
          categoryId: String(ev.category?.id ?? ev.categoryId ?? ""),
          venueId: String(ev.venue?.id ?? ev.venueId ?? ""),
          startDate: toLocalDatetime(ev.startDate),
          endDate: toLocalDatetime(ev.endDate),
          salesOpenDate: toLocalDatetime(ev.salesOpenDate),
          salesCloseDate: toLocalDatetime(ev.salesCloseDate),
          coverImageUrl: ev.coverImageUrl ?? "",
        });
      })
      .catch(() => {
        setSubmitError("Failed to load event data. Please go back and try again.");
      })
      .finally(() => setIsLoadingEvent(false));
  }, [isClient, token, id]);

  function set(field: keyof EditForm, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitError(null);

    if (!form.title.trim()) {
      setSubmitError("Event title is required.");
      return;
    }
    if (!form.startDate) {
      setSubmitError("Start date is required.");
      return;
    }
    if (!form.endDate) {
      setSubmitError("End date is required.");
      return;
    }
    if (new Date(form.endDate) <= new Date(form.startDate)) {
      setSubmitError("End date must be after start date.");
      return;
    }

    setIsSubmitting(true);
    try {
      await api.put(`/api/events/${id}`, {
        title: form.title.trim(),
        description: form.description.trim() || null,
        categoryId: form.categoryId ? Number(form.categoryId) : null,
        venueId: form.venueId ? Number(form.venueId) : null,
        startDate: toIso(form.startDate),
        endDate: toIso(form.endDate),
        salesOpenDate: toIso(form.salesOpenDate),
        salesCloseDate: toIso(form.salesCloseDate),
        coverImageUrl: form.coverImageUrl.trim() || null,
      });
      setSubmitSuccess(true);
      setTimeout(() => router.push("/organizer/events"), 2000);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data
          ?.message ?? "Failed to update event. Please try again.";
      setSubmitError(msg);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!isClient || isLoadingEvent) {
    return (
      <div className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen flex items-center justify-center">
        <p className="text-on-surface-variant">Loading event...</p>
      </div>
    );
  }

  if (!token || userRole !== "ORGANIZER") {
    return null;
  }

  if (submitSuccess) {
    return (
      <div className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen flex items-center justify-center">
        <div className="text-center space-y-4">
          <CheckCircle className="h-16 w-16 text-emerald-500 mx-auto" />
          <h2 className="font-hero-headline-mobile text-on-surface">Event Updated!</h2>
          <p className="text-on-surface-variant font-body">
            &ldquo;{eventTitle}&rdquo; has been saved. Redirecting to your events&hellip;
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="grow pt-[104px] pb-section-gap px-edge-padding max-w-container-max mx-auto w-full min-h-screen">
      {/* Back nav */}
      <nav className="mb-stack-md">
        <Link
          href="/organizer/events"
          className="inline-flex items-center gap-1 text-primary hover:text-primary-container-variant transition-colors font-label-sm"
        >
          <ArrowLeft className="h-[18px] w-[18px]" aria-hidden="true" />
          Back to Events
        </Link>
      </nav>

      {/* Header */}
      <div className="mb-stack-lg">
        <h1 className="font-hero-headline text-hero-headline text-on-surface">Edit Event</h1>
        <p className="font-body text-body text-on-surface-variant mt-1">
          Update the details for &ldquo;{eventTitle}&rdquo;.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-6 max-w-2xl">
        {/* Basic Info */}
        <section className="bg-surface-container-lowest rounded-xl border border-outline-variant/30 shadow-sm p-6 space-y-5">
          <h2 className="font-section-heading text-section-heading text-on-surface">Basic Information</h2>

          <div>
            <label className={labelClass}>Event Title *</label>
            <input
              className={inputClass}
              type="text"
              value={form.title}
              onChange={(e) => set("title", e.target.value)}
              placeholder="e.g. Summer Music Festival 2026"
              required
            />
          </div>

          <div>
            <label className={labelClass}>Description</label>
            <textarea
              className={`${inputClass} resize-none`}
              rows={4}
              value={form.description}
              onChange={(e) => set("description", e.target.value)}
              placeholder="Describe the event experience..."
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Category</label>
              <select
                className={inputClass}
                value={form.categoryId}
                onChange={(e) => set("categoryId", e.target.value)}
              >
                <option value="">Select category</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className={labelClass}>Cover Image URL</label>
              <input
                className={inputClass}
                type="url"
                value={form.coverImageUrl}
                onChange={(e) => set("coverImageUrl", e.target.value)}
                placeholder="https://..."
              />
            </div>
          </div>

          {form.coverImageUrl && (
            <div className="w-full h-40 rounded-lg overflow-hidden border border-outline-variant">
              <img
                src={form.coverImageUrl}
                alt="Cover preview"
                className="w-full h-full object-cover"
              />
            </div>
          )}
        </section>

        {/* Schedule */}
        <section className="bg-surface-container-lowest rounded-xl border border-outline-variant/30 shadow-sm p-6 space-y-5">
          <h2 className="font-section-heading text-section-heading text-on-surface">Schedule & Venue</h2>

          <div>
            <label className={labelClass}>Venue</label>
            <select
              className={inputClass}
              value={form.venueId}
              onChange={(e) => set("venueId", e.target.value)}
            >
              <option value="">Select venue</option>
              {venues.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} — {v.city}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Start Date & Time *</label>
              <input
                className={inputClass}
                type="datetime-local"
                value={form.startDate}
                onChange={(e) => set("startDate", e.target.value)}
                required
              />
            </div>
            <div>
              <label className={labelClass}>End Date & Time *</label>
              <input
                className={inputClass}
                type="datetime-local"
                value={form.endDate}
                min={form.startDate || undefined}
                onChange={(e) => set("endDate", e.target.value)}
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className={labelClass}>Sales Open Date</label>
              <input
                className={inputClass}
                type="datetime-local"
                value={form.salesOpenDate}
                onChange={(e) => set("salesOpenDate", e.target.value)}
              />
            </div>
            <div>
              <label className={labelClass}>Sales Close Date</label>
              <input
                className={inputClass}
                type="datetime-local"
                value={form.salesCloseDate}
                min={form.salesOpenDate || undefined}
                onChange={(e) => set("salesCloseDate", e.target.value)}
              />
            </div>
          </div>
        </section>

        {/* Error */}
        {submitError && (
          <div className="rounded-lg bg-error-container text-on-error-container px-4 py-3 text-sm font-medium">
            {submitError}
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center gap-3 pb-8">
          <button
            type="submit"
            disabled={isSubmitting}
            className="bg-primary text-on-primary rounded-full px-8 py-3 font-label-sm shadow-md hover:shadow-xl transition-shadow disabled:opacity-60 disabled:cursor-not-allowed"
          >
            {isSubmitting ? "Saving..." : "Save Changes"}
          </button>
          <Link
            href="/organizer/events"
            className="rounded-full px-8 py-3 font-label-sm border border-outline-variant text-on-surface-variant hover:border-primary hover:text-primary transition-colors"
          >
            Cancel
          </Link>
        </div>
      </form>
    </div>
  );
}
