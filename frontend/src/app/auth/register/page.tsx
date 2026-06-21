"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

export default function RegisterPage() {
  const router = useRouter();
  const setAuth = useAuthStore((state) => state.setAuth);

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [uiRole, setUiRole] = useState<"ATTENDEE" | "ORGANIZER">("ATTENDEE"); // Decorational UI only
  
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  // Simple password strength calculation
  const getPasswordStrength = (pass: string) => {
    let score = 0;
    if (pass.length > 0) score += 1;
    if (pass.length >= 8) score += 1;
    if (/[A-Z]/.test(pass) && /[0-9]/.test(pass)) score += 1;
    if (/[^A-Za-z0-9]/.test(pass)) score += 1;
    return score;
  };

  const strength = getPasswordStrength(password);
  const strengthLabels = ["Weak", "Weak", "Medium", "Strong", "Very Strong"];

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");

    try {
      // E-001 ENFORCED: role field NOT sent to backend endpoint
      const registerPayload = { firstName, lastName, email, password };
      
      const res = await api.post("/api/v1/auth/register", registerPayload);
      const { token, user } = res.data?.data || {};

      if (token && user) {
        setAuth(token, user.email, user.role);
        
        // Redirect based on actual backend-assigned role
        if (user.role === "ORGANIZER") {
          router.push("/organizer/events");
        } else {
          router.push("/dashboard/bookings");
        }
      } else {
        // Fallback: if backend doesn't auto-login, redirect to login
        router.push("/auth/login?registered=true");
      }
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setError((err as any)?.response?.data?.message || "Failed to register account");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="min-h-screen relative flex items-center justify-center bg-surface px-4 py-20">
      
      {/* Absolute Logo Header */}
      <header className="w-full py-6 px-edge-padding absolute top-0 left-0 flex justify-center sm:justify-start items-center z-50">
        <Link href="/" className="text-section-heading font-section-heading text-primary tracking-tighter">
          VividPass
        </Link>
      </header>

      {/* Decorative Background Blob */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-primary-fixed-dim/30 rounded-full blur-[100px] pointer-events-none -z-10" />

      {/* Register Card */}
      <div className="w-full max-w-[420px] bg-surface-container-lowest rounded-xl shadow-lg border border-outline-variant/30 p-stack-lg z-10">
        
        <h1 className="font-section-heading text-section-heading text-on-surface mb-2">Create your account</h1>
        <p className="font-body text-body text-on-surface-variant mb-6">Join VividPass to unlock premium experiences.</p>
        
        {error ? (
          <div className="mb-4 p-3 bg-error-container text-on-error-container rounded-lg font-label-sm">
            {error}
          </div>
        ) : null}

        <form className="flex flex-col gap-5" onSubmit={handleRegister}>
          
          {/* Name Grid */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label htmlFor="firstName" className="block font-label-sm text-on-surface mb-1">First Name</label>
              <input 
                id="firstName"
                type="text" 
                required
                value={firstName}
                onChange={(e) => setFirstName(e.target.value)}
                className="w-full rounded-lg border border-outline-variant py-2.5 px-3 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body text-sm"
              />
            </div>
            <div>
              <label htmlFor="lastName" className="block font-label-sm text-on-surface mb-1">Last Name</label>
              <input 
                id="lastName"
                type="text" 
                required
                value={lastName}
                onChange={(e) => setLastName(e.target.value)}
                className="w-full rounded-lg border border-outline-variant py-2.5 px-3 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body text-sm"
              />
            </div>
          </div>

          {/* Email */}
          <div>
            <label htmlFor="email" className="block font-label-sm text-on-surface mb-1">Email Address</label>
            <input 
              id="email"
              type="email" 
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-lg border border-outline-variant py-2.5 px-3 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body text-sm"
            />
          </div>

          {/* Password */}
          <div>
            <label htmlFor="password" className="block font-label-sm text-on-surface mb-1">Password</label>
            <input 
              id="password"
              type="password" 
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-lg border border-outline-variant py-2.5 px-3 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body text-sm mb-2"
            />
            {/* Password Strength Indicator */}
            <div className="flex gap-1 h-1.5 w-full mb-1">
              {[1, 2, 3, 4].map(idx => (
                <div 
                  key={idx} 
                  className={`flex-1 rounded-full ${idx <= strength ? "bg-primary" : "bg-surface-variant"}`} 
                />
              ))}
            </div>
            <p className="font-caption text-on-surface-variant text-right">{strengthLabels[strength]}</p>
          </div>

          {/* Role Selection (UI ONLY) */}
          <div>
            <label className="block font-label-sm text-on-surface mb-3">I want to...</label>
            <div className="grid grid-cols-2 gap-4">
              
              {/* Attendee Toggle */}
              <label className="cursor-pointer">
                <input 
                  type="radio" 
                  name="role" 
                  value="ATTENDEE" 
                  className="peer sr-only" 
                  checked={uiRole === "ATTENDEE"}
                  onChange={() => setUiRole("ATTENDEE")}
                />
                <div className="border-2 border-outline-variant peer-checked:border-primary peer-checked:bg-primary-fixed/20 rounded-xl p-4 flex flex-col items-center gap-2 transition-all">
                  <span className={`material-symbols-outlined text-[28px] ${uiRole === "ATTENDEE" ? "text-primary" : "text-on-surface-variant"}`}>confirmation_number</span>
                  <span className={`font-label-sm ${uiRole === "ATTENDEE" ? "text-primary" : "text-on-surface"}`}>Attend Events</span>
                </div>
              </label>

              {/* Organizer Toggle */}
              <label className="cursor-pointer">
                <input 
                  type="radio" 
                  name="role" 
                  value="ORGANIZER" 
                  className="peer sr-only" 
                  checked={uiRole === "ORGANIZER"}
                  onChange={() => setUiRole("ORGANIZER")}
                />
                <div className="border-2 border-outline-variant peer-checked:border-primary peer-checked:bg-primary-fixed/20 rounded-xl p-4 flex flex-col items-center gap-2 transition-all">
                  <span className={`material-symbols-outlined text-[28px] ${uiRole === "ORGANIZER" ? "text-primary" : "text-on-surface-variant"}`}>storefront</span>
                  <span className={`font-label-sm ${uiRole === "ORGANIZER" ? "text-primary" : "text-on-surface"}`}>Host Events</span>
                </div>
              </label>
              
            </div>
          </div>

          {/* Submit */}
          <button 
            type="submit" 
            disabled={isLoading}
            className="btn-gradient w-full py-3 px-4 rounded-full text-on-primary font-label-md mt-6 shadow-md hover:shadow-lg hover:-translate-y-px transition-all duration-300 disabled:opacity-70 disabled:cursor-not-allowed disabled:hover:translate-y-0 disabled:hover:shadow-md flex justify-center items-center gap-2"
          >
            {isLoading ? "Creating Account..." : "Create Account"}
          </button>
        </form>

        <div className="text-center mt-6">
          <p className="font-body text-on-surface-variant">
            Already have an account? <Link href="/auth/login" className="text-primary font-bold hover:underline">Sign in</Link>
          </p>
        </div>
      </div>

    </main>
  );
}
