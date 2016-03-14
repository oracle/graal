/*
 * Example graph analytics query program using Truffle for speeding up queries.
 */
package com.oracle.truffle.example.simplegraphanalitycs;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import java.util.function.Supplier;

public class GraphSampleApp {
    
    public static final int MEASUREMENT_COUNT = 100;
    public static final int QUERIES_PER_MEASUREMENT = 100;

    public static void main(String[] args) {
        Graph graph = createRandomGraph();
        Query q1 = new Query(graph, 8);
        Query q2 = new Query(graph, 2);
        run(q1, q2);
    }
    
    public static void run(Query q1, Query q2) {
        RootCallTarget truffleQ1 = TruffleExt.createCallTarget(q1);
        RootCallTarget truffleQ2 = TruffleExt.createCallTarget(q2);
        for (int i = 0; i < MEASUREMENT_COUNT; ++i) {
            long start = System.currentTimeMillis();
            long sum = 0;
            for (int j = 0; j < QUERIES_PER_MEASUREMENT; ++j) {
                sum += (int) truffleQ1.call() + (int) truffleQ2.call();
            }
            long truffleTime = (System.currentTimeMillis() - start);
            
            start = System.currentTimeMillis();
            for (int j = 0; j < QUERIES_PER_MEASUREMENT; ++j) {
                sum -= (int) q1.get() + (int) q2.get();
            }
            long plainTime = (System.currentTimeMillis() - start);
            if (sum == 0) {
                System.out.println("time (truffle " + truffleTime + ") vs (plain " + plainTime + ")");
            } else {
                System.out.println("validation failed!");
            }
        }
    }

    /**
     * Representation of the graph data structure. Node values represented as {@link #nodeValues}.
     * Edges represented as {@link #nodeEdges} with an array of destination indices for each edge.
     */
    public static class Graph {

        private final int[] nodeValues;
        private final int[][] nodeEdges;

        private Graph(int[] nodeValues, int[][] nodeEdges) {
            this.nodeValues = nodeValues;
            this.nodeEdges = nodeEdges;
        }
        
    }
    
    /**
     * Representation of the query including all data that is constant per query reachable via final fields.
     * The query sums up the value of all edge endpoint values where the sum of source and destination node value is divisible
     * by {@link #divisor}.
     */
    public static class Query implements Supplier {
        
        private final Graph graph;
        private final int divisor;
        
        public Query(Graph graph, int divisor) {
            this.divisor = divisor;
            this.graph = graph;
        }

        /**
         * Truffle entry point for the query. This is the root method of the Truffle compilation.
         * The current query object represented as {@code this} and all final or @CompilationFinal fields
         * reachable from this object become constants during compilation.
         * 
         * @return the value of the query
         */
        @Override
        public Object get() {
            int result = 0;
            for (int i = 0; i < graph.nodeValues.length; ++i) {
                result += divisibleEdgeSumAll(i, divisor);
            }
            return result;
        }
        
        public int divisibleEdgeSumAll(int position, int divisor) {
            Result r = new Result();
            
            // Optional assertion to have better reasoning about the compiler graph.
            assert CompilerDirectives.isCompilationConstant(r.getValue()) : "after the escape analysis, the initial value of r is known as a compile-time constant";
            
            int count = graph.nodeEdges[position].length;
            int ownValue = graph.nodeValues[position];
            
            for (int i = 0; i < count; ++i) {
                r.add(divisibleEdgeSumSingle(i, position, divisor, ownValue));
            }
            
            // Optional assertion that we are sure that the complex result object r is virtualized and never allocated.
            CompilerDirectives.ensureVirtualized(r);
            
            return r.getValue();
        }
        
        public int divisibleEdgeSumSingle(int index, int position, int divisor, int ownValue) {
            int dest = graph.nodeEdges[position][index];
            
            int neighborValue = graph.nodeValues[dest];
            
            // Optional assertions to have better reasoning about the compiler graph.
            assert CompilerDirectives.isPartialEvaluationConstant(divisor) : "we know that divisor is a compile-time constant here";
            assert CompilerDirectives.isPartialEvaluationConstant(graph) : "we know the graph for this query is a compile-time constant here";
            assert CompilerDirectives.isPartialEvaluationConstant(graph.nodeEdges) : "we know the edges array is a compile-time constant here";
            assert CompilerDirectives.isPartialEvaluationConstant(graph.nodeValues) : "we know the edges array is a compile-time constant here";
            assert CompilerDirectives.isPartialEvaluationConstant(graph.nodeEdges.length) : "we know the length of the edges array is a compile-time constant here";
            assert CompilerDirectives.isPartialEvaluationConstant(graph.nodeValues.length) : "we know the length of the edges array is a compile-time constant here";
            
            if (ownValue + neighborValue % divisor == 0) {
                return neighborValue;
            }
            
            return 0;
        }
    }
    
    /**
     * Result object showing that abstractions do not incur a performance penalty.
     */
    public static class Result {
        private int value;
        
        public Result() {
            value = 0;
        }
        
        public void add(int newValue) {
            this.value += newValue;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * Creates a new random graph.
     * @return the new graph
     */
    public static Graph createRandomGraph() {
        final int nodeCount = 100000;
        final int[] nodeValues = new int[nodeCount];
        final int[][] nodeEdges = new int[nodeCount][];
        
        for (int i = 0; i < nodeValues.length; ++i) {
            nodeValues[i] = (int) (Math.random() * 100);
            int curEdgeCount = 0;
            if (Math.random() < 0.5) {
                curEdgeCount = 1;
            } else if (Math.random() < 0.5) {
                curEdgeCount = 2;
            } else {
                curEdgeCount = 3;
            }
            
            nodeEdges[i] = new int[curEdgeCount];
            for (int j = 0; j < curEdgeCount; ++j) {
                nodeEdges[i][j] = (int) (Math.random() * nodeCount);
            }
        }
        
        Graph graph = new Graph(nodeValues, nodeEdges);
        return graph;
    }
}
