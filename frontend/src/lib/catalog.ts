import { api, assertApiUrlConfigured } from "@/lib/api";
import type { ApiResponse, PageResponse } from "@/types/api";
import type { CategoryResponse, VenueResponse } from "@/types/event";

export async function fetchCategories(): Promise<CategoryResponse[]> {
  assertApiUrlConfigured();

  const { data } = await api.get<ApiResponse<PageResponse<CategoryResponse>>>(
    "/api/categories",
    {
      params: { page: 0, size: 20 },
    },
  );

  return data.data.content;
}

export async function fetchVenues(): Promise<VenueResponse[]> {
  assertApiUrlConfigured();

  const { data } = await api.get<ApiResponse<PageResponse<VenueResponse>>>(
    "/api/venues",
    {
      params: { page: 0, size: 200 },
    },
  );

  return data.data.content;
}
