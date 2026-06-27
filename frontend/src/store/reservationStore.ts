import { create } from "zustand";

type ReservationState = {
  bookingId: number | null;
  eventId: number | null;
  expiresAt: string | null;
  returnPath: string | null;
  eventTitle: string | null;
  tierName: string | null;
  quantity: number;
  unitPrice: number;
  totalAmount: number;
  skipBeforeUnload: boolean;
  set: (
    bookingId: number,
    expiresAt: string,
    returnPath: string,
    eventTitle: string,
    tierName: string,
    quantity: number,
    unitPrice: number,
    totalAmount: number,
    eventId: number,
  ) => void;
  clear: () => void;
  setSkipBeforeUnload: (v: boolean) => void;
};

export const useReservationStore = create<ReservationState>()((set) => ({
  bookingId: null,
  eventId: null,
  expiresAt: null,
  returnPath: null,
  eventTitle: null,
  tierName: null,
  quantity: 1,
  unitPrice: 0,
  totalAmount: 0,
  skipBeforeUnload: false,
  set: (bookingId, expiresAt, returnPath, eventTitle, tierName, quantity, unitPrice, totalAmount, eventId) =>
    set({ bookingId, eventId, expiresAt, returnPath, eventTitle, tierName, quantity, unitPrice, totalAmount }),
  clear: () =>
    set({
      bookingId: null,
      eventId: null,
      expiresAt: null,
      returnPath: null,
      eventTitle: null,
      tierName: null,
      quantity: 1,
      unitPrice: 0,
      totalAmount: 0,
      skipBeforeUnload: false,
    }),
  setSkipBeforeUnload: (v: boolean) => set({ skipBeforeUnload: v }),
}));
