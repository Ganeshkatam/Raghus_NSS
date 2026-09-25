import React, { useState, useRef, useEffect, useId } from "react";

export interface SelectOption {
  value: string;
  label: string;
  badge?: string;
  description?: string;
}

export interface CustomSelectProps {
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  id?: string;
  name?: string;
  disabled?: boolean;
  className?: string;
  style?: React.CSSProperties;
  menuStyle?: React.CSSProperties;
  ariaLabel?: string;
}

export const CustomSelect: React.FC<CustomSelectProps> = ({
  value,
  onChange,
  options,
  placeholder = "Select an option",
  id,
  name,
  disabled = false,
  className = "",
  style,
  menuStyle,
  ariaLabel,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [highlightedIndex, setHighlightedIndex] = useState<number>(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);
  const autoId = useId();
  const selectId = id || autoId;

  // Find currently selected option
  const selectedOption = options.find((opt) => opt.value === value);

  // Close when clicking outside
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

  // Keep highlighted index in sync when opening
  useEffect(() => {
    if (isOpen) {
      const idx = options.findIndex((opt) => opt.value === value);
      setHighlightedIndex(idx >= 0 ? idx : 0);
    }
  }, [isOpen, value, options]);

  // Scroll highlighted item into view if navigating with keyboard
  useEffect(() => {
    if (isOpen && highlightedIndex >= 0 && listRef.current) {
      const item = listRef.current.children[highlightedIndex] as HTMLElement | undefined;
      if (item) {
        item.scrollIntoView({ block: "nearest" });
      }
    }
  }, [highlightedIndex, isOpen]);

  const handleSelect = (val: string) => {
    onChange(val);
    setIsOpen(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return;

    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (!isOpen) {
        setIsOpen(true);
      } else {
        setHighlightedIndex((prev) => (prev < options.length - 1 ? prev + 1 : 0));
      }
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (!isOpen) {
        setIsOpen(true);
      } else {
        setHighlightedIndex((prev) => (prev > 0 ? prev - 1 : options.length - 1));
      }
    } else if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      if (isOpen && highlightedIndex >= 0 && highlightedIndex < options.length) {
        handleSelect(options[highlightedIndex].value);
      } else {
        setIsOpen((prev) => !prev);
      }
    } else if (e.key === "Escape" || e.key === "Tab") {
      setIsOpen(false);
    }
  };

  return (
    <div
      ref={containerRef}
      className={`custom-select-wrapper ${className}`}
      style={{ position: "relative", width: "100%", ...style }}
    >
      {/* Hidden input for standard form bindings if name is provided */}
      {name && <input type="hidden" name={name} value={value} />}

      <button
        id={selectId}
        type="button"
        role="combobox"
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        aria-label={ariaLabel || placeholder}
        disabled={disabled}
        onClick={() => !disabled && setIsOpen((prev) => !prev)}
        onKeyDown={handleKeyDown}
        className={`custom-select-trigger ${isOpen ? "is-open" : ""}`}
      >
        <span className={`custom-select-value ${!selectedOption && !value ? "is-placeholder" : ""}`}>
          {selectedOption ? selectedOption.label : placeholder}
        </span>
        <svg
          className={`custom-select-arrow ${isOpen ? "rotate-up" : ""}`}
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
      </button>

      {isOpen && (
        <ul
          ref={listRef}
          role="listbox"
          aria-labelledby={selectId}
          className="custom-select-dropdown"
          style={menuStyle}
        >
          {options.length === 0 ? (
            <li className="custom-select-empty">No options available</li>
          ) : (
            options.map((option, index) => {
              const isSelected = option.value === value;
              const isHighlighted = index === highlightedIndex;

              return (
                <li
                  key={option.value}
                  role="option"
                  aria-selected={isSelected}
                  onMouseEnter={() => setHighlightedIndex(index)}
                  onClick={() => handleSelect(option.value)}
                  className={`custom-select-option ${isSelected ? "is-selected" : ""} ${
                    isHighlighted ? "is-highlighted" : ""
                  }`}
                >
                  <div className="custom-select-option-content">
                    <span className="custom-select-option-label">{option.label}</span>
                    {option.description && (
                      <span className="custom-select-option-desc">{option.description}</span>
                    )}
                  </div>

                  {option.badge && (
                    <span className="custom-select-option-badge">{option.badge}</span>
                  )}

                  {isSelected && (
                    <svg
                      className="custom-select-check"
                      width="16"
                      height="16"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="#2563eb"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                    >
                      <polyline points="20 6 9 17 4 12" />
                    </svg>
                  )}
                </li>
              );
            })
          )}
        </ul>
      )}
    </div>
  );
};
