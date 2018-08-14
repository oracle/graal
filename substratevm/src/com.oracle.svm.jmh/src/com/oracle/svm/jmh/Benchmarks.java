package com.oracle.svm.jmh;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openjdk.jmh.runner.BenchmarkList;

import com.oracle.svm.core.util.UserError;

/**
 * Utility class for reading the default JMH benchmark list from the given benchmark resource. This
 * prevents NPEs in the JMH runner caused by the usage of a class loader that is null in SVM.
 */
public final class Benchmarks {

    // create a substring since string starts with a '/'
    public static final String RESOURCE = BenchmarkList.BENCHMARK_LIST.substring(1);

    public static InputStream inputStream() {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(RESOURCE);

        if (stream == null) {
            UserError.abort("could not find benchmark list, check that the JMH benchmark jar is provided in the classpath");
        }
        return stream;
    }

    public static List<String> strings() {
        try (Stream<String> benchmarks = benchmarks()) {
            return benchmarks.collect(Collectors.toList());
        }
    }

    public static String string() {
        try (Stream<String> benchmarks = benchmarks()) {
            return benchmarks.collect(Collectors.joining(System.lineSeparator()));
        }
    }

    private static Stream<String> benchmarks() {
        return new BufferedReader(new InputStreamReader(inputStream())).lines().filter(line -> !line.startsWith("#") && !line.trim().isEmpty());
    }

}
