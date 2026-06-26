import { create } from "zustand";

type CartState = {
  count: number;
  setCount: (n: number) => void;
  reset: () => void;
};

export const useCartStore = create<CartState>()((set) => ({
  count: 0,
  setCount: (n) => set({ count: n }),
  reset: () => set({ count: 0 }),
}));
