/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.truffle.pelang.benchmark;

import org.graalvm.compiler.truffle.pelang.util.PELangBCFGenerator;
import org.graalvm.compiler.truffle.pelang.util.PELangSample;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangBCFBenchmark {

    public static class SimpleAddBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleAdd());
        }

    }

    public static class SimpleBlockBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleBlock());
        }

    }

    public static class SimpleLocalReadWriteBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleLocalReadWrite());
        }

    }

    public static class SimpleGlobalReadWriteBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleGlobalReadWrite());
        }

    }

    public static class SimpleBranchBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleBranch());
        }

    }

    public static class SimpleLoopBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleLoop());
        }

    }

    public static class SimpleSwitchBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleSwitch());
        }

    }

    public static class SimpleInvokeBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleInvoke());
        }

    }

    public static class SimpleObjectBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleObject());
        }

    }

    public static class SimpleArrayReadBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleArrayRead());
        }

    }

    public static class SimpleMultiArrayReadBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleMultiArrayRead());
        }

    }

    public static class SimpleArrayWriteBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleArrayWrite());
        }

    }

    public static class SimpleMultiArrayWriteBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.simpleMultiArrayWrite());
        }

    }

    public static class ComplexStringArrayBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.complexStringArray());
        }

    }

    public static class NestedAddsBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedAdds());
        }

    }

    public static class NestedBlocksBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedBlocks());
        }

    }

    public static class NestedLocalReadWritesBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedLocalReadWrites());
        }

    }

    public static class NestedBranchesBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedBranches());
        }

    }

    public static class NestedLoopsBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedLoops());
        }

    }

    public static class NestedSwitchesBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedSwitches());
        }

    }

    public static class BranchWithGlobalReadWriteBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.branchWithGlobalReadWrite());
        }

    }

    public static class LoopWithGlobalReadWriteBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.loopWithGlobalReadWrite());
        }

    }

    public static class NestedLoopsWithMultipleBackEdgesBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.nestedLoopsWithMultipleBackEdges());
        }

    }

    public static class InvokeObjectFunctionPropertyBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            PELangBCFGenerator g = new PELangBCFGenerator();
            return g.generate(PELangSample.invokeObjectFunctionProperty());
        }

    }

    public static class IrreducibleLoopBenchmark extends PELangBenchmark {

        @Override
        protected RootNode createRootNode() {
            // no need for a generator as sample is directly built with basic blocks
            return PELangSample.irreducibleLoop();
        }

    }

}
