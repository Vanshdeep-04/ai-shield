package com.aishield.message;

/**
 * Where an analysed message came from. Only GMAIL and MANUAL are implemented.
 * New sources (e.g. SMS) only need a new value plus a client-side extractor:
 * the normalisation, URL analysis, Gemini analysis and risk engine are source-agnostic.
 */
public enum MessageSource {
    /** Email selected by the user from their Gmail account via the Gmail API. */
    GMAIL,
    /** Text pasted by the user to test the analysis engine without Gmail. */
    MANUAL
}
