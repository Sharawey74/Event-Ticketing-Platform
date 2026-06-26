import { create } from "zustand";

type ReservationState = {
  bookingId: number | null;
  expiresAt: string | null;
  returnPath: string | null;
  eventTitle: string | null;
  tierName: string | null;
  quantity: number;
  unitPrice: number;
  set: (
    bookingId: number,
    expiresAt: string,
    returnPath: string,
    eventTitle: string,
    tierName: string,
    quantity: number,
    unitPrice: number,
  ) => void;
  clear: () => void;
};

export const useReservationStore = create<ReservationState>()((set) => ({
  bookingId: null,
  expiresAt: null,
  returnPath: null,
  eventTitle: null,
  tierName: null,
  quantity: 1,
  unitPrice: 0,
  set: (bookingId, expiresAt, returnPath, eventTitle, tierName, quantity, unitPrice) =>
    set({ bookingId, expiresAt, returnPath, eventTitle, tierName, quantity, unitPrice }),
  clear: () =>
    set({
      bookingId: null,
      expiresAt: null,
      returnPath: null,
      eventTitle: null,
      tierName: null,
      quantity: 1,
      unitPrice: 0,
    }),
}));
