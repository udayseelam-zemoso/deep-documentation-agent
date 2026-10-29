package com.deepdoc.docs;

import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.PackageEdge;
import com.deepdoc.graph.PackageNode;
import com.deepdoc.ranker.PackageRank;
import com.deepdoc.ranker.RankingResult;
import org.jgrapht.Graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mermaid flowcharts for the generated Markdown. Only the package graph is drawn: it is the
 * view a new reader can hold in their head. File-level graphs become unreadable past a few
 * dozen nodes, so those stay as lists on each package page.
 *
 * <p>Node ids are sanitised ({@code order.service} → {@code order_service}) because Mermaid
 * treats a dot as syntax. Labels still show the real package name and its role.
 */
public final class MermaidRenderer {
    private MermaidRenderer() {
    }

    /**
     * Left-to-right flowchart of ranked packages. Isolated packages still appear as nodes so
     * the diagram matches the package table.
     */
    public static String packageFlow(DependencyGraph graph, RankingResult ranking) {
        if (graph == null || ranking == null || ranking.getPackages().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");
        for (PackageRank pack : ranking.getPackages()) {
            String id = nodeId(pack.getName());
            String label = escapeLabel(pack.getName() + "\\n" + pack.getRole().name());
            sb.append("    ").append(id).append("[\"").append(label).append("\"]\n");
        }
        Graph<PackageNode, PackageEdge> packages = graph.getPackageGraph();
        List<PackageEdge> edges = new ArrayList<>(packages.edgeSet());
        edges.sort(Comparator
                .comparing((PackageEdge e) -> packages.getEdgeSource(e).getName())
                .thenComparing(e -> packages.getEdgeTarget(e).getName()));
        for (PackageEdge edge : edges) {
            String from = nodeId(packages.getEdgeSource(edge).getName());
            String to = nodeId(packages.getEdgeTarget(edge).getName());
            sb.append("    ").append(from).append(" --> ").append(to).append('\n');
        }
        return sb.toString();
    }

    /** Stable Mermaid id: letters, digits and underscore only; never starts with a digit. */
    public static String nodeId(String name) {
        if (name == null || name.isEmpty()) {
            return "pkg";
        }
        StringBuilder slug = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                slug.append(c);
            } else {
                slug.append('_');
            }
        }
        String id = slug.toString();
        while (id.contains("__")) {
            id = id.replace("__", "_");
        }
        if (id.startsWith("_")) {
            id = id.substring(1);
        }
        if (id.endsWith("_")) {
            id = id.substring(0, id.length() - 1);
        }
        if (id.isEmpty() || Character.isDigit(id.charAt(0))) {
            id = "pkg_" + id;
        }
        return id;
    }

    static String escapeLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.replace("\"", "'").replace("]", "");
    }
}
