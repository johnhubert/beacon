export const COLORS = {
  corePurple: "#440e8e",
  background: "#20064a",
  surface: "#2f0f6a",
  surfaceSecondary: "#3a1c82",
  surfaceOverlay: "rgba(48, 12, 110, 0.85)",
  textPrimary: "#f2e8ff",
  textSecondary: "#cbbdf2",
  accent: "#6c4ce3",
  success: "#56e39f",
  warning: "#f8d66d",
  danger: "#ff6b6b",
  border: "rgba(255, 255, 255, 0.12)"
} as const;

export type ColorKey = keyof typeof COLORS;
