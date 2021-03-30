package com.oracle.svm.core;

import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.sampling.CallStackFrameMethodInfo;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

public class RuntimeAnalysisWorkarounds {
    public static class Options {
        @Option(help = "Use the option to avoid the initial value of the enterSamplingCodeMethodId constant folding. " +
                        "The value of this option must never be set to true in order to keep the correct information in the variable.")//
        static final RuntimeOptionKey<Boolean> ConstantFoldSamplingCodeStartId = new RuntimeOptionKey<>(false);

    }

    public static void avoidFoldingSamplingCodeStart() {
        /*
         * Avoid constant folding the initial value of the enterSamplingCodeMethodId. The true value
         * of the id is set during the image build, and is being used in the runtime. By "falsely"
         * setting the value of the id in runtime, the analysis is "tricked" to never perform the
         * folding. The condition must always be false, and it can't be proved as false.
         */
        if (Options.ConstantFoldSamplingCodeStartId.getValue()) {
            ImageSingletons.lookup(CallStackFrameMethodInfo.class).setEnterSamplingCodeMethodId(0);
        }
    }
}
