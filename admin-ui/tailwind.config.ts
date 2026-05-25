import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        taozi: {
          primary: "#D67744",
          primaryDark: "#B85E2E",
          primaryLight: "#E89060",
          secondary: "#EFB773",
          secondaryDark: "#D69B52",
          secondaryLight: "#F5CB94",
          bg: "#1A1A1A",
          card: "#252525",
          input: "#2A2A2A",
          surface: "#303030",
          textPrimary: "#FFFFFF",
          textSecondary: "#B0B0B0",
          textHint: "#666666",
          success: "#4CAF50",
          error: "#F44336",
          warning: "#FF9800",
        },
      },
      borderRadius: { taozi: "12px" },
    },
  },
  plugins: [],
} satisfies Config;
