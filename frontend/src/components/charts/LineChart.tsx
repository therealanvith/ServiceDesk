"use client";

import { type KeyboardEvent, type PointerEvent, useRef, useState } from "react";
import { compact, niceTicks, textWidth, useChartWidth } from "./chartUtils";

export interface LineSeries {
  key: string;
  label: string;
  /** A CSS colour, normally one of the --chart-* tokens. */
  color: string;
  /** One value per x position; null leaves a gap (nothing to measure that day or week). */
  values: (number | null)[];
}

const HEIGHT = 220;
const MARGIN = { top: 14, bottom: 28, left: 36 };

/**
 * Lines over a shared x axis with a crosshair that snaps to the nearest position and lists every
 * series there. Two or more series get a legend and, where they don't collide, direct end labels.
 */
export function LineChart({
  labels,
  tooltipLabels = labels,
  series,
  formatValue,
  yMax,
  minStep = 0,
  markers = false,
  ariaLabel,
}: {
  /** Axis text for each x position. */
  labels: string[];
  /** Fuller text for the tooltip header, e.g. "Week of 16 Nov". */
  tooltipLabels?: string[];
  series: LineSeries[];
  formatValue: (value: number) => string;
  /** Fixes the top of the scale, e.g. 100 for percentages. */
  yMax?: number;
  /** Smallest tick step, e.g. 1 for whole-ticket counts. */
  minStep?: number;
  /** Dot every point; for sparse series where single points would otherwise vanish. */
  markers?: boolean;
  ariaLabel: string;
}) {
  const wrapRef = useRef<HTMLDivElement>(null);
  const width = useChartWidth(wrapRef);
  const [active, setActive] = useState<number | null>(null);

  const n = labels.length;
  const lastValue = (s: LineSeries) => {
    for (let i = s.values.length - 1; i >= 0; i--) {
      const v = s.values[i];
      if (v != null) return { i, v };
    }
    return null;
  };
  const endLabelText = (s: LineSeries) => {
    const end = lastValue(s);
    if (!end) return "";
    return series.length > 1 ? `${s.label} ${formatValue(end.v)}` : formatValue(end.v);
  };
  const right = Math.max(16, ...series.map((s) => textWidth(endLabelText(s)) + 14));

  const plotW = Math.max(40, width - MARGIN.left - right);
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom;
  const dataMax = Math.max(0, ...series.flatMap((s) => s.values.map((v) => v ?? 0)));
  const ticks = yMax != null ? niceTicks(yMax, 4, minStep) : niceTicks(dataMax, 4, minStep);
  const top = ticks[ticks.length - 1] ?? 1;

  const x = (i: number) => MARGIN.left + (n <= 1 ? plotW / 2 : (i * plotW) / (n - 1));
  const y = (v: number) => MARGIN.top + plotH - (v / top) * plotH;

  const path = (values: (number | null)[]) => {
    let d = "";
    let pen = false;
    values.forEach((v, i) => {
      if (v == null) {
        pen = false;
        return;
      }
      d += `${pen ? "L" : "M"}${x(i).toFixed(1)},${y(v).toFixed(1)}`;
      pen = true;
    });
    return d;
  };

  // Show about one x label per 80px, always including the last.
  const every = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(plotW / 80))));
  const xTicks = labels.flatMap((label, i) => ((n - 1 - i) % every === 0 ? [{ i, label }] : []));

  // End labels sit beside each line's last point; if two land on top of each other the legend and
  // tooltip carry identity instead.
  const ends = series.flatMap((s) => {
    const end = lastValue(s);
    return end ? [{ s, ...end, ty: y(end.v) }] : [];
  });
  const sortedY = ends.map((e) => e.ty).sort((a, b) => a - b);
  const endsCollide = sortedY.some((v, i) => i > 0 && v - (sortedY[i - 1] ?? v) < 14);
  const showEnds = series.length > 1 ? !endsCollide : ends.length > 0;

  const indexAt = (event: PointerEvent<SVGRectElement>) => {
    const box = event.currentTarget.ownerSVGElement?.getBoundingClientRect();
    if (!box || box.width === 0) return null;
    const px = ((event.clientX - box.left) * width) / box.width;
    if (n <= 1) return 0;
    return Math.min(n - 1, Math.max(0, Math.round(((px - MARGIN.left) * (n - 1)) / plotW)));
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const step = { ArrowLeft: -1, ArrowRight: 1, Home: -n, End: n }[event.key];
    if (step == null) return;
    event.preventDefault();
    setActive((current) => Math.min(n - 1, Math.max(0, (current ?? n - 1) + step)));
  };

  const focused = active ?? Math.max(0, n - 1);
  const describe = (i: number) =>
    `${tooltipLabels[i] ?? ""}: ${series
      .map((s) => {
        const v = s.values[i];
        return `${s.label} ${v == null ? "no data" : formatValue(v)}`;
      })
      .join(", ")}`;

  const activeX = active == null ? 0 : x(active);
  const flip = activeX > width / 2;

  return (
    <div className="chart">
      {series.length > 1 && (
        <ul className="chart-legend">
          {series.map((s) => (
            <li key={s.key}>
              <span className="legend-line" style={{ background: s.color }} aria-hidden="true" />
              {s.label}
            </li>
          ))}
        </ul>
      )}
      {/* The plot is a slider over x positions: arrow keys move the crosshair. */}
      <div
        ref={wrapRef}
        className="chart-plot"
        role="slider"
        tabIndex={0}
        aria-label={ariaLabel}
        aria-valuemin={0}
        aria-valuemax={Math.max(0, n - 1)}
        aria-valuenow={focused}
        aria-valuetext={describe(focused)}
        onKeyDown={onKeyDown}
        onFocus={() => setActive((current) => current ?? n - 1)}
        onBlur={() => setActive(null)}
      >
        <svg
          className="chart-svg"
          viewBox={`0 0 ${width} ${HEIGHT}`}
          width="100%"
          aria-hidden="true"
        >
          {ticks.map((t) => (
            <g key={t}>
              <line
                className={t === 0 ? "chart-baseline" : "chart-gridline"}
                x1={MARGIN.left}
                x2={MARGIN.left + plotW}
                y1={y(t)}
                y2={y(t)}
              />
              <text className="chart-tick" x={MARGIN.left - 6} y={y(t) + 4} textAnchor="end">
                {yMax === 100 ? `${t}%` : compact(t)}
              </text>
            </g>
          ))}
          {xTicks.map(({ i, label }) => (
            <text
              key={i}
              className="chart-tick"
              x={x(i)}
              y={HEIGHT - 8}
              textAnchor={n > 1 && i === n - 1 ? "end" : i === 0 && n > 1 ? "start" : "middle"}
            >
              {label}
            </text>
          ))}

          {series.map((s) => (
            <path
              key={s.key}
              className="chart-line"
              d={path(s.values)}
              stroke={s.color}
              fill="none"
            />
          ))}
          {markers &&
            series.map((s) =>
              s.values.map((v, i) =>
                v == null ? null : (
                  <circle
                    key={`${s.key}-${i}`}
                    className="chart-dot"
                    cx={x(i)}
                    cy={y(v)}
                    r={4}
                    fill={s.color}
                  />
                )
              )
            )}
          {ends.map((e) => (
            <g key={e.s.key}>
              {!markers && (
                <circle className="chart-dot" cx={x(e.i)} cy={e.ty} r={4} fill={e.s.color} />
              )}
              {showEnds && (
                <text className="chart-end-label" x={x(e.i) + 8} y={e.ty + 4}>
                  {endLabelText(e.s)}
                </text>
              )}
            </g>
          ))}

          {active != null && (
            <g>
              <line
                className="chart-crosshair"
                x1={activeX}
                x2={activeX}
                y1={MARGIN.top}
                y2={MARGIN.top + plotH}
              />
              {series.map((s) => {
                const v = s.values[active];
                return v == null ? null : (
                  <circle
                    key={s.key}
                    className="chart-dot"
                    cx={activeX}
                    cy={y(v)}
                    r={4.5}
                    fill={s.color}
                  />
                );
              })}
            </g>
          )}

          <rect
            className="chart-hit"
            x={MARGIN.left - 8}
            y={0}
            width={plotW + 16}
            height={HEIGHT}
            onPointerMove={(e) => setActive(indexAt(e))}
            onPointerDown={(e) => setActive(indexAt(e))}
            onPointerLeave={() => setActive(null)}
          />
        </svg>

        {active != null && (
          <div
            className="chart-tooltip"
            style={{
              left: `${(activeX / width) * 100}%`,
              transform: flip ? "translateX(calc(-100% - 12px))" : "translateX(12px)",
            }}
            aria-hidden="true"
          >
            <p className="tooltip-title">{tooltipLabels[active]}</p>
            {series.map((s) => {
              const v = s.values[active];
              return (
                <p key={s.key} className="tooltip-row">
                  <span
                    className="legend-line"
                    style={{ background: s.color }}
                    aria-hidden="true"
                  />
                  <strong>{v == null ? "—" : formatValue(v)}</strong>
                  <span className="muted">{s.label}</span>
                </p>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
