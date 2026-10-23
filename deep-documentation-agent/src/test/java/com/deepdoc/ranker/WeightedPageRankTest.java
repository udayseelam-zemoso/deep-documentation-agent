package com.deepdoc.ranker;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class WeightedPageRankTest {

    private static Map<String, Map<String, Double>> edges(Object... triples) {
        Map<String, Map<String, Double>> edges = new HashMap<>();
        for (int i = 0; i < triples.length; i += 3) {
            edges.computeIfAbsent((String) triples[i], k -> new HashMap<>())
                    .put((String) triples[i + 1], (Double) triples[i + 2]);
        }
        return edges;
    }

    @Test
    void symmetricCycleGivesEqualRanks() {
        List<String> nodes = Arrays.asList("a", "b", "c");
        Map<String, Double> rank = WeightedPageRank.compute(nodes,
                edges("a", "b", 1.0, "b", "c", 1.0, "c", "a", 1.0), new HashMap<>(), 0.85);

        nodes.forEach(n -> assertThat(rank.get(n)).isCloseTo(1.0 / 3, within(1e-9)));
    }

    @Test
    void danglingRankIsRedistributedSoScoresSumToOne() {
        List<String> nodes = Arrays.asList("a", "b", "c", "isolated");
        Map<String, Double> rank = WeightedPageRank.compute(nodes,
                edges("a", "c", 1.0, "b", "c", 1.0), new HashMap<>(), 0.85);

        assertThat(rank.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
        assertThat(rank.get("c")).isGreaterThan(rank.get("a"));
        assertThat(rank.get("a")).isCloseTo(rank.get("isolated"), within(1e-9));
    }

    @Test
    void heavierEdgeAndPriorShiftRank() {
        List<String> nodes = Arrays.asList("src", "heavy", "light");
        Map<String, Double> rank = WeightedPageRank.compute(nodes,
                edges("src", "heavy", 3.0, "src", "light", 1.0), new HashMap<>(), 0.85);
        assertThat(rank.get("heavy")).isGreaterThan(rank.get("light"));

        Map<String, Double> prior = new HashMap<>();
        prior.put("light", 10.0);
        Map<String, Double> biased = WeightedPageRank.compute(nodes,
                edges("src", "heavy", 3.0, "src", "light", 1.0), prior, 0.85);
        assertThat(biased.get("light")).isGreaterThan(biased.get("heavy"));
    }
}
