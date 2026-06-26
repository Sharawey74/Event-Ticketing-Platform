"use client";

import { useState, Suspense } from "react";
import Link from "next/link";
import { Eye, EyeOff, Mail, Lock } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

function SuccessBanner() {
  const searchParams = useSearchParams();
  if (searchParams.get("registered") !== "true") return null;
  return (
    <div className="mb-4 rounded-lg bg-emerald-50 px-4 py-3 text-sm text-emerald-800 text-center font-medium border border-emerald-200">
      Account created! Please sign in to continue.
    </div>
  );
}

export default function LoginPage() {
  const setAuth = useAuthStore((state) => state.setAuth);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    setError("");

    try {
      const res = await api.post("/api/v1/auth/login", { email, password });
      const { token, email: userEmail, role } = res.data?.data || {};

      if (token && userEmail) {
        setAuth(token, userEmail, role);
        document.cookie = `token=${token}; path=/; max-age=${60 * 60 * 24 * 7}; SameSite=Lax`;

        if (role === "ORGANIZER") {
          window.location.href = "/organizer/events";
        } else {
          window.location.href = "/dashboard/bookings";
        }
      } else {
        setError("Invalid response from server");
      }
    } catch (err: unknown) {
      const apiError = err as { response?: { data?: { message?: string } } };
      setError(apiError.response?.data?.message || "Invalid email or password");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="min-h-[calc(100vh-80px)] flex items-center justify-center bg-surface px-4">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl" />
        <div className="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-secondary/10 blur-3xl" />
      </div>

      <div className="relative w-full max-w-md">
        <div className="mb-8 text-center">
          <h1 className="text-2xl font-bold text-on-surface">Welcome back</h1>
          <p className="mt-1 text-sm text-on-surface-variant">
            Sign in to continue to Eventora.
          </p>
        </div>

        <div className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-8 shadow-lg">
          <h1 className="text-xl font-bold text-on-surface text-center mb-6">Sign in</h1>

          <Suspense>
            <SuccessBanner />
          </Suspense>

          {error ? (
            <div className="mb-4 rounded-lg bg-error-container px-4 py-3 text-sm text-on-error-container text-center">
              {error}
            </div>
          ) : null}

          <form className="space-y-4" onSubmit={handleLogin}>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="Email address"
                autoComplete="email"
                className="w-full rounded-xl border border-outline-variant bg-surface py-3 pl-10 pr-4 text-sm text-on-surface placeholder:text-outline focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30 transition-all"
              />
            </div>

            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-on-surface-variant" />
              <input
                type={showPassword ? "text" : "password"}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Password"
                autoComplete="current-password"
                className="w-full rounded-xl border border-outline-variant bg-surface py-3 pl-10 pr-11 text-sm text-on-surface placeholder:text-outline focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30 transition-all"
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

            <button
              type="submit"
              disabled={isLoading}
              className="btn-gradient mt-2 w-full rounded-full py-3 text-sm font-semibold text-white shadow-md hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200 disabled:opacity-60 disabled:cursor-not-allowed disabled:translate-y-0"
            >
              {isLoading ? "Signing in…" : "Sign In"}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-on-surface-variant">
            Don&apos;t have an account?{" "}
            <Link href="/auth/register" className="font-semibold text-primary hover:underline">
              Create one
            </Link>
          </p>
        </div>
      </div>
    </main>
  );
}
