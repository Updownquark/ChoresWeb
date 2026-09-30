import { createTheme } from "@mui/material/styles";

export const myTheme = createTheme({
  colorSchemes: {
    light: {
      palette: {
        primary: {
          main: "#6366f1",
          contrastText: "#ffffff", // White text on purple button
        },
        secondary: {
          main: "#06b6d4",
          contrastText: "#ffffff",
        },
        background: {
          default: "#f8fafc",
          paper: "#ffffff",
        },
        text: {
          primary: "#0f172a",
          secondary: "#475569",
        },
      },
    },
    dark: {
      palette: {
        primary: {
          main: "#c084fc",      // Bright purple
          contrastText: "#000000", // Dark background color for high contrast text on the purple button
        },
        secondary: {
          main: "#06b6d4",
          contrastText: "#16171d",
        },
        background: {
          default: "#16171d",
          paper: "#1f2028",
        },
        text: {
          primary: "#f3f4f6",
          secondary: "#9ca3af",
        },
      },
    },
  },
  shape: {
    borderRadius: 12,
  },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    button: {
      textTransform: "none",
      fontWeight: 600,
    },
  },
});
