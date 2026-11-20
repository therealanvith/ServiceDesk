import type { CSSProperties } from "react";

/** A grey placeholder bar while content loads. Purely decorative: wrap it in a labelled status. */
export function Skeleton({
  width = "100%",
  height = 12,
}: { width?: CSSProperties["width"]; height?: number }) {
  return <span className="skeleton" style={{ width, height }} aria-hidden="true" />;
}

/** A labelled loading state that screen readers announce once, shaped like what is coming. */
export function SkeletonBlock({
  label,
  variant = "list",
  rows = 6,
}: {
  label: string;
  variant?: "list" | "detail" | "board";
  rows?: number;
}) {
  return (
    <output className={`skeleton-block skeleton-${variant}`} aria-busy="true">
      <span className="sr-only">{label}</span>
      {variant === "list" &&
        Array.from({ length: rows }, (_, i) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: static placeholders never reorder
          <span key={i} className="skeleton-row">
            <Skeleton width={64} />
            <Skeleton width={`${40 + ((i * 17) % 35)}%`} />
            <Skeleton width={72} />
          </span>
        ))}
      {variant === "detail" && (
        <>
          <Skeleton width={120} />
          <Skeleton width="55%" height={22} />
          <span className="skeleton-detail-grid">
            <span className="card card-body skeleton-stack">
              <Skeleton width="90%" />
              <Skeleton width="75%" />
              <Skeleton width="82%" />
              <Skeleton width="40%" />
            </span>
            <span className="card card-body skeleton-stack">
              <Skeleton height={32} />
              <Skeleton width="70%" />
            </span>
          </span>
        </>
      )}
      {variant === "board" &&
        Array.from({ length: 5 }, (_, i) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: static placeholders never reorder
          <span key={i} className="skeleton-column">
            <Skeleton width="50%" />
            {Array.from({ length: 3 - (i % 2) }, (_, j) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: static placeholders never reorder
              <span key={j} className="skeleton-card">
                <Skeleton width={56} />
                <Skeleton width="85%" />
                <Skeleton width="45%" />
              </span>
            ))}
          </span>
        ))}
    </output>
  );
}
