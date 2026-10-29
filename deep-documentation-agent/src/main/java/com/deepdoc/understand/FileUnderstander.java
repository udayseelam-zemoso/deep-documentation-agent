package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;

/** Turns source text into a {@link FileOutline}. Day 6-7 ships only the JavaParser implementation. */
public interface FileUnderstander {
    FileOutline outline(FileInfo file, DocLevel level, String source);
}
