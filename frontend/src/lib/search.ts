import type { EventFilters } from "@/types/event";

export type SearchFieldValues = {
  query: string;
  city: string;
  date: string;
  categoryId: string;
};

function trimValue(value: string): string {
  return value.trim();
}

export function buildSearchHref(values: SearchFieldValues): string {
  const params = new URLSearchParams();

  if (trimValue(values.query)) {
    params.set("q", trimValue(values.query));
  }

  if (trimValue(values.city)) {
    params.set("city", trimValue(values.city));
  }

  if (trimValue(values.date)) {
    params.set("date", trimValue(values.date));
  }

  if (trimValue(values.categoryId)) {
    params.set("categoryId", trimValue(values.categoryId));
  }

  const queryString = params.toString();
  return queryString ? `/search?${queryString}` : "/search";
}

export function buildEventFilters(values: SearchFieldValues): EventFilters {
  return {
    q: trimValue(values.query) || undefined,
    city: trimValue(values.city) || undefined,
    categoryId: trimValue(values.categoryId)
      ? Number(values.categoryId)
      : undefined,
  };
}
