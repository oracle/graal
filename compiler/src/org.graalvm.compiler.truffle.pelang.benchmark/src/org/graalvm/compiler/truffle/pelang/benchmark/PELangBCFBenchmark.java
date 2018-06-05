package org.graalvm.compiler.truffle.pelang.benchmark;

import org.graalvm.compiler.truffle.pelang.util.PELangBCFGenerator;
import org.graalvm.compiler.truffle.pelang.util.PELangSample;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangBCFBenchmark {

    public static class SimpleAddBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleAdd());
        }

    }

    public static class SimpleBlockBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleBlock());
        }

    }

    public static class SimpleLocalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleLocalReadWrite());
        }

    }

    public static class SimpleGlobalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleGlobalReadWrite());
        }

    }

    public static class SimpleBranchBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleBranch());
        }

    }

    public static class SimpleLoopBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleLoop());
        }

    }

    public static class SimpleSwitchBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleSwitch());
        }

    }

    public static class SimpleInvokeBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleInvoke());
        }

    }

    public static class SimpleObjectBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleObject());
        }

    }

    public static class SimpleArrayReadBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleArrayRead());
        }

    }

    public static class SimpleMultiArrayReadBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleMultiArrayRead());
        }

    }

    public static class SimpleArrayWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleArrayWrite());
        }

    }

    public static class SimpleMultiArrayWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleMultiArrayWrite());
        }

    }

    public static class ComplexStringArrayBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.complexStringArray());
        }

    }

    public static class NestedAddsBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedAdds());
        }

    }

    public static class NestedBlocksBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedBlocks());
        }

    }

    public static class NestedLocalReadWritesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedLocalReadWrites());
        }

    }

    public static class NestedBranchesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedBranches());
        }

    }

    public static class NestedLoopsBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedLoops());
        }

    }

    public static class NestedSwitchesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedSwitches());
        }

    }

    public static class BranchWithGlobalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.branchWithGlobalReadWrite());
        }

    }

    public static class LoopWithGlobalReadWriteBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.loopWithGlobalReadWrite());
        }

    }

    public static class NestedLoopsWithMultipleBackEdgesBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedLoopsWithMultipleBackEdges());
        }

    }

    public static class InvokeObjectFunctionPropertyBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.invokeObjectFunctionProperty());
        }

    }

    public static class IrreducibleLoopBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected RootNode createRootNode() {
            // no need for a generator as sample is directly built with basic blocks
            return PELangSample.irreducibleLoop();
        }

    }

}
