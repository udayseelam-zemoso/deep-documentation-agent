package com.deepdoc.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * One entry of {@code architectures.yml}. Setter-based so SnakeYAML can bind it.
 */
public class ArchitecturePattern {
    private String name;
    private String kind = "framework";
    private String architecture;
    private String layer = "";
    /** Pattern names this one replaces when both match (Spring Boot replaces Spring). */
    private List<String> suppresses = new ArrayList<>();
    private List<ArchitectureSignal> signals = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind == null ? "framework" : kind;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer == null ? "" : layer;
    }

    public List<String> getSuppresses() {
        return suppresses;
    }

    public void setSuppresses(List<String> suppresses) {
        this.suppresses = suppresses == null ? new ArrayList<>() : suppresses;
    }

    public List<ArchitectureSignal> getSignals() {
        return signals;
    }

    public void setSignals(List<ArchitectureSignal> signals) {
        this.signals = signals == null ? new ArrayList<>() : signals;
    }
}
