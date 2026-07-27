import { render, screen, fireEvent } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import TutorialButton from "../TutorialButton";

describe("TutorialButton", () => {
  it("클릭하면 onClick 콜백을 호출한다", () => {
    const onClick = vi.fn();
    render(<TutorialButton onClick={onClick} />);

    fireEvent.click(screen.getByRole("button", { name: /튜토리얼/ }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
