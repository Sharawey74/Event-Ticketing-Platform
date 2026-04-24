import { api, assertApiUrlConfigured } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { EventResponse } from "@/types/event";

export async function fetchEventById(eventId: number): Promise<EventResponse> {
  assertApiUrlConfigured();

  const { data } = await api.get<ApiResponse<EventResponse>>(
    `/api/events/${eventId}`,
  );

  return data.data;
}
