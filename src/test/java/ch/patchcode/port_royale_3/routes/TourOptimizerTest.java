package ch.patchcode.port_royale_3.routes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import ch.patchcode.graphs.trees.Tree;
import ch.patchcode.graphs.trees.TreeUtils;
import ch.patchcode.port_royale_3.routes.DistanceGraph.Edge;
import ch.patchcode.port_royale_3.routes.DistanceGraph.Vertex;

public class TourOptimizerTest {

    private DistanceGraph graph;

    @Before
    public void setup() throws IOException {
        try (InputStream is = PortRoyaleTriangleInequalityTest.class.getClassLoader()
                .getResourceAsStream("port-royale-3-distances.csv")) {
            graph = new DistanceGraph(new DistanceCsvData(is));
        }
    }

    @Test
    public void outputTree() throws IOException {
        Tree<Vertex> tree = new GreedyMinimumDistanceSpanningTree(graph);

        Map<Vertex, List<Vertex>> links = new HashMap<>();
        TreeUtils.visitAllEdges(tree, (a, b) -> {
            links.computeIfAbsent(a, $ -> new ArrayList<>()).addAll(Arrays.asList(b, b));
            links.computeIfAbsent(b, $ -> new ArrayList<>()).addAll(Arrays.asList(a, a));
        });

        List<Vertex> redundantVertices = links.entrySet().stream().filter(it -> it.getValue().size() / 2 > 1)
                .map(it -> it.getKey()).collect(Collectors.toList());
        while (redundantVertices.size() > 0) {
            List<ShortCutMetric> metrics = new ArrayList<>();
            for (Vertex v : redundantVertices) {

                Set<Vertex> neighbours = links.get(v).stream().distinct().collect(Collectors.toSet());

                List<Edge> edges = v.getEdges().stream()
                        .filter(it -> it.getVertices().stream().anyMatch(it2 -> neighbours.contains(it2)))
                        .collect(Collectors.toList());

                for (int i = 0; i < edges.size(); ++i) {
                    Edge edge1 = edges.get(i);
                    Vertex v1 = edge1.getVertices().stream().filter(it -> !it.equals(v)).findFirst().get();
                    for (int j = i + 1; j < edges.size(); ++j) {
                        Edge edge2 = edges.get(j);
                        Vertex v2 = edge2.getVertices().stream().filter(it -> !it.equals(v)).findFirst().get();
                        Edge edge3 = v1.getEdges().stream().filter(it -> it.getVertices().contains(v2)).findFirst().get();
                        
                        double benefit = Math.max(0, edge1.getWeight()+edge2.getWeight()-edge3.getWeight());
                        metrics.add(new ShortCutMetric(v, Arrays.asList(v1, v2), benefit));
                    }
                }
            }

            // Problem: may (will) split the graph
            ShortCutMetric top = metrics.stream().sorted().findFirst().get();
            System.out.println(top);

            List<Vertex> c = links.get(top.center);
            c.remove(top.neighbours.get(0));
            c.remove(top.neighbours.get(1));
            links.get(top.neighbours.get(0)).remove(top.center);
            links.get(top.neighbours.get(1)).remove(top.center);
            links.get(top.neighbours.get(0)).add(top.neighbours.get(1));
            links.get(top.neighbours.get(1)).add(top.neighbours.get(0));

            redundantVertices = links.entrySet().stream().filter(it -> it.getValue().size() / 2 > 1)
                    .map(it -> it.getKey()).collect(Collectors.toList());
        }

        printChain(links);
    }

    private void printChain(Map<Vertex, List<Vertex>> links) {
        List<Vertex> visited = new ArrayList<>();
        Optional<Vertex> node = links.entrySet().stream().findFirst().map(it -> it.getKey());
        while (node.isPresent())  {
            System.out.println("visit " + node.get().getName() + " (" + links.get(node.get()).stream().map(it -> it.getName()).collect(Collectors.joining(", ")) + ")");
            visited.add(node.get());
            node = links.get(node.get()).stream().filter(it -> !visited.contains(it)).findFirst();
        }
    }
    
    public static class ShortCutMetric implements Comparable<ShortCutMetric> {

        public final Vertex center;
        public final List<Vertex> neighbours;
        public final double benefit;

        public ShortCutMetric(Vertex center, List<Vertex> neighbours, double benefit) {
            this.center = center;
            this.neighbours = Collections.unmodifiableList(neighbours.stream().sorted().collect(Collectors.toList()));
            this.benefit = benefit;
        }

        @Override
        public int compareTo(ShortCutMetric o) {
            int byBenefitDescending = Double.compare(-benefit, -o.benefit);
            if (byBenefitDescending != 0) return byBenefitDescending;

            int byCenter = center.compareTo(o.center);
            if (byCenter != 0) return byCenter;

            int byFirstNeighbour = neighbours.get(0).compareTo(o.neighbours.get(0));
            if (byFirstNeighbour != 0) return byFirstNeighbour;

            return neighbours.get(1).compareTo(o.neighbours.get(1));
        }

        @Override
        public String toString() {
            // TODO Auto-generated method stub
            return String.format("ShortCutMetric[%s-(%s)-%s saves %.1f]", neighbours.get(0).getName(), center.getName(), neighbours.get(1).getName(), benefit);
        }
    }
}