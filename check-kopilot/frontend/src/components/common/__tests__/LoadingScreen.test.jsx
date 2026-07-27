import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import LoadingScreen from "../LoadingScreen";

describe("LoadingScreen", () => {
  it("Kopilot 브랜드와 안내 문구를 보여준다", () => {
    render(<LoadingScreen />);

    expect(screen.getByText("Kopilot")).toBeInTheDocument();
    expect(screen.getByText("금융 데이터를 준비하고 있어요")).toBeInTheDocument();
  });
});
