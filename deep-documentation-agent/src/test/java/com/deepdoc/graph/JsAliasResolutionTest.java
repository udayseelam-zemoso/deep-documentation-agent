package com.deepdoc.graph;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.deepdoc.testutil.ExampleRepos.write;
import static org.assertj.core.api.Assertions.assertThat;

class JsAliasResolutionTest {

    @TempDir
    Path repo;

    @Test
    void resolvesTsconfigPathsWorkspacePackagesBaseUrlAndConfiguredAliases() throws IOException {
        write(repo.resolve("tsconfig.base.json"),
                "{",
                "  // comments and trailing commas are allowed in tsconfig",
                "  \"compilerOptions\": {",
                "    \"baseUrl\": \".\",",
                "    \"paths\": {",
                "      \"@/*\": [\"src/*\"],",
                "      \"@shared\": [\"libs/shared/index.ts\"],",
                "    },",
                "  },",
                "}");
        write(repo.resolve("tsconfig.json"), "{ \"extends\": \"./tsconfig.base\" }");
        write(repo.resolve("src/app.ts"),
                "import { Client } from '@/api/client';",
                "import { util } from '@shared';",
                "import Button from 'components/Button';",
                "import { Ui } from '@acme/ui';",
                "import { Toggle } from '@acme/ui/toggle';",
                "import { x } from '@lib/x';",
                "import React from 'react';",
                "// import { gone } from '@/removed';");
        write(repo.resolve("src/api/client.ts"), "export class Client {}");
        write(repo.resolve("libs/shared/index.ts"), "export const util = 1;");
        write(repo.resolve("components/Button.tsx"), "export default () => null;");
        write(repo.resolve("packages/ui/package.json"), "{ \"name\": \"@acme/ui\", \"main\": \"src/main.ts\" }");
        write(repo.resolve("packages/ui/src/main.ts"), "export const Ui = 1;");
        write(repo.resolve("packages/ui/src/toggle.tsx"), "export const Toggle = 1;");
        write(repo.resolve("lib/x.ts"), "export const x = 1;");
        write(repo.resolve("src/removed.ts"), "export const gone = 1;");

        AppConfig config = ConfigLoader.load();
        config.getGraph().getJsAliases().put("@lib", "lib");
        GraphFixture fx = GraphFixture.build(repo, config);

        assertThat(fx.targetsOf("src/app.ts")).containsExactly(
                "components/Button.tsx",
                "lib/x.ts",
                "libs/shared/index.ts",
                "packages/ui/src/main.ts",
                "packages/ui/src/toggle.tsx",
                "src/api/client.ts");
    }

    @Test
    void usesNearestTsconfigAndFallsBackToAtSlashConvention() throws IOException {
        write(repo.resolve("web/tsconfig.json"),
                "{ \"compilerOptions\": { \"paths\": { \"~/*\": [\"./app/*\"] } } }");
        write(repo.resolve("web/app/main.ts"), "import { u } from '~/util';");
        write(repo.resolve("web/app/util.ts"), "export const u = 1;");
        write(repo.resolve("other/package.json"), "{ \"name\": \"other\" }");
        write(repo.resolve("other/src/thing.js"), "const h = require('@/helper');");
        write(repo.resolve("other/src/helper.js"), "module.exports = {};");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("web/app/main.ts")).containsExactly("web/app/util.ts");
        assertThat(fx.targetsOf("other/src/thing.js")).containsExactly("other/src/helper.js");
    }

    @Test
    void readsOnlyScriptBlocksOfVueAndSvelteComponents() throws IOException {
        write(repo.resolve("src/App.vue"),
                "<template>",
                "  <p>import Fake from './Fake'</p>",
                "</template>",
                "<script setup lang=\"ts\">",
                "import Card from './components/Card.vue'",
                "import { store } from './store'",
                "</script>");
        write(repo.resolve("src/components/Card.vue"), "<script>export default {}</script>");
        write(repo.resolve("src/store.ts"), "export const store = {};");
        write(repo.resolve("src/Fake.ts"), "export default 1;");
        write(repo.resolve("src/Widget.svelte"),
                "<script>",
                "  import Card from './components/Card.vue';",
                "</script>",
                "<div>{name}</div>");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("src/App.vue")).containsExactly("src/components/Card.vue", "src/store.ts");
        assertThat(fx.edge("src/App.vue", "src/store.ts").getEvidence().getStartLine()).isEqualTo(6);
        assertThat(fx.targetsOf("src/Widget.svelte")).containsExactly("src/components/Card.vue");
    }
}
