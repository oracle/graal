package com.oracle.svm.jmh;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.runner.ActionPlan;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.util.Multimap;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitutes the JMH runner so that it always runs in embedded mode, as forking is currently not
 * supported.
 */
@TargetClass(Runner.class)
public final class Target_org_openjdk_jmh_runner_Runner {

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
