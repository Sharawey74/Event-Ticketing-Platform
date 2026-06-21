import { api, assertApiUrlConfigured } from "@/lib/api";
import type { ApiResponse } from "@/types/api";
import type { EventDetailResponse } from "@/types/event";

export async function fetchEventById(eventId: number): Promise<EventDetailResponse> {
  assertApiUrlConfigured();

  const { data } = await api.get<ApiResponse<EventDetailResponse>>(
    `/api/events/${eventId}`,
  );

  return data.data;
}
