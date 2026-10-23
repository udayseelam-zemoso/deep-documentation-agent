package com.deepdoc.ranker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntryPointDetectorTest {

    @Test
    void recognisesEntryPointsInEveryLanguageFamily() {
        assertThat(EntryPointDetector.isEntryPoint("Python", "if __name__ == \"__main__\":\n    main()")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Python", "@app.get(\"/items\")\ndef items(): ...")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Go", "package main\n\nfunc main() {}")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("C#", "[ApiController]\npublic class A : ControllerBase {}"))
                .isTrue();
        assertThat(EntryPointDetector.isEntryPoint("C", "int main(int argc, char **argv) {}")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Rust", "#[tokio::main]\nasync fn main() {}")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Ruby", "class A < ApplicationController\nend")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("PHP", "class A extends Controller {}")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Swift", "@main\nstruct App {}")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Shell", "#!/bin/sh\necho hi")).isTrue();
    }

    @Test
    void recognisesMainFunctionsInGenericFallbackLanguages() {
        assertThat(EntryPointDetector.isEntryPoint("Dart", "void main() => runApp(App());")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Clojure", "(defn -main [& args])")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Haskell", "main :: IO ()\nmain = pure ()")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Elixir", "defmodule A do\n  use Application\nend")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Lua", "local function domain() end")).isFalse();
    }

    @Test
    void ignoresOrdinaryFilesAndCommentedOutMarkers() {
        assertThat(EntryPointDetector.isEntryPoint("Python", "# if __name__ == '__main__':\ndef f(): pass"))
                .isFalse();
        assertThat(EntryPointDetector.isEntryPoint("Go", "package util\n\nfunc mainly() {}")).isFalse();
        assertThat(EntryPointDetector.isEntryPoint("Rust", "// fn main() {}\npub fn run() {}")).isFalse();
        assertThat(EntryPointDetector.isEntryPoint("Shell", "log() { echo \"$@\"; }")).isFalse();
        assertThat(EntryPointDetector.isEntryPoint("Markdown", "int main() {}")).isFalse();
    }
}
