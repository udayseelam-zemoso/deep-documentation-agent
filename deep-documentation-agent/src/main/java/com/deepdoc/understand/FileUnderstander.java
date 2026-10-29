package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;

/** Turns one source file into a {@link FileOutline}. */
public interface FileUnderstander {
    FileOutline outline(FileInfo file, DocLevel level, String source);
}
