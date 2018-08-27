package com.oracle.svm.jmh;

import java.util.Properties;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.runner.ActionPlan;
import org.openjdk.jmh.runner.BenchmarkList;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.util.Multimap;
import org.openjdk.jmh.util.Utils;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitutes the JMH runner so that it always runs in embedded mode, as forking is currently not
 * supported.
 */
@TargetClass(Runner.class)
final class Target_org_openjdk_jmh_runner_Runner {

    @Substitute
    private Multimap<BenchmarkParams, BenchmarkResult> runSeparate(ActionPlan actionPlan) {
        return Target_org_openjdk_jmh_runner_BaseRunner.class.cast(this).runBenchmarksEmbedded(actionPlan);
    }

    @TargetClass(className = "org.openjdk.jmh.runner.BaseRunner")
    static final class Target_org_openjdk_jmh_runner_BaseRunner {

        @Alias
        protected native Multimap<BenchmarkParams, BenchmarkResult> runBenchmarksEmbedded(ActionPlan actionPlan);

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
 * Substitutes {@link Utils#getRecordedSystemProperties()} to set up properties with non-null
 * values, as Substrate currently returns null for some system properties.
 */
@TargetClass(Utils.class)
final class Target_org_openjdk_jmh_util_Utils {

    @Substitute
    public static Properties getRecordedSystemProperties() {
        Properties properties = new Properties();
        properties.put("java.vm.name", System.getProperty("java.vm.name"));

        // set up custom values as Substrate returns null for these properties
        properties.put("java.version", "unknown");
        properties.put("java.vm.version", "unknown");

        return properties;
    }

}

/**
 * Dummy class for JMH substitutions.
 */
public final class JMHSubstitutions {
}
