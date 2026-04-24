import { create } from "zustand";

type AuthState = {
  token: string | null;
  userEmail: string | null;
  setAuth: (token: string, userEmail: string) => void;
  clearAuth: () => void;
};

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  userEmail: null,
  setAuth: (token, userEmail) => set({ token, userEmail }),
  clearAuth: () => set({ token: null, userEmail: null }),
}));
