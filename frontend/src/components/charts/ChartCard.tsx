"use client";

import { type ReactNode, useId, useState } from "react";

export interface TableColumn<Row> {
  header: string;
  cell: (row: Row) => ReactNode;
  numeric?: boolean;
}

/**
 * A chart in a card with a "Table" toggle that shows the same numbers, so no value is only
 * reachable by hovering.
 */
export function ChartCard<Row>({
  title,
  hint,
  wide,
  rows,
  rowKey,
  columns,
  empty,
  children,
}: {
  title: string;
  hint?: string;
  wide?: boolean;
  rows: Row[];
  rowKey: (row: Row) => string;
  columns: TableColumn<Row>[];
  /** Shown instead of the chart and table when there is nothing to plot. */
  empty?: string;
  children: ReactNode;
}) {
  const [showTable, setShowTable] = useState(false);
  const titleId = useId();

  return (
    <figure className={`card chart-card${wide ? " chart-wide" : ""}`} aria-labelledby={titleId}>
      <div className="card-header">
        <div>
          <h3 id={titleId} className="chart-title">
            {title}
          </h3>
          {hint && <p className="hint">{hint}</p>}
        </div>
        {!empty && (
          <button
            type="button"
            className="btn btn-sm"
            aria-pressed={showTable}
            onClick={() => setShowTable((v) => !v)}
          >
            {showTable ? "Chart" : "Table"}
          </button>
        )}
      </div>
      {empty ? (
        <p className="card-body muted">{empty}</p>
      ) : showTable ? (
        <div className="table-wrap chart-table">
          <table className="table">
            <thead>
              <tr>
                {columns.map((c) => (
                  <th key={c.header} scope="col" className={c.numeric ? "num" : undefined}>
                    {c.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={rowKey(row)}>
                  {columns.map((c) => (
                    <td key={c.header} className={c.numeric ? "num" : undefined}>
                      {c.cell(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="chart-body">{children}</div>
      )}
    </figure>
  );
}
