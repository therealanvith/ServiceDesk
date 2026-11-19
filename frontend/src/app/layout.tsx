import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { Navbar } from "@/components/Navbar";
import { themeInitScript } from "@/components/ThemeToggle";
import { AuthProvider } from "@/context/AuthContext";

export const metadata: Metadata = {
  title: "ServiceDesk",
  description: "Report IT issues and follow their progress.",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    // The theme script sets data-theme before hydration, so the attribute legitimately differs.
    <html lang="en" suppressHydrationWarning>
      <head>
        {/* biome-ignore lint/security/noDangerouslySetInnerHtml: static inline script, no user input */}
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body>
        <AuthProvider>
          <a href="#main-content" className="skip-link">
            Skip to content
          </a>
          <Navbar />
          <main id="main-content" className="page">
            {children}
          </main>
        </AuthProvider>
      </body>
    </html>
  );
}
