export type EventStatus =
  | "DRAFT"
  | "PUBLISHED"
  | "SALES_OPEN"
  | "SALES_CLOSED"
  | "COMPLETED"
  | "ARCHIVED";

export type EventResponse = {
  id: number;
  title: string;
  description: string | null;
  organizerId: number | null;
  categoryId: number | null;
  venueId: number | null;
  startDate: string;
  endDate: string;
  salesOpenDate: string | null;
  salesCloseDate: string | null;
  coverImageUrl: string | null;
  status: EventStatus;
  dynamicPricingEnabled: boolean;
  waitlistEnabled: boolean;
};

export type CategoryResponse = {
  id: number;
  name: string;
  description: string | null;
  iconUrl: string | null;
};

export type VenueResponse = {
  id: number;
  name: string;
  address: string;
  city: string;
  country: string;
  totalCapacity: number;
};

export type EventFilters = {
  q?: string;
  city?: string;
  categoryId?: number;
};
