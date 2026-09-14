from __future__ import annotations

import pandas as pd


def summarize_service_hours(rows: list[dict[str, object]]) -> pd.DataFrame:
    frame = pd.DataFrame(rows)
    if frame.empty:
        return pd.DataFrame(columns=["volunteer_id", "hours"])

    required = {"volunteer_id", "hours"}
    missing = required.difference(frame.columns)
    if missing:
        raise ValueError(f"Missing required columns: {sorted(missing)}")

    frame["hours"] = pd.to_numeric(frame["hours"], errors="raise")
    if (frame["hours"] < 0).any():
        raise ValueError("Service hours cannot be negative.")

    return (
        frame.groupby("volunteer_id", as_index=False)["hours"]
        .sum()
        .sort_values("volunteer_id")
        .reset_index(drop=True)
    )
