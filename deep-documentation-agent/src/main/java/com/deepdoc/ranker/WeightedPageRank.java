package com.deepdoc.ranker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Personalized PageRank on a weighted directed graph, solved by power iteration.
 *
 * <pre>
 * r(v) = (1 - d) * p(v) + d * sum over u->v of r(u) * w(u,v) / out(u)
 * </pre>
 * {@code p} is the normalized prior (teleport) vector, {@code d} the damping factor (the
 * probability of following an edge instead of jumping), {@code w(u,v)} the edge weight and
 * {@code out(u)} the total outgoing weight of {@code u}. Rank held by nodes without outgoing
 * edges ("dangling" nodes) is redistributed by the prior as well, so the scores always sum to 1.
 *
 * <p>Implemented by hand rather than with JGraphT's PageRank because we need both edge weights
 * and a non-uniform prior (personalization), and want the exact formula to be visible and tested.
 */
public final class WeightedPageRank {
    /** Power iteration converges geometrically (rate d); 200 rounds is far more than graphs here need. */
    static final int MAX_ITERATIONS = 200;
    /** Stop when the L1 change between two iterations falls below this. */
    static final double TOLERANCE = 1e-12;

    private WeightedPageRank() {
    }

    /**
     * @param nodes  all nodes, including isolated ones
     * @param edges  {@code edges.get(u).get(v)} = weight of u -> v
     * @param prior  unnormalized teleport weight per node (missing = 1)
     * @param damping probability of following an edge, typically 0.85
     * @return rank per node, in {@code nodes} order, summing to 1
     */
    public static <V> Map<V, Double> compute(List<V> nodes, Map<V, Map<V, Double>> edges,
                                             Map<V, Double> prior, double damping) {
        int n = nodes.size();
        Map<V, Double> result = new LinkedHashMap<>();
        if (n == 0) {
            return result;
        }
        Map<V, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            index.put(nodes.get(i), i);
        }

        // Normalize the prior into a probability vector.
        double[] p = new double[n];
        double priorSum = 0;
        for (int i = 0; i < n; i++) {
            p[i] = Math.max(0, prior.getOrDefault(nodes.get(i), 1.0));
            priorSum += p[i];
        }
        for (int i = 0; i < n; i++) {
            p[i] = priorSum > 0 ? p[i] / priorSum : 1.0 / n;
        }

        // Adjacency as arrays of target indices and transition probabilities (weight / out-weight),
        // so the inner loop below does no map lookups.
        int[][] targets = new int[n][];
        double[][] shares = new double[n][];
        for (int i = 0; i < n; i++) {
            Map<V, Double> out = edges.getOrDefault(nodes.get(i), new HashMap<>());
            double total = out.values().stream().mapToDouble(Double::doubleValue).sum();
            targets[i] = new int[out.size()];
            shares[i] = new double[out.size()];
            int k = 0;
            for (Map.Entry<V, Double> e : out.entrySet()) {
                targets[i][k] = index.get(e.getKey());
                shares[i][k] = total > 0 ? e.getValue() / total : 0;
                k++;
            }
            // Edges with zero total weight are treated as no edges: the node is dangling.
            if (total <= 0) {
                targets[i] = new int[0];
                shares[i] = new double[0];
            }
        }

        double[] rank = new double[n];
        Arrays.fill(rank, 1.0 / n);
        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            // Rank sitting on dangling nodes would leak out of the system; hand it back via the prior.
            double dangling = 0;
            for (int i = 0; i < n; i++) {
                if (targets[i].length == 0) dangling += rank[i];
            }
            double[] next = new double[n];
            for (int i = 0; i < n; i++) {
                next[i] = (1 - damping) * p[i] + damping * dangling * p[i];
            }
            for (int i = 0; i < n; i++) {
                for (int k = 0; k < targets[i].length; k++) {
                    next[targets[i][k]] += damping * rank[i] * shares[i][k];
                }
            }
            double delta = 0;
            for (int i = 0; i < n; i++) {
                delta += Math.abs(next[i] - rank[i]);
            }
            rank = next;
            if (delta < TOLERANCE) {
                break;
            }
        }

        for (int i = 0; i < n; i++) {
            result.put(nodes.get(i), rank[i]);
        }
        return result;
    }
}
