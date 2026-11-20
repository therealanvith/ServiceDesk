"use client";

import { type RefObject, useEffect, useState } from "react";

/**
 * Tracks an element's rendered width so a chart can draw its viewBox at real pixels: text stays
 * 11-12px on a phone instead of shrinking with the SVG.
 */
export function useChartWidth(ref: RefObject<HTMLElement | null>, fallback = 640): number {
  const [width, setWidth] = useState(fallback);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const measure = () => setWidth(Math.max(240, Math.round(el.clientWidth)));
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, [ref]);
  return width;
}

/** Clean axis ticks from 0: steps of 1, 2 or 5 times a power of ten, about four of them. */
export function niceTicks(max: number, count = 4, minStep = 0): number[] {
  if (max <= 0) return [0, 1];
  const raw = max / count;
  const power = 10 ** Math.floor(Math.log10(raw));
  const step = Math.max(minStep, [1, 2, 5, 10].map((m) => m * power).find((s) => s >= raw) ?? raw);
  const steps = Math.ceil(max / step - 1e-9);
  return Array.from({ length: steps + 1 }, (_, i) => Number((i * step).toFixed(6)));
}

/** Compact tick text: 1,200 -> 1.2K. */
export function compact(value: number): string {
  if (Math.abs(value) >= 1000) return `${Number((value / 1000).toFixed(1))}K`;
  return String(Number(value.toFixed(1)));
}

/** "2026-11-20" (a UTC day) -> "20 Nov". */
export function shortDate(iso: string): string {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  });
}

/** Rough text width for the system sans, used to keep labels from colliding or overflowing. */
export function textWidth(text: string, fontSize = 12): number {
  return text.length * fontSize * 0.58;
}
