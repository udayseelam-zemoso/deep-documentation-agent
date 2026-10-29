package com.deepdoc.understand;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.UnderstandSection;
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
 * Day 6-7 pipeline: parse ranked <em>Java</em> files with JavaParser, extract facts, hang
 * unique type names off a knowledge graph.
 *
 * <p>Only DEEP and SUMMARY files are parsed by default. LISTED files will appear as names
 * in a package listing later; they are not worth an AST walk on a large repo. Non-Java
 * files are counted and skipped — lexical outlines are Day 8-10.
 */
public class JavaUnderstandingService {
    private static final Logger logger = LoggerFactory.getLogger(JavaUnderstandingService.class);

    private final UnderstandSection config;
    private final FileUnderstander java;

    public JavaUnderstandingService(AppConfig config) {
        this(config.getUnderstand(), new JavaAstUnderstander());
    }

    JavaUnderstandingService(UnderstandSection config, FileUnderstander java) {
        this.config = config;
        this.java = java;
    }

    public UnderstandingResult understand(RankingResult ranking) {
        List<FileOutline> outlines = new ArrayList<>();
        int ast = 0;
        int skipped = 0;
        int failed = 0;
        for (FileRank rank : select(ranking)) {
            if (!"Java".equals(rank.getFile().getLanguage())) {
                skipped++;
                continue;
            }
            FileOutline outline = parse(rank);
            if (!outline.isParsed()) {
                failed++;
            } else {
                ast++;
            }
            outlines.add(outline);
        }
        KnowledgeGraph knowledge = KnowledgeGraph.from(outlines);
        logger.info("Understood {} Java files ({} failed, {} non-Java skipped); {} facts, {} use edges",
                outlines.size(), failed, skipped, knowledge.getFacts().size(), knowledge.useEdgeCount());
        return new UnderstandingResult(knowledge, ast, skipped, failed);
    }

    private List<FileRank> select(RankingResult ranking) {
        List<FileRank> selected = new ArrayList<>();
        for (FileRank rank : ranking.getFiles()) {
            if (rank.getLevel() == DocLevel.LISTED && !config.isIncludeListed()) {
                continue;
            }
            selected.add(rank);
            if (selected.size() >= config.getMaxFiles()) {
                break;
            }
        }
        return selected;
    }

    private FileOutline parse(FileRank rank) {
        FileInfo file = rank.getFile();
        String source = SourceFiles.read(file.getFilePath());
        if (source == null) {
            return new FileOutline(file, rank.getLevel(), FileOutline.ENGINE_JAVAPARSER, false, "",
                    new ArrayList<>(), new ArrayList<>());
        }
        return java.outline(file, rank.getLevel(), source);
    }
}
