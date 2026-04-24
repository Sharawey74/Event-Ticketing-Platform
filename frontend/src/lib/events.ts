import { api, assertApiUrlConfigured } from "@/lib/api";
import type { ApiResponse, PageResponse } from "@/types/api";
import type { EventFilters, EventResponse } from "@/types/event";

function toSearchParams(filters: EventFilters): Record<string, string | number> {
  const params: Record<string, string | number> = {};

  if (filters.q) {
    params.q = filters.q;
  }

  if (filters.city) {
    params.city = filters.city;
  }

  if (filters.categoryId) {
    params.category = filters.categoryId;
  }

  return params;
}

export async function fetchPublishedEvents(
  filters: EventFilters,
): Promise<PageResponse<EventResponse>> {
  assertApiUrlConfigured();

  const hasFilters = Boolean(filters.q || filters.city || filters.categoryId);

  if (hasFilters) {
    const { data } = await api.get<ApiResponse<PageResponse<EventResponse>>>(
      "/api/search/events",
      {
        params: toSearchParams(filters),
      },
    );

    return data.data;
  }

  const { data } = await api.get<ApiResponse<PageResponse<EventResponse>>>(
    "/api/events",
    {
      params: {
        status: "PUBLISHED",
      },
    },
  );

  return data.data;
}
