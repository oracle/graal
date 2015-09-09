package com.oracle.graal.microbenchmarks.graal;

import java.util.*;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.oracle.graal.graph.*;
import com.oracle.graal.microbenchmarks.graal.util.*;
import com.oracle.graal.microbenchmarks.graal.util.NodesState.NodePair;

public class NodeBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class StringEquals extends NodesState {
    }

    @Benchmark
    @Warmup(iterations = 20)
    public int getNodeClass(StringEquals s) {
        int sum = 0;
        for (Node n : s.nodes) {
            sum += n.getNodeClass().iterableId();
        }
        return sum;
    }

    @Benchmark
    public void inputs(StringEquals s, Blackhole bh) {
        for (Node n : s.nodes) {
            for (Node input : n.inputs()) {
                bh.consume(input);
            }
        }
    }

    @Benchmark
    public void usages(StringEquals s, Blackhole bh) {
        for (Node n : s.nodes) {
            for (Node input : n.usages()) {
                bh.consume(input);
            }
        }
    }

    @MethodSpec(declaringClass = HashMap.class, name = "computeIfAbsent")
    public static class HashMapComputeIfAbsent extends NodesState {
    }

    @Benchmark
    @Warmup(iterations = 20)
    public void nonNullInputs(HashMapComputeIfAbsent s, Blackhole bh) {
        for (Node n : s.nodes) {
            for (Node input : n.inputs().nonNull()) {
                bh.consume(input);
            }
        }
    }

    @Benchmark
    @Warmup(iterations = 20)
    public int valueEquals_STRING_EQUALS(StringEquals s) {
        int result = 0;
        for (NodePair np : s.valueEqualsNodePairs) {
            if (np.n1.valueEquals(np.n2)) {
                result += 27;
            } else {
                result += 31;
            }
        }
        return result;
    }

    @Benchmark
    @Warmup(iterations = 20)
    public int valueEquals_HASHMAP_COMPUTE_IF_ABSENT(HashMapComputeIfAbsent s) {
        int result = 0;
        for (NodePair np : s.valueEqualsNodePairs) {
            if (np.n1.valueEquals(np.n2)) {
                result += 27;
            } else {
                result += 31;
            }
        }
        return result;
    }

    @Benchmark
    @Warmup(iterations = 20)
    public int valueNumberLeaf_HASHMAP_COMPUTE_IF_ABSENT(HashMapComputeIfAbsent s) {
        int result = 0;
        for (Node n : s.valueNumberableLeafNodes) {
            result += (n.getNodeClass().isLeafNode() ? 1 : 0);
        }
        return result;
    }

    @Benchmark
    @Warmup(iterations = 20)
    public int valueNumberLeaf_STRING_EQUALS(StringEquals s) {
        int result = 0;
        for (Node n : s.valueNumberableLeafNodes) {
            result += (n.getNodeClass().isLeafNode() ? 1 : 0);
        }
        return result;
    }
}
