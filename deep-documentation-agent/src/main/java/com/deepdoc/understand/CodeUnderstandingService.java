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
 * Week 2 pipeline: parse the files that will be documented, hang their symbols off a knowledge
 * graph, and write a one-sentence responsibility for each.
 *
 * <p>Only DEEP and SUMMARY files are parsed by default. LISTED files get a name in the ranking
 * table and are not worth an AST walk on a 100k-line repo. Java is parsed with JavaParser; every
 * other language uses {@link LexicalUnderstander}.
 */
public class CodeUnderstandingService {
    private static final Logger logger = LoggerFactory.getLogger(CodeUnderstandingService.class);

    private final UnderstandSection config;
    private final FileUnderstander java;
    private final ResponsibilityInferrer inferrer;
    private final LlmClient llm;
    private final boolean llmAllowed;

    public CodeUnderstandingService(AppConfig config, boolean llmAllowed) {
        this.config = config.getUnderstand();
        this.java = new JavaAstUnderstander();
        this.llm = llmClient(config, llmAllowed);
        this.inferrer = new ResponsibilityInferrer(this.llm);
        this.llmAllowed = llmAllowed && config.getLlm().isEnabled();
    }

    CodeUnderstandingService(UnderstandSection config, FileUnderstander java,
                             ResponsibilityInferrer inferrer, LlmClient llm, boolean llmAllowed) {
        this.config = config;
        this.java = java;
        this.inferrer = inferrer;
        this.llm = llm;
        this.llmAllowed = llmAllowed;
    }

    public UnderstandingResult understand(DependencyGraph graph, RankingResult ranking, String architecture) {
        List<FileRank> selected = select(ranking);
        List<FileOutline> outlines = new ArrayList<>();
        int ast = 0;
        int lexical = 0;
        int failed = 0;
        for (FileRank rank : selected) {
            FileOutline outline = parse(rank);
            if (!outline.isParsed()) failed++;
            else if (FileOutline.ENGINE_JAVAPARSER.equals(outline.getEngine())) ast++;
            else lexical++;
            outlines.add(outline);
        }
        KnowledgeGraph knowledge = new KnowledgeGraph(outlines);
        for (int i = 0; i < outlines.size(); i++) {
            inferrer.infer(outlines.get(i), selected.get(i), graph, knowledge, architecture, llmAllowed);
        }
        logger.info("Understood {} files ({} JavaParser, {} lexical, {} failed); LLM: {}",
                outlines.size(), ast, lexical, failed, llm.mode());
        return new UnderstandingResult(knowledge, ast, lexical, failed, llm.mode());
    }

    private List<FileRank> select(RankingResult ranking) {
        List<FileRank> selected = new ArrayList<>();
        for (FileRank rank : ranking.getFiles()) {
            if (rank.getLevel() == DocLevel.LISTED && !config.isIncludeListed()) continue;
            selected.add(rank);
            if (selected.size() >= config.getMaxFiles()) break;
        }
        return selected;
    }

    private FileOutline parse(FileRank rank) {
        FileInfo file = rank.getFile();
        String source = SourceFiles.read(file.getFilePath());
        if (source == null) {
            return new FileOutline(file, rank.getLevel(), FileOutline.ENGINE_LEXICAL, false, "",
                    new ArrayList<>(), new ArrayList<>());
        }
        if ("Java".equals(file.getLanguage())) {
            return java.outline(file, rank.getLevel(), source);
        }
        return new LexicalUnderstander(LanguageFamily.of(file.getLanguage()))
                .outline(file, rank.getLevel(), source);
    }

    static LlmClient llmClient(AppConfig config, boolean llmAllowed) {
        if (!llmAllowed || !config.getLlm().isEnabled()) {
            return LlmClient.disabled();
        }
        if (!"openai".equalsIgnoreCase(config.getLlm().getProvider())) {
            logger.warn("Unknown llm.provider '{}'; using heuristic responsibilities", config.getLlm().getProvider());
            return LlmClient.disabled();
        }
        return new OpenAiLlmClient(config.getLlm());
    }
}
