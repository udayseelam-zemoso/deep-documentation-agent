package com.deepdoc.ranker;

/** How much documentation effort a file gets. */
public enum DocLevel {
    /** Own section: purpose, design, key flows, with source references. */
    DEEP,
    /** A paragraph inside its package's section. */
    SUMMARY,
    /** Name and one-line role in the package file list. */
    LISTED
}
