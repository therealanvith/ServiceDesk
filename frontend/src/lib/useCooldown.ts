"use client";

import { ApiError } from "@/lib/apiClient";
import { useCallback, useEffect, useState } from "react";

/**
 * Counts down after the server says "too many requests", so a button can stay disabled and say
 * when it will work again. `trigger(err)` starts the countdown when `err` is a 429.
 */
export function useCooldown() {
  const [secondsLeft, setSecondsLeft] = useState(0);

  useEffect(() => {
    if (secondsLeft <= 0) return;
    const timer = window.setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [secondsLeft]);

  const trigger = useCallback((err: unknown) => {
    if (err instanceof ApiError && err.status === 429) {
      setSecondsLeft(err.retryAfterSeconds ?? 30);
      return true;
    }
    return false;
  }, []);

  return { secondsLeft, coolingDown: secondsLeft > 0, trigger };
}
