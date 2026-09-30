import { createTheme } from "@mui/material/styles";

export const myTheme = createTheme({
  colorSchemes: {
    light: {
      palette: {
        primary: { main: "#6366f1" },
        secondary: { main: "#06b6d4" },
        background: { default: "#f8fafc", paper: "#ffffff" },
        text: { primary: "#0f172a", secondary: "#475569" },
      },
    },
    dark: {
      palette: {
        primary: { main: "#c084fc" }, // Matches your CSS --accent
        secondary: { main: "#06b6d4" },
        background: { 
          default: "#16171d", // Matches your CSS --bg
          paper: "#1f2028",   // Dark background for Accordions/Cards
        },
        text: { 
          primary: "#f3f4f6", // Matches your CSS --text-h
          secondary: "#9ca3af" // Matches your CSS --text
        },
      },
    },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    button: { textTransform: "none", fontWeight: 600 },
  },
  // Keep your custom component overrides down here...
});
