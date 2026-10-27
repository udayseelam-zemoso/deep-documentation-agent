package com.deepdoc.analyzer;

import com.deepdoc.config.ConfigLoader;
import com.deepdoc.model.ArchitectureStyle;
import com.deepdoc.model.DetectedModule;
import com.deepdoc.model.DetectedPattern;
import com.deepdoc.model.RepositoryContext;
import com.deepdoc.model.RepositoryManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.deepdoc.testutil.ExampleRepos.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ArchitectureDetectorTest {

    @TempDir
    Path repo;

    @Test
    void springBootMonolithWithConfidenceAndEvidence() throws IOException {
        write(repo.resolve("pom.xml"),
                "<project><artifactId>shop</artifactId>",
                "  <dependencies><dependency>",
                "    <groupId>org.springframework.boot</groupId>",
                "    <artifactId>spring-boot-starter-web</artifactId>",
                "  </dependency></dependencies></project>");
        write(repo.resolve("src/main/resources/application.yml"), "server.port: 8080");
        write(repo.resolve("src/main/java/com/acme/ShopApplication.java"),
                "package com.acme;",
                "",
                "@SpringBootApplication",
                "public class ShopApplication {",
                "    public static void main(String[] args) {}",
                "}");

        RepositoryContext context = analyze();

        assertThat(context.getDetectedArchitecture()).isEqualTo("Spring Boot Application");
        assertThat(context.getArchitectureStyle()).isEqualTo(ArchitectureStyle.MONOLITH);
        assertThat(context.getArchitectureConfidence()).isCloseTo(1.0, within(0.001));
        assertThat(context.getFrameworks()).containsExactly("Spring Boot");
        assertThat(context.getTechnologies()).containsEntry("build", "Maven");
        assertThat(context.getModules()).extracting(DetectedModule::getRelativePath).contains(".");
        assertThat(context.getPatterns()).filteredOn(DetectedPattern::isFramework)
                .extracting(DetectedPattern::getName)
                .doesNotContain("Spring");
        assertThat(context.getPatterns().get(0).getEvidence())
                .anyMatch(e -> e.getStartLine() == 3);
    }

    @Test
    void mavenMultiModuleWithOneAppIsModularMonolith() throws IOException {
        write(repo.resolve("pom.xml"),
                "<project><artifactId>shop</artifactId>",
                "  <modules><module>api</module><module>domain</module></modules></project>");
        write(repo.resolve("api/pom.xml"), "<project><artifactId>shop-api</artifactId></project>");
        write(repo.resolve("domain/pom.xml"), "<project><artifactId>shop-domain</artifactId></project>");
        write(repo.resolve("api/src/main/java/com/acme/ApiApplication.java"),
                "@SpringBootApplication",
                "public class ApiApplication { public static void main(String[] a) {} }");
        write(repo.resolve("domain/src/main/java/com/acme/Order.java"), "public class Order {}");

        RepositoryContext context = analyze();

        assertThat(context.getArchitectureStyle()).isEqualTo(ArchitectureStyle.MODULAR_MONOLITH);
        assertThat(context.getDetectedArchitecture()).isEqualTo("Spring Boot Application");
        assertThat(context.getModules()).extracting(DetectedModule::getRelativePath)
                .containsExactly(".", "api", "domain");
        assertThat(module(context, "api").isDeployable()).isTrue();
        assertThat(module(context, "domain").isDeployable()).isFalse();
        assertThat(module(context, ".").isDeployable()).isFalse();
    }

    @Test
    void twoAppsWithComposeAreMicroservices() throws IOException {
        write(repo.resolve("docker-compose.yml"),
                "services:",
                "  orders:",
                "    build: ./orders",
                "  billing:",
                "    build: ./billing",
                "  postgres:",
                "    image: postgres:16");
        write(repo.resolve("orders/Dockerfile"), "FROM eclipse-temurin:17");
        write(repo.resolve("billing/Dockerfile"), "FROM eclipse-temurin:17");
        write(repo.resolve("orders/src/main/java/OrdersApp.java"),
                "@SpringBootApplication public class OrdersApp { public static void main(String[] a) {} }");
        write(repo.resolve("billing/src/main/java/BillingApp.java"),
                "@SpringBootApplication public class BillingApp { public static void main(String[] a) {} }");
        write(repo.resolve("orders/pom.xml"), "<project><artifactId>orders</artifactId></project>");
        write(repo.resolve("billing/pom.xml"), "<project><artifactId>billing</artifactId></project>");

        RepositoryContext context = analyze();

        assertThat(context.getArchitectureStyle()).isEqualTo(ArchitectureStyle.MICROSERVICES);
        assertThat(context.getDetectedArchitecture()).isEqualTo("Spring Boot microservices");
        assertThat(context.getModules()).filteredOn(DetectedModule::isDeployable).hasSize(2);
    }

    @Test
    void reactPlusExpressIsFullStack() throws IOException {
        write(repo.resolve("frontend/package.json"),
                "{ \"name\": \"web\", \"scripts\": { \"start\": \"vite\" }, \"dependencies\": { \"react\": \"^18.0.0\" } }");
        write(repo.resolve("frontend/src/App.tsx"), "export const App = () => null;");
        write(repo.resolve("backend/package.json"),
                "{ \"name\": \"api\", \"scripts\": { \"start\": \"node src/index.js\" }, \"dependencies\": { \"express\": \"^4.18.0\" } }");
        write(repo.resolve("backend/src/index.js"), "const app = express();");

        RepositoryContext context = analyze();

        assertThat(context.getDetectedArchitecture()).isEqualTo("Full-stack (Express.js + React)");
        assertThat(context.getFrameworks()).contains("React", "Express.js");
        assertThat(context.getArchitectureStyle()).isEqualTo(ArchitectureStyle.MODULAR_MONOLITH);
    }

    @Test
    void nextJsSuppressesReactAsThePrimaryFrontend() throws IOException {
        write(repo.resolve("package.json"),
                "{ \"dependencies\": { \"next\": \"14.0.0\", \"react\": \"18.0.0\" } }");
        write(repo.resolve("next.config.js"), "module.exports = {};");

        RepositoryContext context = analyze();

        assertThat(context.getDetectedArchitecture()).isEqualTo("Next.js Application");
        assertThat(context.getFrameworks()).contains("Next.js").doesNotContain("React");
    }

    @Test
    void djangoAndUnknownRepo() throws IOException {
        write(repo.resolve("manage.py"), "#!/usr/bin/env python", "import django");
        write(repo.resolve("requirements.txt"), "Django==4.2");
        write(repo.resolve("templates/index.html"), "<html></html>");

        RepositoryContext django = analyze();
        assertThat(django.getDetectedArchitecture()).isEqualTo("Django Application");
        assertThat(django.getArchitectureStyle()).isEqualTo(ArchitectureStyle.MONOLITH);

        Path empty = repo.resolve("empty");
        java.nio.file.Files.createDirectories(empty);
        RepositoryContext unknown = new RepositoryAnalyzer(ConfigLoader.load()).analyze(empty).getContext();
        assertThat(unknown.getDetectedArchitecture()).isEqualTo("Unknown");
        assertThat(unknown.getArchitectureConfidence()).isZero();
    }

    @Test
    void composeServiceCountIgnoresDatabases() {
        String compose = String.join("\n",
                "services:",
                "  api:",
                "    build: .",
                "  postgres:",
                "    image: postgres:16",
                "volumes:",
                "  data:");
        assertThat(ModuleDetector.countComposeServices(compose)).isEqualTo(1);
    }

    @Test
    void percentFormatsConfidence() {
        assertThat(ArchitectureDetector.percent(0.851)).isEqualTo("85%");
        assertThat(ArchitectureDetector.percent(1.0)).isEqualTo("100%");
    }

    private RepositoryContext analyze() throws IOException {
        RepositoryManifest manifest = new RepositoryAnalyzer(ConfigLoader.load()).analyze(repo);
        return manifest.getContext();
    }

    private static DetectedModule module(RepositoryContext context, String relativePath) {
        return context.getModules().stream()
                .filter(m -> m.getRelativePath().equals(relativePath))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no module " + relativePath));
    }
}
