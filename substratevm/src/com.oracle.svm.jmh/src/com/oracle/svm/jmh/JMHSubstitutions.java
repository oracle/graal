package com.oracle.svm.jmh;

import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.util.Optional;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitutes {@link CommandLineOptions} so that benchmarks are always run in embedded mode, as
 * forking is currently not supported.
 */
@TargetClass(CommandLineOptions.class)
final class Target_org_openjdk_jmh_runner_options_CommandLineOptions {

    @Substitute
    @SuppressWarnings("static-method")
    Optional<Integer> getForkCount() {
        return Optional.of(0);
    }

}

/**
 * Substitutes {@link OptionsBuilder} so that benchmarks are always run in embedded mode, as forking
 * is currently not supported.
 */
@TargetClass(OptionsBuilder.class)
final class Target_org_openjdk_jmh_runner_options_OptionsBuilder {

    @Substitute
    @SuppressWarnings("static-method")
    Optional<Integer> getForkCount() {
        return Optional.of(0);
    }

}

/**
 * Substitutes the {@link BenchmarkList} of JMH so that it loads the benchmarks from the
 * {@link Benchmarks} utility class.
 */
@TargetClass(BenchmarkList.class)
final class Target_org_openjdk_jmh_runner_BenchmarkList {

    @Substitute
    public static BenchmarkList defaultList() {
        return BenchmarkList.fromString(Benchmarks.string());
    }

}

/**
 * Dummy class for JMH substitutions.
 */
public final class JMHSubstitutions {
}
