import { createTheme } from "@mui/material/styles";

export const myTheme = createTheme({
  palette: {
    mode: "light",
    primary: {
      main: "#6366f1", // Indigo modern accent
      light: "#818cf8",
      dark: "#4f46e5",
    },
    secondary: {
      main: "#06b6d4", // Cyan accent
    },
    background: {
      default: "#f8fafc",
      paper: "#ffffff",
    },
    text: { //Explicit dark colors for light mode typography
      primary: "#0f172a",   // Slate 900: Deep dark color for headings and body text
      secondary: "#475569", // Slate 600: Medium dark color for captions and subtitles
      disabled: "#94a3b8",  // Slate 400: Light gray for disabled states
    },  },
  shape: {
    borderRadius: 12, // Modern smooth corners
  },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    button: {
      textTransform: "none", // Remove all-caps for modern look
      fontWeight: 600,
    },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          boxShadow: "none",
          "&:hover": {
            boxShadow: "0px 4px 12px rgba(99, 102, 241, 0.2)",
          },
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          boxShadow:
            "0px 1px 3px rgba(0, 0, 0, 0.05), 0px 10px 15px -5px rgba(0, 0, 0, 0.04)",
        },
      },
    },
  },
});
