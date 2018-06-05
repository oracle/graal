package org.graalvm.compiler.truffle.pelang.benchmark;

import org.graalvm.compiler.truffle.pelang.util.PELangSample;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangNCFBenchmark {

    public static class SimpleAddBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleAdd();
        }

    }

    public static class SimpleBlockBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleBlock();
        }

    }

    public static class SimpleLocalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleLocalReadWrite();
        }

    }

    public static class SimpleGlobalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleGlobalReadWrite();
        }

    }

    public static class SimpleBranchBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleBranch();
        }

    }

    public static class SimpleLoopBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleLoop();
        }

    }

    public static class SimpleSwitchBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleSwitch();
        }

    }

    public static class SimpleInvokeBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleInvoke();
        }

    }

    public static class SimpleObjectBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleObject();
        }

    }

    public static class SimpleArrayReadBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleArrayRead();
        }

    }

    public static class SimpleMultiArrayReadBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleMultiArrayRead();
        }

    }

    public static class SimpleArrayWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleArrayWrite();
        }

    }

    public static class SimpleMultiArrayWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.simpleMultiArrayWrite();
        }

    }

    public static class ComplexStringArrayBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.complexStringArray();
        }

    }

    public static class NestedAddsBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.nestedAdds();
        }

    }

    public static class NestedBlocksBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.nestedBlocks();
        }

    }

    public static class NestedLocalReadWritesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.nestedLocalReadWrites();
        }

    }

    public static class NestedBranchesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.nestedBranches();
        }

    }

    public static class NestedLoopsBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.nestedLoops();
        }

    }

    public static class NestedSwitchesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.nestedSwitches();
        }

    }

    public static class BranchWithGlobalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.branchWithGlobalReadWrite();
        }

    }

    public static class LoopWithGlobalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.loopWithGlobalReadWrite();
        }

    }

    public static class NestedLoopsWithMultipleBackEdgesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.nestedLoopsWithMultipleBackEdges();
        }

    }

    public static class InvokeObjectFunctionPropertyBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            return PELangSample.invokeObjectFunctionProperty();
        }

    }

}
