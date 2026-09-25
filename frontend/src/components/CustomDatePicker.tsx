import React, { useState, useRef, useEffect, useId } from "react";

export interface CustomDatePickerProps {
  value: string; // ISO date "YYYY-MM-DD" or "YYYY-MM-DDTHH:mm"
  onChange: (value: string) => void;
  placeholder?: string;
  label?: string;
  id?: string;
  name?: string;
  disabled?: boolean;
  minDate?: string; // "YYYY-MM-DD" or "YYYY-MM-DDTHH:mm"
  maxDate?: string; // "YYYY-MM-DD" or "YYYY-MM-DDTHH:mm"
  yearRangeStart?: number;
  yearRangeEnd?: number;
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
  yearRangeStart,
  yearRangeEnd,
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
  const minDayIso = minDate ? minDate.slice(0, 10) : "";
  const maxDayIso = maxDate ? maxDate.slice(0, 10) : "";

  const minYearVal = minDayIso ? Number(minDayIso.slice(0, 4)) : null;
  const minMonthVal = minDayIso ? Number(minDayIso.slice(5, 7)) - 1 : null;
  const maxYearVal = maxDayIso ? Number(maxDayIso.slice(0, 4)) : null;
  const maxMonthVal = maxDayIso ? Number(maxDayIso.slice(5, 7)) - 1 : null;

  // Determine initial view year and month
  const getInitialView = () => {
    if (parsed) return { year: parsed.year, month: parsed.month };
    const currentYear = today.getFullYear();
    const currentMonth = today.getMonth();
    const todayIsoStr = `${currentYear}-${padZero(currentMonth + 1)}-${padZero(today.getDate())}`;

    if (minDayIso && todayIsoStr < minDayIso && minYearVal !== null && minMonthVal !== null) {
      return { year: minYearVal, month: minMonthVal };
    }
    if (maxDayIso && todayIsoStr > maxDayIso && maxYearVal !== null && maxMonthVal !== null) {
      return { year: maxYearVal, month: maxMonthVal };
    }
    return { year: currentYear, month: currentMonth };
  };

  const initialView = getInitialView();
  const [viewYear, setViewYear] = useState<number>(initialView.year);
  const [viewMonth, setViewMonth] = useState<number>(initialView.month);
  const [selectedHours, setSelectedHours] = useState<number>(parsed ? parsed.hours : 9);
  const [selectedMinutes, setSelectedMinutes] = useState<number>(parsed ? parsed.minutes : 0);
  const [viewMode, setViewMode] = useState<"days" | "months" | "years">("days");
  const [openUpward, setOpenUpward] = useState(false);

  // Detect available space to smartly position popup upward or downward
  useEffect(() => {
    if (isOpen && containerRef.current) {
      const rect = containerRef.current.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      if (spaceBelow < 380 && rect.top > 380) {
        setOpenUpward(true);
      } else {
        setOpenUpward(false);
      }
    } else {
      setViewMode("days");
    }
  }, [isOpen]);

  // Sync view when external value changes
  useEffect(() => {
    if (parsed) {
      setViewYear(parsed.year);
      setViewMonth(parsed.month);
      setSelectedHours(parsed.hours);
      setSelectedMinutes(parsed.minutes);
    } else {
      if (minYearVal !== null && minMonthVal !== null) {
        if (viewYear < minYearVal || (viewYear === minYearVal && viewMonth < minMonthVal)) {
          setViewYear(minYearVal);
          setViewMonth(minMonthVal);
        }
      }
      if (maxYearVal !== null && maxMonthVal !== null) {
        if (viewYear > maxYearVal || (viewYear === maxYearVal && viewMonth > maxMonthVal)) {
          setViewYear(maxYearVal);
          setViewMonth(maxMonthVal);
        }
      }
    }
  }, [value, minDate, maxDate]);

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

  const isPrevMonthDisabled =
    minYearVal !== null &&
    minMonthVal !== null &&
    (viewYear < minYearVal || (viewYear === minYearVal && viewMonth <= minMonthVal));

  const isNextMonthDisabled =
    maxYearVal !== null &&
    maxMonthVal !== null &&
    (viewYear > maxYearVal || (viewYear === maxYearVal && viewMonth >= maxMonthVal));

  const handlePrevMonth = () => {
    if (isPrevMonthDisabled) return;
    if (viewMonth === 0) {
      setViewMonth(11);
      setViewYear((prev) => prev - 1);
    } else {
      setViewMonth((prev) => prev - 1);
    }
  };

  const handleNextMonth = () => {
    if (isNextMonthDisabled) return;
    if (viewMonth === 11) {
      setViewMonth(0);
      setViewYear((prev) => prev + 1);
    } else {
      setViewMonth((prev) => prev + 1);
    }
  };

  const handleYearChange = (newYear: number) => {
    let newMonth = viewMonth;
    if (minYearVal !== null && minMonthVal !== null && newYear === minYearVal && newMonth < minMonthVal) {
      newMonth = minMonthVal;
    }
    if (maxYearVal !== null && maxMonthVal !== null && newYear === maxYearVal && newMonth > maxMonthVal) {
      newMonth = maxMonthVal;
    }
    setViewYear(newYear);
    setViewMonth(newMonth);
  };

