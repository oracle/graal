package com.oracle.svm.jmh;

import org.openjdk.jmh.runner.BenchmarkList;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Substitutes the {@link BenchmarkList} of JMH so that it loads the benchmarks from the
 * {@link Benchmarks} utility class.
 */
@TargetClass(BenchmarkList.class)
public final class Target_org_openjdk_jmh_runner_BenchmarkList {

    @Substitute
    public static BenchmarkList defaultList() {
        return BenchmarkList.fromString(Benchmarks.string());
    }

}
