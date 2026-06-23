import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

type AuthState = {
  token: string | null;
  userEmail: string | null;
  userRole: string | null;
  setAuth: (token: string, userEmail: string, userRole: string) => void;
  clearAuth: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      userEmail: null,
      userRole: null,
      setAuth: (token, userEmail, userRole) => set({ token, userEmail, userRole }),
      clearAuth: () => set({ token: null, userEmail: null, userRole: null }),
    }),
    {
      name: "eventora-auth-storage",
      storage: createJSONStorage(() => localStorage),
    }
  )
);