  const handleHourStep = (delta: number) => {
    const newHours = (selectedHours + delta + 24) % 24;
    handleTimeChange(newHours, selectedMinutes);
  };

  const handleMinuteStep = (delta: number) => {
    let newMinutes = selectedMinutes + delta;
    let newHours = selectedHours;
    if (newMinutes >= 60) {
      newMinutes = 0;
      newHours = (newHours + 1) % 24;
    } else if (newMinutes < 0) {
      newMinutes = 55;
      newHours = (newHours - 1 + 24) % 24;
    }
    handleTimeChange(newHours, newMinutes);
  };

  const handleSelectDay = (day: number) => {
    const dateStr = `${viewYear}-${padZero(viewMonth + 1)}-${padZero(day)}`;
    if (minDayIso && dateStr < minDayIso) return;
    if (maxDayIso && dateStr > maxDayIso) return;
    if (includeTime) {
      const timeStr = `${padZero(selectedHours)}:${padZero(selectedMinutes)}`;
      onChange(`${dateStr}T${timeStr}`);
    } else {
      onChange(dateStr);
      setIsOpen(false);
    }
  };

  const todayIso = `${today.getFullYear()}-${padZero(today.getMonth() + 1)}-${padZero(today.getDate())}`;
  const isTodayDisabled =
    Boolean(minDayIso && todayIso < minDayIso) ||
    Boolean(maxDayIso && todayIso > maxDayIso);

