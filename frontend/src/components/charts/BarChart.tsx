"use client";

import { type KeyboardEvent, useRef, useState } from "react";
import { textWidth, useChartWidth } from "./chartUtils";

export interface BarRow {
  key: string;
  label: string;
  value: number;
  /** Extra context for the tooltip, e.g. "4 resolved". */
  detail?: string;
}

const ROW = 30;
const BAR = 16;
const PAD_Y = 6;
const VALUE_ROOM = 64;

/** Bar path with a 4px rounded data end and a square end on the baseline. */
function barPath(x0: number, y0: number, length: number, thickness: number): string {
  const r = Math.min(4, length / 2, thickness / 2);
  const x1 = x0 + length;
  const y1 = y0 + thickness;
  return `M${x0},${y0}H${x1 - r}A${r},${r} 0 0 1 ${x1},${y0 + r}V${y1 - r}A${r},${r} 0 0 1 ${x1 - r},${y1}H${x0}Z`;
}

/**
 * One-series horizontal bars in a single hue with the value at each tip. Each whole row is the
 * hover target for its tooltip; arrow keys step through the rows.
 */
export function BarChart({
  rows,
  formatValue,
  color,
  ariaLabel,
}: {
  rows: BarRow[];
  formatValue: (value: number) => string;
  color: string;
  ariaLabel: string;
}) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const width = useChartWidth(wrapRef);
  const [active, setActive] = useState<number | null>(null);

  const labelW = Math.min(170, Math.max(90, width * 0.36));
  const plotW = Math.max(40, width - labelW - VALUE_ROOM);
  const height = rows.length * ROW + PAD_Y * 2;
  const max = Math.max(0, ...rows.map((r) => r.value));
  const length = (v: number) => (max === 0 || v <= 0 ? 0 : Math.max(2, (v / max) * plotW));

  // Labels that won't fit are shortened here; the full name is in the tooltip and the table.
  const fit = (label: string) => {
    const room = labelW - 12;
    if (textWidth(label) <= room) return label;
    let cut = label;
    while (cut.length > 1 && textWidth(`${cut}…`) > room) cut = cut.slice(0, -1);
    return `${cut.trimEnd()}…`;
  };

  const activeRow = active == null ? null : rows[active];
  const activeY = active == null ? 0 : PAD_Y + active * ROW;
  const focused = active ?? 0;
  const describe = (row: BarRow | undefined) =>
    row ? `${row.label}: ${formatValue(row.value)}${row.detail ? `, ${row.detail}` : ""}` : "";

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const n = rows.length;
    const step = { ArrowUp: -1, ArrowLeft: -1, ArrowDown: 1, ArrowRight: 1, Home: -n, End: n }[
      event.key
    ];
    if (step == null) return;
    event.preventDefault();
    setActive((current) => Math.min(n - 1, Math.max(0, (current ?? 0) + step)));
  };

  return (
    // Like the line chart, the plot is a slider over the bars so arrow keys show each tooltip.
    <div
      ref={wrapRef}
      className="chart-plot"
      role="slider"
      tabIndex={0}
      aria-label={ariaLabel}
      aria-valuemin={0}
      aria-valuemax={Math.max(0, rows.length - 1)}
      aria-valuenow={focused}
      aria-valuetext={describe(rows[focused])}
      onKeyDown={onKeyDown}
      onFocus={() => setActive((current) => current ?? 0)}
      onBlur={() => setActive(null)}
    >
      <svg className="chart-svg" viewBox={`0 0 ${width} ${height}`} width="100%" aria-hidden="true">
        <line
          className="chart-baseline"
          x1={labelW}
          x2={labelW}
          y1={PAD_Y - 2}
          y2={height - PAD_Y + 2}
        />
        {rows.map((row, i) => {
          const top = PAD_Y + i * ROW;
          const barLength = length(row.value);
          return (
            <g
              key={row.key}
              className={`chart-bar-row${active === i ? " is-active" : ""}`}
              onPointerEnter={() => setActive(i)}
              onPointerDown={() => setActive(i)}
              onPointerLeave={() => setActive(null)}
            >
              <rect className="chart-hit" x={0} y={top} width={width} height={ROW} />
              <text
                className="chart-category"
                x={labelW - 10}
                y={top + ROW / 2 + 4}
                textAnchor="end"
              >
                {fit(row.label)}
              </text>
              {barLength > 0 && (
                <path
                  className="chart-bar"
                  d={barPath(labelW, top + (ROW - BAR) / 2, barLength, BAR)}
                  fill={color}
                />
              )}
              <text className="chart-value" x={labelW + barLength + 6} y={top + ROW / 2 + 4}>
                {formatValue(row.value)}
              </text>
            </g>
          );
        })}
      </svg>
      {activeRow && (
        <div
          className="chart-tooltip"
          style={{
            left: `${((labelW + length(activeRow.value) / 2) / width) * 100}%`,
            top: activeY + ROW,
            transform: "translateX(-50%)",
          }}
          aria-hidden="true"
        >
          <p className="tooltip-title">{activeRow.label}</p>
          <p className="tooltip-row">
            <strong>{formatValue(activeRow.value)}</strong>
            {activeRow.detail && <span className="muted">{activeRow.detail}</span>}
          </p>
        </div>
      )}
    </div>
  );
}
