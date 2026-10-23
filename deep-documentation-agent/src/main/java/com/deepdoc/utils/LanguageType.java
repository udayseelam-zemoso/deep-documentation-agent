package com.deepdoc.utils;

/** What kind of file a language describes; see {@code languages.yml}. */
public enum LanguageType {
    /** Source code: parsed for dependencies and ranked. */
    PROGRAMMING,
    /** HTML, XML, stylesheets. */
    MARKUP,
    /** JSON, YAML, SQL, schemas. */
    DATA,
    /** Human-written documentation. */
    PROSE,
    /** Build, container and infrastructure definitions. */
    CONFIG
}
