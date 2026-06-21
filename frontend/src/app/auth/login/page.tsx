"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuthStore } from "@/store/authStore";
import { api } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
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
      const { token, user } = res.data?.data || {};

      if (token && user) {
        setAuth(token, user.email, user.role);
        
        // Redirect based on role
        if (user.role === "ORGANIZER") {
          router.push("/organizer/events");
        } else {
          router.push("/dashboard/bookings");
        }
      } else {
        setError("Invalid response from server");
      }
    } catch (err: unknown) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setError((err as any)?.response?.data?.message || "Invalid email or password");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="min-h-screen relative flex items-center justify-center bg-surface px-4">
      
      {/* Absolute Logo Header */}
      <header className="w-full py-6 px-edge-padding absolute top-0 left-0 flex justify-center sm:justify-start items-center z-50">
        <Link href="/" className="text-section-heading font-section-heading text-primary tracking-tighter">
          VividPass
        </Link>
      </header>

      {/* Decorative Background */}
      <div className="absolute inset-0 opacity-40 pointer-events-none" style={{
        backgroundImage: 'radial-gradient(circle at 80% 20%, rgba(99,14,212,0.08) 0%, transparent 40%), radial-gradient(circle at 20% 80%, rgba(75,65,225,0.08) 0%, transparent 40%)'
      }} />

      {/* Login Card */}
      <div className="w-full max-w-[420px] bg-surface-container-lowest rounded-xl shadow-lg relative z-10 p-stack-lg border border-surface-variant overflow-hidden">
        
        {/* Top gradient accent bar */}
        <div className="absolute top-0 left-0 w-full h-1 bg-linear-to-r from-primary to-secondary"></div>
        
        <h1 className="font-section-heading text-section-heading text-on-surface text-center mb-stack-lg mt-2">Sign in to your account</h1>
        
        {error && (
          <div className="mb-4 p-3 bg-error-container text-on-error-container rounded-lg font-label-sm text-center">
            {error}
          </div>
        )}

        <form className="space-y-stack-md" onSubmit={handleLogin}>
          
          {/* Email Input */}
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]">mail</span>
            <input 
              type="email" 
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="Email address"
              className="w-full rounded-lg border border-outline-variant py-3 pl-10 pr-4 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body"
            />
          </div>

          {/* Password Input */}
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]">lock</span>
            <input 
              type={showPassword ? "text" : "password"} 
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              className="w-full rounded-lg border border-outline-variant py-3 pl-10 pr-10 bg-surface-bright focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent font-body"
            />
            <button 
              type="button" 
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant hover:text-primary transition-colors focus:outline-none"
            >
              <span className="material-symbols-outlined text-[20px]">
                {showPassword ? "visibility_off" : "visibility"}
              </span>
            </button>
          </div>

          {/* Forgot Password Link */}
          <div className="flex justify-end">
            <a href="#" className="text-primary font-label-sm hover:underline">Forgot password?</a>
          </div>

          {/* Submit Button */}
          <button 
            type="submit" 
            disabled={isLoading}
            className="w-full bg-linear-to-r from-primary to-secondary text-on-primary rounded-full py-3 font-bold hover:shadow-lg hover:-translate-y-[1px] transition-all disabled:opacity-70 disabled:hover:translate-y-0"
          >
            {isLoading ? "Signing in..." : "Sign In"}
          </button>
          
          {/* Register Link */}
          <div className="text-center mt-6 pt-2">
            <p className="font-body text-on-surface-variant">
              Don&apos;t have an account? <Link href="/auth/register" className="text-primary font-bold hover:underline">Register</Link>
            </p>
          </div>
        </form>
      </div>

    </main>
  );
}
