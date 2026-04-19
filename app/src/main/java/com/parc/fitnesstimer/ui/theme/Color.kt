package com.parc.fitnesstimer.ui.theme

import androidx.compose.ui.graphics.Color

// ── Physical display colours ─────────────────────────────────────────────────
/** Matches the blue seven-segment digits (D0/D1 — round counter). */
val DisplayBlue = Color(0xFF2979FF)

/** Matches the red seven-segment digits (D2–D5 — MM:SS). */
val DisplayRed = Color(0xFFE53935)

// ── Background / surface hierarchy ──────────────────────────────────────────
val BackgroundDeep   = Color(0xFF090909)   // Base layer
val SurfaceCard      = Color(0xFF111111)   // Card / panel
val SurfaceElevated  = Color(0xFF181818)   // Slightly raised surfaces
val BorderSubtle     = Color(0xFF1E1E1E)   // Dividers and outlines

// ── Text ──────────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFE4E4E4)
val TextSecondary = Color(0xFF999999)
val TextDisabled  = Color(0xFF4A4A4A)

// ── Accent / status ──────────────────────────────────────────────────────────
val AccentRed      = Color(0xFFE53935)   // Active chips, primary actions
val AccentBlue     = Color(0xFF2979FF)   // Round counter accent
val AccentGreen    = Color(0xFF43A047)   // Success, connected state
val AccentOrange   = Color(0xFFFF6D00)   // Warnings, transitions

// ── Material3 role overrides ─────────────────────────────────────────────────
val Md3Primary        = AccentRed
val Md3OnPrimary      = Color(0xFFFFFFFF)
val Md3Secondary      = Color(0xFF1E1E1E)
val Md3OnSecondary    = TextPrimary
val Md3Background     = BackgroundDeep
val Md3OnBackground   = TextPrimary
val Md3Surface        = SurfaceCard
val Md3OnSurface      = TextPrimary
val Md3SurfaceVariant = SurfaceElevated
val Md3OnSurfaceVar   = TextSecondary
val Md3Outline        = BorderSubtle
val Md3Error          = Color(0xFFCF6679)
val Md3OnError        = Color(0xFF000000)
