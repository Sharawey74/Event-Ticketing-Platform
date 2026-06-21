import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

type AuthState = {
  token: string | null;
  userEmail: string | null;
  setAuth: (token: string, userEmail: string) => void;
  clearAuth: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      userEmail: null,
      setAuth: (token, userEmail) => set({ token, userEmail }),
      clearAuth: () => set({ token: null, userEmail: null }),
    }),
    {
      name: "vividpass-auth-storage",
      storage: createJSONStorage(() => sessionStorage),
    }
  )
);
