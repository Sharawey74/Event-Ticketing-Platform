"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/authStore";

export default function CreateEventPage() {
  const router = useRouter();
  const { token, userRole } = useAuthStore();
  
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [category, setCategory] = useState("MUSIC");

  // In a real app, we'd handle unauthorized access, but middleware handles it primarily
  if (!token || userRole !== "ORGANIZER") {
    if (typeof window !== "undefined") {
      router.push("/auth/login");
    }
    return null;
  }

  const handleNext = (e: React.FormEvent) => {
    e.preventDefault();
    // Move to step 2 logic goes here
    alert("Moving to Step 2: Schedule (Placeholder)");
  };

  return (
    <main className="flex-grow pt-[104px] pb-section-gap px-edge-padding w-full min-h-screen">
      <div className="max-w-3xl mx-auto">
        
        {/* Back Link */}
        <nav className="mb-stack-md">
          <Link href="/organizer/events" className="inline-flex items-center gap-1 text-primary hover:text-primary-container-variant transition-colors font-label-sm">
            <span className="material-symbols-outlined text-[18px]">arrow_back</span>
            Cancel
          </Link>
        </nav>

        {/* Page Header */}
        <div className="mb-stack-lg text-center">
          <h1 className="font-hero-headline text-hero-headline text-on-background mb-2">Create New Event</h1>
          <p className="font-body-lg text-body-lg text-on-surface-variant">Provide the details to get your event published.</p>
        </div>

        {/* Progress Indicator */}
        <div className="relative mb-stack-lg flex justify-between">
          <div className="absolute top-1/2 left-0 w-full h-[2px] bg-surface-container-highest -translate-y-1/2 z-0"></div>
          <div className="absolute top-1/2 left-0 w-[25%] h-[2px] bg-primary -translate-y-1/2 z-0 transition-all duration-300"></div>
          
          <div className="relative z-10 flex flex-col items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-primary text-on-primary flex items-center justify-center font-label-sm font-bold shadow-sm">1</div>
            <span className="font-caption text-primary font-bold">Basic Info</span>
          </div>
          <div className="relative z-10 flex flex-col items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-label-sm font-bold">2</div>
            <span className="font-caption text-on-surface-variant">Schedule</span>
          </div>
          <div className="relative z-10 flex flex-col items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-label-sm font-bold">3</div>
            <span className="font-caption text-on-surface-variant">Tickets</span>
          </div>
          <div className="relative z-10 flex flex-col items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-surface-container-highest text-on-surface-variant flex items-center justify-center font-label-sm font-bold">4</div>
            <span className="font-caption text-on-surface-variant">Review</span>
          </div>
        </div>

        {/* Form Container */}
        <div className="bg-surface-container-lowest rounded-xl shadow-md p-stack-lg border border-outline-variant/50">
          <h2 className="font-section-heading text-section-heading text-on-background mb-stack-md">Basic Information</h2>
          
          <form className="space-y-stack-md flex flex-col" onSubmit={handleNext}>
            
            {/* Event Title */}
            <div>
              <label className="block font-label-sm text-on-surface mb-2">Event Title</label>
              <input 
                type="text" 
                required
                value={title}
                onChange={e => setTitle(e.target.value)}
                placeholder="E.g., Neon Nights Music Festival"
                className="w-full rounded-lg border border-outline-variant bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent py-3 px-4 font-body"
              />
            </div>

            {/* Description */}
            <div>
              <label className="block font-label-sm text-on-surface mb-2">Event Description</label>
              <textarea 
                required
                rows={4}
                value={description}
                onChange={e => setDescription(e.target.value)}
                placeholder="Describe what makes your event special..."
                className="w-full rounded-lg border border-outline-variant bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent py-3 px-4 font-body resize-y"
              ></textarea>
            </div>

            {/* Category Radio Chips */}
            <div>
              <label className="block font-label-sm text-on-surface mb-3">Category</label>
              <div className="flex flex-wrap gap-3">
                {["MUSIC", "SPORTS", "THEATER", "TECH", "FOOD"].map(cat => (
                  <label key={cat} className="cursor-pointer">
                    <input 
                      type="radio" 
                      name="category" 
                      value={cat} 
                      className="peer sr-only"
                      checked={category === cat}
                      onChange={() => setCategory(cat)}
                    />
                    <div className={`rounded-full border px-4 py-2 font-label-sm transition-colors ${category === cat ? 'bg-primary-fixed text-primary border-primary' : 'border-outline-variant text-on-surface-variant hover:border-outline'}`}>
                      {cat}
                    </div>
                  </label>
                ))}
              </div>
            </div>

            {/* Cover Image Upload (UI Only) */}
            <div>
              <label className="block font-label-sm text-on-surface mb-2">Cover Image</label>
              <div className="aspect-[16/9] w-full rounded-lg border-2 border-dashed border-outline-variant bg-surface-bright hover:bg-surface-container-low transition-colors flex flex-col items-center justify-center cursor-pointer group relative overflow-hidden">
                <span className="material-symbols-outlined text-[48px] text-outline group-hover:text-primary transition-colors mb-2">add_photo_alternate</span>
                <p className="font-label-sm text-on-surface-variant group-hover:text-primary transition-colors">Click to upload image</p>
                <p className="font-caption text-on-surface-variant mt-1">PNG, JPG or GIF up to 5MB</p>
              </div>
            </div>

            {/* Next Button */}
            <div className="pt-stack-md flex justify-end">
              <button type="submit" className="bg-primary text-on-primary rounded-full px-8 py-3 shadow-md hover:shadow-xl transition-all font-label-sm">
                Next Step
              </button>
            </div>
            
          </form>
        </div>

      </div>
    </main>
  );
}
