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
 * Day 6-7 pipeline: parse ranked <em>Java</em> files with JavaParser, extract facts at the
 * file's documentation level, hang unique type names off a knowledge graph.
 *
 * <p>Week 1 already stamped each file DEEP, SUMMARY or LISTED. This step follows that:
 * DEEP and SUMMARY are parsed (full method facts on DEEP, a paragraph's facts on SUMMARY).
 * LISTED files stay as names for a later package listing. Non-Java files are counted and
 * skipped — lexical outlines are Day 8-10.
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
        int skippedNonJava = 0;
        int skippedListed = 0;
        int failed = 0;
        for (FileRank rank : ranking.getFiles()) {
            if (rank.getLevel() == DocLevel.LISTED && !config.isIncludeListed()) {
                skippedListed++;
                continue;
            }
            if (outlines.size() + skippedNonJava >= config.getMaxFiles()) {
                break;
            }
            if (!"Java".equals(rank.getFile().getLanguage())) {
                skippedNonJava++;
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
        int deep = 0;
        int summary = 0;
        for (FileOutline outline : outlines) {
            if (outline.getLevel() == DocLevel.DEEP) {
                deep++;
            } else if (outline.getLevel() == DocLevel.SUMMARY) {
                summary++;
            }
        }
        logger.info("Understood {} Java files ({} DEEP, {} SUMMARY, {} LISTED skipped, {} non-Java skipped, {} failed); {} facts, {} use edges",
                outlines.size(), deep, summary, skippedListed, skippedNonJava, failed,
                knowledge.getFacts().size(), knowledge.useEdgeCount());
        return new UnderstandingResult(knowledge, ast, skippedNonJava, skippedListed, failed);
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
