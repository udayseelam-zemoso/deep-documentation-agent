package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Java outline from the JavaParser AST, not from regular expressions.
 *
 * <p>We do <em>not</em> run the symbol solver. That needs the project's classpath, which an
 * autonomous scan of an arbitrary repo does not have. The AST still gives us type names,
 * methods, annotations, extends/implements, Javadoc, and the simple names mentioned in a body.
 * Cross-file links are made later by {@link KnowledgeGraph} matching those names to declarations.
 *
 * <p>Language level is Java 17 so records and sealed types in the scanned repo still parse;
 * this agent itself compiles as Java 11.
 */
public final class JavaAstUnderstander implements FileUnderstander {
    private static final Logger logger = LoggerFactory.getLogger(JavaAstUnderstander.class);

    private final JavaParser parser;

    public JavaAstUnderstander() {
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.parser = new JavaParser(configuration);
    }

    @Override
    public FileOutline outline(FileInfo file, DocLevel level, String source) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(source == null ? "" : source);
            if (!result.getResult().isPresent()) {
                logger.debug("JavaParser produced no AST for {}", file.getFilePath());
                return new FileOutline(file, level, FileOutline.ENGINE_JAVAPARSER, false, "",
                        new ArrayList<>(), new ArrayList<>());
            }
            CompilationUnit unit = result.getResult().get();
            String namespace = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            List<String> imports = new ArrayList<>();
            unit.getImports().forEach(i -> imports.add(i.getNameAsString()));

            List<DeclaredSymbol> symbols = new ArrayList<>();
            for (TypeDeclaration<?> type : unit.getTypes()) {
                symbols.add(toSymbol(type));
            }
            // Recovery can yield an empty CompilationUnit for garbage input; that is not a parse.
            // An actually empty file is a successful parse with no types.
            boolean emptyInput = source == null || source.trim().isEmpty();
            boolean parsed = !symbols.isEmpty() || (result.isSuccessful() && emptyInput);
            return new FileOutline(file, level, FileOutline.ENGINE_JAVAPARSER, parsed, namespace, imports, symbols);
        } catch (RuntimeException e) {
            logger.debug("JavaParser failed on {}: {}", file.getFilePath(), e.getMessage());
            return new FileOutline(file, level, FileOutline.ENGINE_JAVAPARSER, false, "",
                    new ArrayList<>(), new ArrayList<>());
        }
    }

    private static DeclaredSymbol toSymbol(TypeDeclaration<?> type) {
        SymbolKind kind = kindOf(type);
        List<String> annotations = new ArrayList<>();
        for (AnnotationExpr annotation : type.getAnnotations()) {
            annotations.add(annotation.getName().getIdentifier());
        }
        List<String> parents = new ArrayList<>();
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration ci = (ClassOrInterfaceDeclaration) type;
            ci.getExtendedTypes().forEach(t -> parents.add(t.getNameAsString()));
            ci.getImplementedTypes().forEach(t -> parents.add(t.getNameAsString()));
        }
        List<String> methods = new ArrayList<>();
        type.getMethods().forEach(m -> methods.add(m.getNameAsString()));
        type.getConstructors().forEach(c -> methods.add(c.getNameAsString()));

        Set<String> references = new LinkedHashSet<>();
        type.accept(new MentionCollector(references, type.getNameAsString()), null);
        references.remove(type.getNameAsString());

        return new DeclaredSymbol(type.getNameAsString(), kind, lineOf(type),
                type.getAccessSpecifier().asString(), firstSentence(javadocOf(type)),
                annotations, parents, methods, new ArrayList<>(references));
    }

    private static SymbolKind kindOf(TypeDeclaration<?> type) {
        if (type.isEnumDeclaration() || type instanceof EnumDeclaration) return SymbolKind.ENUM;
        if (type.isAnnotationDeclaration()) return SymbolKind.ANNOTATION;
        if (type instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration ci = (ClassOrInterfaceDeclaration) type;
            if (ci.isInterface()) return SymbolKind.INTERFACE;
        }
        String asString = type.toString();
        if (asString.contains("record " + type.getNameAsString())) return SymbolKind.RECORD;
        return SymbolKind.CLASS;
    }

    private static String javadocOf(TypeDeclaration<?> type) {
        if (type.getJavadoc().isPresent()) {
            return type.getJavadoc().get().getDescription().toText();
        }
        if (type.getComment().isPresent()) {
            return type.getComment().get().getContent();
        }
        return "";
    }

    /** First sentence, stripped of Javadoc markup and collapsed whitespace. */
    static String firstSentence(String text) {
        if (text == null) return "";
        String cleaned = text
                .replaceAll("\\{@link\\s+([^}]+)}", "$1")
                .replaceAll("\\{@code\\s+([^}]+)}", "$1")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) return "";
        int end = cleaned.indexOf('.');
        return end < 0 ? cleaned : cleaned.substring(0, end + 1).trim();
    }

    private static int lineOf(Node node) {
        return node.getBegin().map(p -> p.line).orElse(1);
    }

    /**
     * Collects simple type names and method-call names inside a declaration. {@code String},
     * {@code Object} and other java.lang names are dropped; they never point at a project file.
     */
    private static final class MentionCollector extends VoidVisitorAdapter<Void> {
        private static final Set<String> IGNORE = new LinkedHashSet<>();

        static {
            for (String name : new String[] {
                    "String", "Object", "Integer", "Long", "Boolean", "Double", "Float", "Short",
                    "Byte", "Character", "Void", "Class", "Override", "Deprecated", "SuppressWarnings",
                    "List", "Map", "Set", "Optional", "ArrayList", "HashMap", "HashSet",
                    "Collection", "Iterable", "Comparator", "Exception", "RuntimeException"}) {
                IGNORE.add(name);
            }
        }

        private final Set<String> into;
        private final String self;

        MentionCollector(Set<String> into, String self) {
            this.into = into;
            this.self = self;
        }

        @Override
        public void visit(ClassOrInterfaceType type, Void arg) {
            String name = type.getNameAsString();
            if (!IGNORE.contains(name) && !name.equals(self)) into.add(name);
            super.visit(type, arg);
        }

        @Override
        public void visit(MethodCallExpr call, Void arg) {
            String name = call.getNameAsString();
            if (!IGNORE.contains(name)) into.add(name);
            super.visit(call, arg);
        }

        @Override
        public void visit(MethodDeclaration method, Void arg) {
            super.visit(method, arg);
        }

        @Override
        public void visit(ConstructorDeclaration constructor, Void arg) {
            super.visit(constructor, arg);
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
        }
    }
}
