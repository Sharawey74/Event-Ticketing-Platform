import { useSyncExternalStore } from "react";
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

/**
 * True once `persist` has finished rehydrating from localStorage.
 *
 * Fix 26-hydration. On a cold load the first client render always sees `token === null`, because
 * rehydration happens after mount. Pages that redirected on that render bounced signed-in users to
 * /auth/login on any bookmark, shared link, or refresh.
 *
 * `persist.hasHydrated()` alone is not enough — it is a plain getter, so nothing re-renders when
 * hydration completes. Subscribing through `useSyncExternalStore` makes it reactive, and avoids
 * the `setState`-inside-`useEffect` pattern the lint rules reject.
 *
 * The third argument is the server snapshot: during SSR nothing has hydrated, so it is always
 * false, which keeps the server and first client render in agreement.
 */
export function useAuthHydrated(): boolean {
  return useSyncExternalStore(
    (onChange) => useAuthStore.persist.onFinishHydration(onChange),
    () => useAuthStore.persist.hasHydrated(),
    () => false,
  );
}
