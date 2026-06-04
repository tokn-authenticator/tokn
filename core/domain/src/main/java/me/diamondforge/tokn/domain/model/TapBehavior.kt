package me.diamondforge.tokn.domain.model

/**
 * Controls how a tap on an account card triggers a code copy.
 *
 * [NONE] tapping does nothing.
 * [SINGLE] a single tap copies the code.
 * [DOUBLE] a double tap copies the code.
 */
enum class TapBehavior { NONE, SINGLE, DOUBLE }
