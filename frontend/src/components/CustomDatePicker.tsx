import React, { useState, useRef, useEffect, useId } from "react";

export interface CustomDatePickerProps {
  value: string; // ISO date "YYYY-MM-DD" or "YYYY-MM-DDTHH:mm"
  onChange: (value: string) => void;
  placeholder?: string;
  label?: string;
  id?: string;
  name?: string;
  disabled?: boolean;
  minDate?: string; // "YYYY-MM-DD"
  maxDate?: string; // "YYYY-MM-DD"
  includeTime?: boolean;
  className?: string;
  style?: React.CSSProperties;
  ariaLabel?: string;
  required?: boolean;
}

const MONTH_NAMES = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
];

const SHORT_MONTH_NAMES = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
];

const DAYS_OF_WEEK = ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"];

const padZero = (n: number): string => (n < 10 ? `0${n}` : `${n}`);

export const CustomDatePicker: React.FC<CustomDatePickerProps> = ({
  value,
  onChange,
  placeholder = "Select date...",
  label,
  id,
  name,
  disabled = false,
  minDate,
  maxDate,
  includeTime = false,
  className = "",
  style,
  ariaLabel,
  required = false,
}) => {
  const autoId = useId();
  const inputId = id || autoId;
  const containerRef = useRef<HTMLDivElement>(null);

  const [isOpen, setIsOpen] = useState(false);

  // Parse incoming value
  const parseValue = (val: string) => {
    if (!val) return null;
    const parts = val.split("T");
    const dateParts = parts[0].split("-").map(Number);
    if (dateParts.length !== 3 || isNaN(dateParts[0]) || isNaN(dateParts[1]) || isNaN(dateParts[2])) {
      return null;
    }
    const year = dateParts[0];
    const month = dateParts[1] - 1; // 0-indexed
    const day = dateParts[2];

    let hours = 0;
    let minutes = 0;
    if (parts[1]) {
      const timeParts = parts[1].split(":").map(Number);
      if (!isNaN(timeParts[0])) hours = timeParts[0];
      if (!isNaN(timeParts[1])) minutes = timeParts[1];
    }
    return { year, month, day, hours, minutes };
  };

  const parsed = parseValue(value);

  const today = new Date();
  const [viewYear, setViewYear] = useState<number>(parsed ? parsed.year : today.getFullYear());
  const [viewMonth, setViewMonth] = useState<number>(parsed ? parsed.month : today.getMonth());
  const [selectedHours, setSelectedHours] = useState<number>(parsed ? parsed.hours : 9);
  const [selectedMinutes, setSelectedMinutes] = useState<number>(parsed ? parsed.minutes : 0);

  // Sync view when opened or when external value changes
  useEffect(() => {
    if (parsed) {
      setViewYear(parsed.year);
      setViewMonth(parsed.month);
      setSelectedHours(parsed.hours);
      setSelectedMinutes(parsed.minutes);
    }
  }, [value]);

  // Click outside listener
  useEffect(() => {
    const handleOutsideClick = (e: MouseEvent | TouchEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    if (isOpen) {
      document.addEventListener("mousedown", handleOutsideClick);
      document.addEventListener("touchstart", handleOutsideClick);
    }
    return () => {
      document.removeEventListener("mousedown", handleOutsideClick);
      document.removeEventListener("touchstart", handleOutsideClick);
    };
  }, [isOpen]);

  // Keyboard navigation for popup
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && isOpen) {
        setIsOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen]);

  const handlePrevMonth = () => {
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear((prev) => prev - 1);
    } else {
      setViewMonth((prev) => prev - 1);
    }
  };

  const handleNextMonth = () => {
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear((prev) => prev + 1);
    } else {
      setViewMonth((prev) => prev + 1);
    }
  };

  const handleSelectDay = (day: number) => {
    const dateStr = `${viewYear}-${padZero(viewMonth + 1)}-${padZero(day)}`;
    if (includeTime) {
      const timeStr = `${padZero(selectedHours)}:${padZero(selectedMinutes)}`;
      onChange(`${dateStr}T${timeStr}`);
    } else {
      onChange(dateStr);
      setIsOpen(false);
    }
  };

  const handleTodayClick = () => {
    const now = new Date();
    const dateStr = `${now.getFullYear()}-${padZero(now.getMonth() + 1)}-${padZero(now.getDate())}`;
    if (includeTime) {
      const timeStr = `${padZero(now.getHours())}:${padZero(now.getMinutes())}`;
      onChange(`${dateStr}T${timeStr}`);
    } else {
      onChange(dateStr);
      setIsOpen(false);
    }
  };

  const handleClearClick = (e?: React.MouseEvent) => {
    if (e) e.stopPropagation();
    onChange("");
  };

  const handleTimeChange = (hours: number, minutes: number) => {
    setSelectedHours(hours);
    setSelectedMinutes(minutes);
    if (parsed) {
      const dateStr = `${parsed.year}-${padZero(parsed.month + 1)}-${padZero(parsed.day)}`;
      onChange(`${dateStr}T${padZero(hours)}:${padZero(minutes)}`);
    }
  };

  // Helper calculations for calendar grid
  const daysInMonth = new Date(viewYear, viewMonth + 1, 0).getDate();
  const firstDayOfWeek = new Date(viewYear, viewMonth, 1).getDay(); // 0 = Sun
  const daysInPrevMonth = new Date(viewYear, viewMonth, 0).getDate();

  // Year options for quick select (10 years back, 10 years forward)
  const currentYear = today.getFullYear();
  const yearOptions: number[] = [];
  for (let y = currentYear - 10; y <= currentYear + 10; y++) {
    yearOptions.push(y);
  }

  // Format label for display
  const formatDisplay = () => {
    if (!parsed) return "";
    const dateFormatted = `${parsed.day} ${SHORT_MONTH_NAMES[parsed.month]} ${parsed.year}`;
    if (includeTime) {
      return `${dateFormatted}, ${padZero(parsed.hours)}:${padZero(parsed.minutes)}`;
    }
    return dateFormatted;
  };

  return (
    <div
      ref={containerRef}
      className={`custom-datepicker-wrapper ${className}`}
      style={{ position: "relative", width: "100%", ...style }}
    >
      {label && (
        <label htmlFor={inputId} className="custom-datepicker-label">
          {label} {required && <span style={{ color: "#ef4444" }}>*</span>}
        </label>
      )}

      {/* Hidden input for HTML form submission */}
      {name && <input type="hidden" name={name} value={value} />}

      <button
        id={inputId}
        type="button"
        role="combobox"
        aria-haspopup="dialog"
        aria-expanded={isOpen}
        aria-label={ariaLabel || label || placeholder}
        disabled={disabled}
        onClick={() => !disabled && setIsOpen((prev) => !prev)}
        className={`custom-datepicker-trigger ${isOpen ? "is-open" : ""}`}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "0.6rem", overflow: "hidden" }}>
          {/* Calendar Icon */}
          <svg
            width="17"
            height="17"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{ color: value ? "var(--nss-navy, #1e3a8a)" : "#64748b", flexShrink: 0 }}
          >
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
            <line x1="16" y1="2" x2="16" y2="6"></line>
            <line x1="8" y1="2" x2="8" y2="6"></line>
            <line x1="3" y1="10" x2="21" y2="10"></line>
          </svg>

          <span className={`custom-datepicker-value ${!value ? "is-placeholder" : ""}`}>
            {value ? formatDisplay() : placeholder}
          </span>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: "0.35rem" }}>
          {value && !disabled && (
            <span
              role="button"
              tabIndex={0}
              onClick={handleClearClick}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.stopPropagation();
                  handleClearClick();
                }
              }}
              className="custom-datepicker-clear-btn"
              title="Clear date"
              aria-label="Clear selected date"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </span>
          )}

          <svg
            className={`custom-datepicker-arrow ${isOpen ? "rotate-up" : ""}`}
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </div>
      </button>

      {/* Dropdown Calendar Popup */}
      {isOpen && (
        <div
          role="dialog"
          aria-label="Calendar date selector"
          className="custom-datepicker-dropdown"
        >
          {/* Header Month / Year Selectors */}
          <div className="custom-datepicker-header">
            <button
              type="button"
              onClick={handlePrevMonth}
              className="custom-datepicker-nav-btn"
              title="Previous month"
              aria-label="Previous month"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <polyline points="15 18 9 12 15 6"></polyline>
              </svg>
            </button>

            <div className="custom-datepicker-selects">
              <select
                value={viewMonth}
                onChange={(e) => setViewMonth(Number(e.target.value))}
                className="custom-datepicker-select"
                aria-label="Select month"
              >
                {MONTH_NAMES.map((m, idx) => (
                  <option key={m} value={idx}>
                    {m}
                  </option>
                ))}
              </select>

              <select
                value={viewYear}
                onChange={(e) => setViewYear(Number(e.target.value))}
                className="custom-datepicker-select"
                aria-label="Select year"
              >
                {yearOptions.map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="button"
              onClick={handleNextMonth}
              className="custom-datepicker-nav-btn"
              title="Next month"
              aria-label="Next month"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </button>
          </div>

          {/* Weekday Names */}
          <div className="custom-datepicker-weekdays">
            {DAYS_OF_WEEK.map((d) => (
              <span key={d} className="custom-datepicker-weekday">
                {d}
              </span>
            ))}
          </div>

          {/* Days Grid */}
          <div className="custom-datepicker-days-grid">
            {/* Previous month leading days */}
            {Array.from({ length: firstDayOfWeek }).map((_, i) => {
              const dayNum = daysInPrevMonth - firstDayOfWeek + i + 1;
              return (
                <button
                  key={`prev-${dayNum}`}
                  type="button"
                  disabled
                  className="custom-datepicker-day is-outside"
                >
                  {dayNum}
                </button>
              );
            })}

            {/* Current month days */}
            {Array.from({ length: daysInMonth }).map((_, i) => {
              const dayNum = i + 1;
              const isSelected =
                Boolean(parsed) &&
                parsed?.year === viewYear &&
                parsed?.month === viewMonth &&
                parsed?.day === dayNum;

              const isToday =
                today.getFullYear() === viewYear &&
                today.getMonth() === viewMonth &&
                today.getDate() === dayNum;

              const currentDateIso = `${viewYear}-${padZero(viewMonth + 1)}-${padZero(dayNum)}`;
              const isBeforeMin = minDate ? currentDateIso < minDate : false;
              const isAfterMax = maxDate ? currentDateIso > maxDate : false;
              const isDisabled = isBeforeMin || isAfterMax;

              return (
                <button
                  key={`cur-${dayNum}`}
                  type="button"
                  disabled={isDisabled}
                  onClick={() => handleSelectDay(dayNum)}
                  className={`custom-datepicker-day ${isSelected ? "is-selected" : ""} ${
                    isToday ? "is-today" : ""
                  }`}
                >
                  {dayNum}
                </button>
              );
            })}
          </div>

          {/* Time Picker Section (if enabled) */}
          {includeTime && (
            <div className="custom-datepicker-time-section">
              <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <circle cx="12" cy="12" r="10"></circle>
                  <polyline points="12 6 12 12 16 14"></polyline>
                </svg>
                <span style={{ fontSize: "0.82rem", fontWeight: 600, color: "#334155" }}>Time:</span>
              </div>

              <div style={{ display: "flex", alignItems: "center", gap: "0.3rem" }}>
                <select
                  value={selectedHours}
                  onChange={(e) => handleTimeChange(Number(e.target.value), selectedMinutes)}
                  className="custom-datepicker-select"
                  style={{ padding: "0.25rem 0.5rem", fontSize: "0.82rem" }}
                  aria-label="Select hour"
                >
                  {Array.from({ length: 24 }).map((_, h) => (
                    <option key={h} value={h}>
                      {padZero(h)}
                    </option>
                  ))}
                </select>
                <span>:</span>
                <select
                  value={selectedMinutes}
                  onChange={(e) => handleTimeChange(selectedHours, Number(e.target.value))}
                  className="custom-datepicker-select"
                  style={{ padding: "0.25rem 0.5rem", fontSize: "0.82rem" }}
                  aria-label="Select minute"
                >
                  {Array.from({ length: 12 }).map((_, m) => {
                    const minVal = m * 5;
                    return (
                      <option key={minVal} value={minVal}>
                        {padZero(minVal)}
                      </option>
                    );
                  })}
                </select>
              </div>
            </div>
          )}

          {/* Footer Actions */}
          <div className="custom-datepicker-footer">
            <button
              type="button"
              onClick={handleTodayClick}
              className="custom-datepicker-footer-btn"
            >
              Today
            </button>

            {value && (
              <button
                type="button"
                onClick={() => handleClearClick()}
                className="custom-datepicker-footer-btn"
              >
                Clear
              </button>
            )}

            <button
              type="button"
              onClick={() => setIsOpen(false)}
              className="custom-datepicker-footer-btn is-primary"
            >
              Done
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
