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

import org.graalvm.compiler.truffle.benchmark.PartialEvaluationBenchmark;
import org.graalvm.compiler.truffle.pelang.util.PELangSample;

import com.oracle.truffle.api.nodes.RootNode;

public class PELangNCFBenchmark {

    public static class SimpleAdd extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleAdd();
        }

    }

    public static class SimpleBlock extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleBlock();
        }

    }

    public static class SimpleLocalReadWrite extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleLocalReadWrite();
        }

    }

    public static class SimpleGlobalReadWrite extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleGlobalReadWrite();
        }

    }

    public static class SimpleBranch extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleBranch();
        }

    }

    public static class SimpleLoop extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleLoop();
        }

    }

    public static class SimpleSwitch extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleSwitch();
        }

    }

    public static class SimpleInvoke extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleInvoke();
        }

    }

    public static class SimpleObject extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleObject();
        }

    }

    public static class SimpleArrayRead extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleArrayRead();
        }

    }

    public static class SimpleMultiArrayRead extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleMultiArrayRead();
        }

    }

    public static class SimpleArrayWrite extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleArrayWrite();
        }

    }

    public static class SimpleMultiArrayWrite extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.simpleMultiArrayWrite();
        }

    }

    public static class ComplexStringArray extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.complexStringArray();
        }

    }

    public static class NestedAdds extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.nestedAdds();
        }

    }

    public static class NestedBlocks extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.nestedBlocks();
        }

    }

    public static class NestedLocalReadWrites extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.nestedLocalReadWrites();
        }

    }

    public static class NestedBranches extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.nestedBranches();
        }

    }

    public static class NestedLoops extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.nestedLoops();
        }

    }

    public static class NestedSwitches extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.nestedSwitches();
        }

    }

    public static class BranchWithGlobalReadWrite extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.branchWithGlobalReadWrite();
        }

    }

    public static class LoopWithGlobalReadWrite extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.loopWithGlobalReadWrite();
        }

    }

    public static class NestedLoopsWithMultipleBackEdges extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.nestedLoopsWithMultipleBackEdges();
        }

    }

    public static class InvokeObjectFunctionProperty extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.invokeObjectFunctionProperty();
        }

    }

    public static class BinaryTrees extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.binaryTrees();
        }

        @Override
        protected Object[] callArguments() {
            return new Object[]{10L};
        }

    }

    public static class ArraySum extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.arraySum();
        }

    }

    public static class ArrayCompare extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.arrayCompare();
        }

    }

    public static class Pow extends PartialEvaluationBenchmark {

        @Override
        protected RootNode rootNode() {
            return PELangSample.pow();
        }

        @Override
        protected Object[] callArguments() {
            return new Object[]{3L, 2L};
        }

    }

}
