import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";
import { Navbar } from "@/components/Navbar";
import { AuthProvider } from "@/context/AuthContext";

export const metadata: Metadata = {
  title: "ServiceDesk | Intelligent Incident Resolution System",
  description: "BITS ServiceDesk: Pure Java Domain Core & Next.js Incident Management System",
};

export default function RootLayout({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <div
            style={{
              minHeight: "100vh",
              display: "flex",
              flexDirection: "column",
            }}
          >
            <Navbar />

            <main
              style={{
                flex: 1,
                maxWidth: "1100px",
                width: "100%",
                margin: "0 auto",
                padding: "2rem",
              }}
            >
              {children}
            </main>

            <footer
              style={{
                backgroundColor: "var(--bg-surface)",
                borderTop: "1px solid var(--border-color)",
                padding: "1.25rem 2rem",
                fontSize: "0.8125rem",
                color: "var(--text-muted)",
              }}
            >
              <div
                style={{
                  maxWidth: "1100px",
                  margin: "0 auto",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  flexWrap: "wrap",
                  gap: "0.5rem",
                }}
              >
                <span>ServiceDesk &bull; Intelligent IT Service Management System</span>
                <span>Framework-Free Domain Core (Hexagonal Architecture)</span>
              </div>
            </footer>
          </div>
        </AuthProvider>
      </body>
    </html>
  );
}