  const handleTodayClick = () => {
    if (isTodayDisabled) return;
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

  // Dynamic Year options derivation
  const currentYear = today.getFullYear();
  let startYear = yearRangeStart;
  let endYear = yearRangeEnd;

  if (startYear === undefined) {
    if (minYearVal !== null) {
      startYear = minYearVal;
    } else {
      startYear = currentYear - 3;
    }
  }

  if (endYear === undefined) {
    if (maxYearVal !== null) {
      endYear = maxYearVal;
    } else {
      endYear = currentYear + 3;
    }
  }

  if (endYear < startYear) {
    endYear = startYear;
  }

  const activeYear = parsed ? parsed.year : viewYear;
  startYear = Math.min(startYear, activeYear, viewYear);
  endYear = Math.max(endYear, activeYear, viewYear);

  const yearOptions: number[] = [];
  for (let y = startYear; y <= endYear; y++) {
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
          className={`custom-datepicker-dropdown ${openUpward ? "open-up" : "open-down"}`}
        >
          {/* Header Month / Year Selectors */}
          <div className="custom-datepicker-header">
            <button
              type="button"
              disabled={viewMode === "days" ? isPrevMonthDisabled : false}
              onClick={handlePrevMonth}
              className="custom-datepicker-nav-btn"
              title={viewMode === "days" && isPrevMonthDisabled ? "Previous month unavailable" : "Previous"}
              aria-label="Previous"
              style={{
                opacity: viewMode === "days" && isPrevMonthDisabled ? 0.3 : 1,
                cursor: viewMode === "days" && isPrevMonthDisabled ? "not-allowed" : "pointer"
              }}
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <polyline points="15 18 9 12 15 6"></polyline>
              </svg>
            </button>

            <div className="custom-datepicker-header-titles">
              <button
                type="button"
                className={`custom-datepicker-view-btn ${viewMode === "months" ? "is-active" : ""}`}
                onClick={() => setViewMode(viewMode === "months" ? "days" : "months")}
                title="Toggle month selection view"
              >
                <span>{MONTH_NAMES[viewMonth]}</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <polyline points={viewMode === "months" ? "18 15 12 9 6 15" : "6 9 12 15 18 9"}></polyline>
                </svg>
              </button>

              <button
                type="button"
                className={`custom-datepicker-view-btn ${viewMode === "years" ? "is-active" : ""}`}
                onClick={() => setViewMode(viewMode === "years" ? "days" : "years")}
                title="Toggle year selection view"
              >
                <span>{viewYear}</span>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                  <polyline points={viewMode === "years" ? "18 15 12 9 6 15" : "6 9 12 15 18 9"}></polyline>
                </svg>
              </button>
            </div>

            <button
              type="button"
              disabled={viewMode === "days" ? isNextMonthDisabled : false}
              onClick={handleNextMonth}
              className="custom-datepicker-nav-btn"
              title={viewMode === "days" && isNextMonthDisabled ? "Next month unavailable" : "Next"}
              aria-label="Next"
              style={{
                opacity: viewMode === "days" && isNextMonthDisabled ? 0.3 : 1,
                cursor: viewMode === "days" && isNextMonthDisabled ? "not-allowed" : "pointer"
              }}
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                <polyline points="9 18 15 12 9 6"></polyline>
              </svg>
            </button>
          </div>

          {/* Month Selector Grid View */}
          {viewMode === "months" && (
            <div className="custom-datepicker-months-grid">
              {MONTH_NAMES.map((m, idx) => {
                const isMonthBeforeMin =
                  minYearVal !== null && minMonthVal !== null && viewYear === minYearVal && idx < minMonthVal;
                const isMonthAfterMax =
                  maxYearVal !== null && maxMonthVal !== null && viewYear === maxYearVal && idx > maxMonthVal;
                const isCurrentMonth = today.getFullYear() === viewYear && today.getMonth() === idx;
                const isSelectedMonth = parsed ? parsed.year === viewYear && parsed.month === idx : viewMonth === idx;
                const isDisabled = isMonthBeforeMin || isMonthAfterMax;

                return (
                  <button
                    key={m}
                    type="button"
                    disabled={isDisabled}
                    onClick={() => {
                      setViewMonth(idx);
                      setViewMode("days");
                    }}
                    className={`custom-datepicker-month-cell ${isSelectedMonth ? "is-selected" : ""} ${
                      isCurrentMonth ? "is-today" : ""
                    }`}
                  >
                    {SHORT_MONTH_NAMES[idx]}
                  </button>
                );
              })}
            </div>
          )}

          {/* Year Selector Grid View */}
          {viewMode === "years" && (
            <div className="custom-datepicker-years-grid">
              {yearOptions.map((y) => {
                const isCurrentYear = today.getFullYear() === y;
                const isSelectedYear = parsed ? parsed.year === y : viewYear === y;
                return (
                  <button
                    key={y}
                    type="button"
                    onClick={() => {
                      handleYearChange(y);
                      setViewMode("days");
                    }}
                    className={`custom-datepicker-year-cell ${isSelectedYear ? "is-selected" : ""} ${
                      isCurrentYear ? "is-today" : ""
                    }`}
                  >
                    {y}
                  </button>
                );
              })}
            </div>
          )}

          {/* Days Calendar Grid View */}
          {viewMode === "days" && (
            <>
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
                  const isBeforeMin = minDayIso ? currentDateIso < minDayIso : false;
                  const isAfterMax = maxDayIso ? currentDateIso > maxDayIso : false;
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
            </>
          )}

          {/* Time Picker Section (if enabled) */}
          {includeTime && viewMode === "days" && (
            <div className="custom-datepicker-time-section">
              <div className="custom-datepicker-time-header">
                <div style={{ display: "flex", alignItems: "center", gap: "0.45rem" }}>
                  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <circle cx="12" cy="12" r="10"></circle>
                    <polyline points="12 6 12 12 16 14"></polyline>
                  </svg>
                  <span className="custom-datepicker-time-label">Time</span>
                </div>

                {/* Tactile Stepper inputs */}
                <div className="custom-datepicker-stepper-wrap">
                  {/* Hours stepper */}
                  <div className="custom-datepicker-stepper">
                    <button
                      type="button"
                      className="custom-datepicker-step-btn"
                      onClick={() => handleHourStep(-1)}
                      title="Decrease hour"
                      aria-label="Decrease hour"
                    >
                      -
                    </button>
                    <span className="custom-datepicker-step-value">{padZero(selectedHours)}</span>
                    <button
                      type="button"
                      className="custom-datepicker-step-btn"
                      onClick={() => handleHourStep(1)}
                      title="Increase hour"
                      aria-label="Increase hour"
                    >
                      +
                    </button>
                  </div>

                  <span className="custom-datepicker-time-colon">:</span>

                  {/* Minutes stepper */}
                  <div className="custom-datepicker-stepper">
                    <button
                      type="button"
                      className="custom-datepicker-step-btn"
                      onClick={() => handleMinuteStep(-5)}
                      title="Decrease minutes"
                      aria-label="Decrease minutes"
                    >
                      -
                    </button>
                    <span className="custom-datepicker-step-value">{padZero(selectedMinutes)}</span>
                    <button
                      type="button"
                      className="custom-datepicker-step-btn"
                      onClick={() => handleMinuteStep(5)}
                      title="Increase minutes"
                      aria-label="Increase minutes"
                    >
                      +
                    </button>
                  </div>
                </div>
              </div>

              {/* Quick Time Presets */}
              <div className="custom-datepicker-time-presets">
                {[
                  { label: "09:00", h: 9, m: 0 },
                  { label: "10:00", h: 10, m: 0 },
                  { label: "11:00", h: 11, m: 0 },
                  { label: "14:00", h: 14, m: 0 },
                  { label: "16:00", h: 16, m: 0 },
                ].map((preset) => {
                  const isPresetActive = selectedHours === preset.h && selectedMinutes === preset.m;
                  return (
                    <button
                      key={preset.label}
                      type="button"
                      className={`custom-datepicker-preset-pill ${isPresetActive ? "is-active" : ""}`}
                      onClick={() => handleTimeChange(preset.h, preset.m)}
                    >
                      {preset.label}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

          {/* Footer Actions */}
          <div className="custom-datepicker-footer">
            <button
              type="button"
              disabled={isTodayDisabled}
              onClick={handleTodayClick}
              className="custom-datepicker-footer-btn"
              title={isTodayDisabled ? "Today is outside allowed date range" : "Select today"}
              style={{ opacity: isTodayDisabled ? 0.35 : 1, cursor: isTodayDisabled ? "not-allowed" : "pointer" }}
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
