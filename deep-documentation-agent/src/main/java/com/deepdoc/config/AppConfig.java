package com.deepdoc.config;

/**
 * Root object for {@code application.yml}.
 * Property names match the YAML keys so SnakeYAML can bind them.
 */
public class AppConfig {
    private AppSection app = new AppSection();
    private AnalysisSection analysis = new AnalysisSection();
    private GitSection git = new GitSection();
    private GraphSection graph = new GraphSection();
    private RankingSection ranking = new RankingSection();
    private OutputSection output = new OutputSection();
    private LlmSection llm = new LlmSection();

    public AppSection getApp() {
        return app;
    }

    public void setApp(AppSection app) {
        this.app = app;
    }

    public AnalysisSection getAnalysis() {
        return analysis;
    }

    public void setAnalysis(AnalysisSection analysis) {
        this.analysis = analysis;
    }

    public GitSection getGit() {
        return git;
    }

    public void setGit(GitSection git) {
        this.git = git;
    }

    public GraphSection getGraph() {
        return graph;
    }

    public void setGraph(GraphSection graph) {
        this.graph = graph;
    }

    public RankingSection getRanking() {
        return ranking;
    }

    public void setRanking(RankingSection ranking) {
        this.ranking = ranking;
    }

    public OutputSection getOutput() {
        return output;
    }

    public void setOutput(OutputSection output) {
        this.output = output;
    }

    public LlmSection getLlm() {
        return llm;
    }

    public void setLlm(LlmSection llm) {
        this.llm = llm;
    }
}
