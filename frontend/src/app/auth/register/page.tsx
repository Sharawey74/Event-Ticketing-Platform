"use client";

import { useState } from "react";
import Link from "next/link";
import { Eye, EyeOff } from "lucide-react";
import { api } from "@/lib/api";

export default function RegisterPage() {
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [role, setRole] = useState<"USER" | "ORGANIZER">("USER");
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

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
  const strengthColors = ["bg-error", "bg-error", "bg-yellow-500", "bg-emerald-500", "bg-emerald-600"];

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");
    try {
      await api.post("/api/v1/auth/register", { firstName, lastName, email, password, role });
      window.location.href = "/auth/login?registered=true";
    } catch (err: unknown) {
      const apiError = err as { response?: { data?: { message?: string } } };
      setError(apiError.response?.data?.message || "Failed to register account");
    } finally {
      setIsLoading(false);
    }
  };

  const inputClass =
    "w-full rounded-xl border border-outline-variant bg-surface py-3 px-4 text-sm text-on-surface placeholder:text-outline focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30 transition-all [&:-webkit-autofill]:shadow-[inset_0_0_0_1000px_#f8f9ff]";

  return (
    <main className="min-h-[calc(100vh-80px)] flex items-center justify-center bg-surface px-4 py-8">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl" />
        <div className="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-secondary/10 blur-3xl" />
      </div>

      <div className="relative w-full max-w-lg">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-on-surface">Create your account</h1>
          <p className="mt-1 text-sm text-on-surface-variant">Join Eventora to unlock premium experiences.</p>
        </div>

        <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-8 shadow-lg">
          {error && (
            <div className="mb-4 rounded-lg bg-error-container px-4 py-3 text-sm text-on-error-container text-center">
              {error}
            </div>
          )}

          <form className="flex flex-col gap-5" onSubmit={handleRegister}>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="firstName" className="block text-sm font-medium text-on-surface mb-1.5">
                  First Name
                </label>
                <input
                  id="firstName"
                  type="text"
                  required
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  autoComplete="given-name"
                  placeholder="First name"
                  className={inputClass}
                />
              </div>
              <div>
                <label htmlFor="lastName" className="block text-sm font-medium text-on-surface mb-1.5">
                  Last Name
                </label>
                <input
                  id="lastName"
                  type="text"
                  required
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  autoComplete="family-name"
                  placeholder="Last name"
                  className={inputClass}
                />
              </div>
            </div>

            <div>
              <label htmlFor="email" className="block text-sm font-medium text-on-surface mb-1.5">
                Email Address
              </label>
              <input
                id="email"
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                placeholder="you@example.com"
                className={inputClass}
              />
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-on-surface mb-1.5">
                Password
              </label>
              <div className="relative">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password"
                  placeholder="At least 8 characters"
                  className={`${inputClass} pr-11`}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-primary transition-colors focus:outline-none"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>

              {password.length > 0 && (
                <div className="mt-2.5">
                  <div className="flex items-center gap-2 mb-2">
                    <div className="flex gap-1 h-1.5 flex-1">
                      {[1, 2, 3, 4].map((idx) => (
                        <div
                          key={idx}
                          className={`flex-1 rounded-full transition-colors ${
                            idx <= strength ? strengthColors[strength] : "bg-surface-variant"
                          }`}
                        />
                      ))}
                    </div>
                    <span className="text-xs text-on-surface-variant shrink-0">{strengthLabels[strength]}</span>
                  </div>
                  <ul className="space-y-1">
                    <li
                      className={`flex items-center gap-1.5 text-sm ${
                        password.length >= 8 ? "text-emerald-600" : "text-on-surface-variant"
                      }`}
                    >
                      <span className="w-3 text-center">{password.length >= 8 ? "✓" : "•"}</span>
                      At least 8 characters
                    </li>
                    <li
                      className={`flex items-center gap-1.5 text-sm ${
                        /[A-Z]/.test(password) ? "text-emerald-600" : "text-on-surface-variant"
                      }`}
                    >
                      <span className="w-3 text-center">{/[A-Z]/.test(password) ? "✓" : "•"}</span>
                      One uppercase letter
                    </li>
                    <li
                      className={`flex items-center gap-1.5 text-sm ${
                        /[0-9]/.test(password) ? "text-emerald-600" : "text-on-surface-variant"
                      }`}
                    >
                      <span className="w-3 text-center">{/[0-9]/.test(password) ? "✓" : "•"}</span>
                      One number
                    </li>
                  </ul>
                </div>
              )}
            </div>

            <div>
              <label className="block text-sm font-medium text-on-surface mb-3">I want to...</label>
              <div className="grid grid-cols-2 gap-4">
                <label className="cursor-pointer">
                  <input
                    type="radio"
                    name="role"
                    value="USER"
                    className="peer sr-only"
                    checked={role === "USER"}
                    onChange={() => setRole("USER")}
                  />
                  <div className="border-2 border-outline-variant peer-checked:border-primary peer-checked:ring-2 peer-checked:ring-primary/20 peer-checked:bg-primary-fixed/20 rounded-xl p-5 flex flex-col items-center gap-2 transition-all">
                    <span
                      className={`material-symbols-outlined text-[36px] ${
                        role === "USER" ? "text-primary" : "text-on-surface-variant"
                      }`}
                    >
                      confirmation_number
                    </span>
                    <span className={`text-sm font-medium ${role === "USER" ? "text-primary" : "text-on-surface"}`}>
                      Attend Events
                    </span>
                  </div>
                </label>

                <label className="cursor-pointer">
                  <input
                    type="radio"
                    name="role"
                    value="ORGANIZER"
                    className="peer sr-only"
                    checked={role === "ORGANIZER"}
                    onChange={() => setRole("ORGANIZER")}
                  />
                  <div className="border-2 border-outline-variant peer-checked:border-primary peer-checked:ring-2 peer-checked:ring-primary/20 peer-checked:bg-primary-fixed/20 rounded-xl p-5 flex flex-col items-center gap-2 transition-all">
                    <span
                      className={`material-symbols-outlined text-[36px] ${
                        role === "ORGANIZER" ? "text-primary" : "text-on-surface-variant"
                      }`}
                    >
                      storefront
                    </span>
                    <span
                      className={`text-sm font-medium ${role === "ORGANIZER" ? "text-primary" : "text-on-surface"}`}
                    >
                      Host Events
                    </span>
                  </div>
                </label>
              </div>
              {role === "ORGANIZER" && (
                <p className="mt-2 text-sm text-on-surface-variant text-center">
                  You&apos;ll be able to create and manage events after signing in.
                </p>
              )}
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="btn-gradient mt-2 w-full rounded-full py-3 text-sm font-semibold text-white shadow-md hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 disabled:opacity-60 disabled:cursor-not-allowed disabled:translate-y-0 flex justify-center items-center gap-2"
            >
              {isLoading ? "Creating Account…" : "Create Account"}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-on-surface-variant">
            Already have an account?{" "}
            <Link href="/auth/login" className="font-semibold text-primary hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </main>
  );
}
