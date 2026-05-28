package me.diamondforge.tokn.importer

/**
 * Reads an export file from a third-party authenticator and converts it to OtpAccounts.
 *
 * Implementations are stateless and side-effect free: registry detection calls [canHandle]
 * cheaply on raw bytes, then the UI calls [parse] with an optional password. Encryption
 * handling lives behind the [ImportOutcome.NeedsPassword] / [ImportOutcome.WrongPassword]
 * states so the ViewModel and screen never have to know format-specific crypto details.
 */
interface ExternalImporter {
    /** Stable machine id used for state restoration in the UI (e.g. "aegis"). */
    val id: String

    /** User-visible display name (e.g. "Aegis Authenticator"). Not translated; brand names. */
    val displayName: String

    /** Single-line localized help blurb, surfaced under the radio in the source picker. */
    val noteRes: Int

    /**
     * Sort weight in the source picker. Lower values surface first; equal values
     * fall back to alphabetical [displayName]. Override to pin a built-in source
     * above the alphabetised brand list.
     */
    val pickerOrder: Int get() = 100

    /** MIME types passed to the system file picker. */
    val acceptedMimeTypes: Array<String>

    /** Cheap structural check. Must NOT fully parse — used to route a file to the right importer. */
    fun canHandle(raw: ByteArray): Boolean

    /**
     * @param raw the full file bytes
     * @param password supplied by the user; null on first attempt
     */
    fun parse(raw: ByteArray, password: String?): ImportOutcome
}
