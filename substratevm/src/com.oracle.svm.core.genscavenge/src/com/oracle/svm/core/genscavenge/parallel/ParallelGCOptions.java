package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.option.RuntimeOptionKey;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;

public class ParallelGCOptions {

    @Option(help = "Number of worker threads used by ParallelGC.", type = OptionType.User)
    public static final RuntimeOptionKey<Integer> ParallelGCWorkers = new RuntimeOptionKey<>(0);
}
