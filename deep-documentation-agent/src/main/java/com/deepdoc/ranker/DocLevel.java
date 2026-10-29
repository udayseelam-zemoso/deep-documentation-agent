package com.deepdoc.ranker;

/**
 * How much documentation effort a file gets. Large repositories cannot be documented file by
 * file within a reasonable budget, so effort goes where readers need it most.
 */
public enum DocLevel {
    /** Own section: purpose, design, key flows, with source references. */
    DEEP,
    /** A paragraph inside its package's section. */
    SUMMARY,
    /** Name and one-line role in the package file list. */
    LISTED
}
