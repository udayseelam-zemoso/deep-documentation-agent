package com.deepdoc.understand;

import com.deepdoc.graph.LanguageFamily;
import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalUnderstanderTest {

    @TempDir
    Path dir;

    @Test
    void javascriptExportsAndArrowFunctions() {
        FileOutline outline = outline("app.ts", LanguageFamily.JAVASCRIPT, String.join("\n",
                "export class Cart {",
                "  add() {}",
                "}",
                "export function checkout() {}",
                "export const render = () => null;",
                "// export function gone() {}"));
        assertThat(outline.typeNames()).contains("Cart");
        assertThat(outline.methodNames()).contains("checkout", "render");
        assertThat(outline.methodNames()).doesNotContain("gone");
    }

    @Test
    void pythonClassAndDefs() {
        FileOutline outline = outline("main.py", LanguageFamily.PYTHON, String.join("\n",
                "class User:",
                "    pass",
                "def run():",
                "    pass"));
        assertThat(outline.typeNames()).contains("User");
        assertThat(outline.methodNames()).contains("run");
    }

    @Test
    void goTypesAndFuncs() {
        FileOutline outline = outline("store.go", LanguageFamily.GO, String.join("\n",
                "type Store struct {}",
                "func New() *Store { return &Store{} }",
                "func (s *Store) Open() {}"));
        assertThat(outline.typeNames()).contains("Store");
        assertThat(outline.methodNames()).contains("New", "Open");
    }

    private FileOutline outline(String name, LanguageFamily family, String source) {
        FileInfo file = new FileInfo(dir.resolve(name), "TypeScript", 10, LocalDateTime.now());
        return new LexicalUnderstander(family).outline(file, DocLevel.SUMMARY, source);
    }
}
