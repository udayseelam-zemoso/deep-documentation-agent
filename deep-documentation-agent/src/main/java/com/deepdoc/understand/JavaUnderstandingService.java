package com.deepdoc.understand;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.UnderstandSection;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.LanguageFamily;
import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;
import com.deepdoc.ranker.FileRank;
import com.deepdoc.ranker.RankingResult;
import com.deepdoc.utils.SourceFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Day 6-10 pipeline: outline ranked files, extract facts at the file's documentation level,
 * hang unique type names off a knowledge graph, and write a one-sentence responsibility.
 *
 * <p>Week 1 already stamped each file DEEP, SUMMARY or LISTED. DEEP and SUMMARY are outlined.
 * LISTED files stay as names for a later package listing. Java uses JavaParser. Every other
 * language uses {@link LexicalUnderstander}. Responsibilities come from Javadoc, then a name
 * heuristic; DEEP files may be rewritten by the LLM when a key is present and {@code --no-llm}
 * was not passed.
 */
public class JavaUnderstandingService {
    private static final Logger logger = LoggerFactory.getLogger(JavaUnderstandingService.class);

    private final UnderstandSection config;
    private final FileUnderstander java;
    private final ResponsibilityInferrer inferrer;
    private final LlmClient llm;
    private final boolean llmAllowed;

    public JavaUnderstandingService(AppConfig config) {
        this(config, false);
    }

    public JavaUnderstandingService(AppConfig config, boolean llmAllowed) {
        this(config.getUnderstand(), new JavaAstUnderstander(),
                client(config, llmAllowed), llmAllowed && config.getLlm().isEnabled());
    }

    JavaUnderstandingService(UnderstandSection config, FileUnderstander java,
                             LlmClient llm, boolean llmAllowed) {
        this.config = config;
        this.java = java;
        this.llm = llm == null ? LlmClient.disabled() : llm;
        this.llmAllowed = llmAllowed && this.llm.available();
        this.inferrer = new ResponsibilityInferrer(this.llm);
    }

    /** Structure and heuristic responsibilities only. Tests use this. */
    public UnderstandingResult understand(RankingResult ranking) {
        return understand(null, ranking, null, false);
    }

    public UnderstandingResult understand(DependencyGraph graph, RankingResult ranking,
                                          String architecture, boolean allowLlm) {
        List<FileOutline> outlines = new ArrayList<>();
        List<FileRank> ranks = new ArrayList<>();
        int ast = 0;
        int lexical = 0;
        int skippedListed = 0;
        int failed = 0;
        for (FileRank rank : ranking.getFiles()) {
            if (rank.getLevel() == DocLevel.LISTED && !config.isIncludeListed()) {
                skippedListed++;
                continue;
            }
            if (outlines.size() >= config.getMaxFiles()) {
                break;
            }
            FileOutline outline = parse(rank);
            if (!outline.isParsed()) {
                failed++;
            } else if (FileOutline.ENGINE_JAVAPARSER.equals(outline.getEngine())) {
                ast++;
            } else {
                lexical++;
            }
            outlines.add(outline);
            ranks.add(rank);
        }
        KnowledgeGraph knowledge = KnowledgeGraph.from(outlines);
        boolean rewrite = allowLlm && llmAllowed;
        for (int i = 0; i < outlines.size(); i++) {
            inferrer.infer(outlines.get(i), ranks.get(i), graph, knowledge, architecture, rewrite);
        }
        int deep = 0;
        int summary = 0;
        for (FileOutline outline : outlines) {
            if (outline.getLevel() == DocLevel.DEEP) {
                deep++;
            } else if (outline.getLevel() == DocLevel.SUMMARY) {
                summary++;
            }
        }
        String mode = llm.available() && rewrite ? llm.mode() : "heuristic";
        logger.info("Understood {} files ({} DEEP, {} SUMMARY, {} Java AST, {} lexical, {} LISTED skipped, {} failed); responsibilities: {}; {} facts, {} use edges",
                outlines.size(), deep, summary, ast, lexical, skippedListed, failed, mode,
                knowledge.getFacts().size(), knowledge.useEdgeCount());
        return new UnderstandingResult(knowledge, ast, lexical, skippedListed, failed, mode);
    }

    private FileOutline parse(FileRank rank) {
        FileInfo file = rank.getFile();
        String source = SourceFiles.read(file.getFilePath());
        boolean javaFile = "Java".equals(file.getLanguage());
        if (source == null) {
            return new FileOutline(file, rank.getLevel(),
                    javaFile ? FileOutline.ENGINE_JAVAPARSER : FileOutline.ENGINE_LEXICAL,
                    false, "", new ArrayList<>(), new ArrayList<>());
        }
        if (javaFile) {
            return java.outline(file, rank.getLevel(), source);
        }
        return new LexicalUnderstander(LanguageFamily.of(file.getLanguage()))
                .outline(file, rank.getLevel(), source);
    }

    static LlmClient client(AppConfig config, boolean llmAllowed) {
        if (!llmAllowed || config.getLlm() == null || !config.getLlm().isEnabled()) {
            return LlmClient.disabled();
        }
        if (!"openai".equalsIgnoreCase(config.getLlm().getProvider())) {
            logger.warn("Unknown llm.provider '{}'; using heuristic responsibilities", config.getLlm().getProvider());
            return LlmClient.disabled();
        }
        return new OpenAiLlmClient(config.getLlm());
    }
}
