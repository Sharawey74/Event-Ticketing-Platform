import { describe, expect, it } from "vitest";

import { buildEventFilters, buildSearchHref } from "./search";

describe("buildSearchHref", () => {
  it("creates a clean search url with all supported filters", () => {
    expect(
      buildSearchHref({
        query: "  jazz night  ",
        city: "  Boston ",
        date: "2026-04-30",
        categoryId: " 3 ",
      }),
    ).toBe("/search?q=jazz+night&city=Boston&date=2026-04-30&categoryId=3");
  });

  it("returns the base search route when all values are empty", () => {
    expect(
      buildSearchHref({
        query: "",
        city: "",
        date: "",
        categoryId: "",
      }),
    ).toBe("/search");
  });
});

describe("buildEventFilters", () => {
  it("maps form values to api filters", () => {
    expect(
      buildEventFilters({
        query: "  comedy  ",
        city: "  Chicago ",
        date: "2026-05-01",
        categoryId: "4",
      }),
    ).toEqual({
      q: "comedy",
      city: "Chicago",
      categoryId: 4,
    });
  });

  it("drops blank values", () => {
    expect(
      buildEventFilters({
        query: "",
        city: "",
        date: "",
        categoryId: "",
      }),
    ).toEqual({
      q: undefined,
      city: undefined,
      categoryId: undefined,
    });
  });
});
