import { describe, it, expect } from "vitest";
import { tokenFromHash } from "../adminApi";

describe("tokenFromHash", () => {
  it("해시의 k 쿼리 파라미터를 토큰으로 추출한다", () => {
    expect(tokenFromHash("#/admin?k=kopilot-demo")).toBe("kopilot-demo");
  });

  it("쿼리가 없으면 빈 문자열을 반환한다", () => {
    expect(tokenFromHash("#/admin")).toBe("");
  });

  it("k 파라미터가 없으면 빈 문자열을 반환한다", () => {
    expect(tokenFromHash("#/admin?foo=bar")).toBe("");
  });
});
