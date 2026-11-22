"use client";

import { type CategorySetting, api } from "@/lib/apiClient";
import { CATEGORY_LABELS } from "@/lib/labels";
import { useEffect, useState } from "react";

/** Which incident categories reporters can choose. Existing tickets keep a switched-off one. */
export function CategorySettings({ onError }: { onError: (message: string | null) => void }) {
  const [items, setItems] = useState<CategorySetting[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    api
      .listCategories()
      .then(setItems)
      .catch((err: Error) => onError(err.message));
  }, [onError]);

  const toggle = async (item: CategorySetting) => {
    setBusy(item.category);
    onError(null);
    try {
      const updated = await api.setCategoryActive(item.category, !item.active);
      setItems((list) => list?.map((c) => (c.category === updated.category ? updated : c)) ?? null);
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : "Couldn’t update the category");
    } finally {
      setBusy(null);
    }
  };

  if (!items) return <output className="card empty">Loading categories…</output>;

  return (
    <div className="card table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Category</th>
            <th scope="col">Offered to reporters</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.category}>
              <td>{CATEGORY_LABELS[item.category]}</td>
              <td>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={item.active}
                    disabled={busy === item.category}
                    onChange={() => toggle(item)}
                    aria-label={`${CATEGORY_LABELS[item.category]} is offered to reporters`}
                  />
                  {item.active ? "Yes" : "No"}
                </label>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
