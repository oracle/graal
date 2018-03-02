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
package org.graalvm.compiler.microbenchmarks.lir.trace;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.graalvm.compiler.lir.StandardOp.JumpOp;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.alloc.trace.GlobalLivenessInfo;
import org.graalvm.compiler.lir.alloc.trace.GlobalLivenessInfo.Builder;
import org.graalvm.compiler.lir.alloc.trace.TrivialTraceAllocator;
import org.graalvm.compiler.lir.alloc.trace.NaiveTrivialTraceAllocator;
import org.graalvm.compiler.lir.alloc.trace.MappingTrivialTraceAllocator;
import org.graalvm.compiler.lir.framemap.SimpleVirtualStackSlot;
import org.graalvm.compiler.nodes.cfg.Block;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import jdk.vm.ci.meta.Value;

/**
 *
 */
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(1)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class TrivialTraceAllocatorBenchmark {

    public class TrivialAllocation {

        public GlobalLivenessInfo livenessInfo;
        public int numVariables;
        public JumpOp jump;
        public Value[] phiOutValues;

        public TrivialAllocation(int[] varsIn, int[] varsOut, int[] phiOut) {
            numVariables = varsIn.length == 0 ? 0 : varsIn[varsIn.length - 1] + 1;

            Builder builder = new GlobalLivenessInfo.Builder(numVariables, numBlocks);
            builder.setIncoming(b2, varsIn);
            builder.setOutgoing(b2, varsOut);
            livenessInfo = builder.createLivenessInfo();

            Value[] predOut = IntStream.range(0, varsIn.length).mapToObj(i -> (Value) new SimpleVirtualStackSlot(i, null)).toArray(n -> new Value[n]);
            livenessInfo.setOutLocations(b0, predOut);

            if (phiOut.length == 0) {
                jump = null;
            } else {
                jump = new JumpOp(null);
                phiOutValues = Arrays.stream(phiOut).mapToObj(i -> new Variable(null, i)).toArray(n -> new Value[n]);

            }
        }

        public void setupIteration() {
            if (jump != null) {
                jump.setPhiValues(phiOutValues.clone());
            }
        }
    }

    public Block b0;
    public Block b1;
    public Block b2;
    public Block b3;

    public TrivialAllocation[] instances;
    private int numBlocks;

    /**
     * <pre>
     *     B0
     *    /  \
     *   B1 [B2]
     *    \  /
     *     B3
     * </pre>
     *
     * B2 is the trivial trace.
     */
    @Setup
    public void setup() {
        b0 = new Block(null);
        b1 = new Block(null);
        b2 = new Block(null);
        b3 = new Block(null);
        numBlocks = 4;

        // ids
        b0.setId(0);
        b1.setId(1);
        b2.setId(2);
        b3.setId(3);
        // successors
        b0.setSuccessors(new Block[]{b1, b2});
        b1.setSuccessors(new Block[]{b3});
        b2.setSuccessors(new Block[]{b3});
        b3.setSuccessors(new Block[]{});
        // predecessors
        b0.setPredecessors(new Block[]{});
        b1.setPredecessors(new Block[]{b0});
        b2.setPredecessors(new Block[]{b0});
        b3.setPredecessors(new Block[]{b1, b2});

        int[] varsIn = new int[]{1, 2, 3, 4, 5, 6};
        int[] varsOut = new int[]{1, 3, 4, 5};
        int[] phiOut = new int[]{2, 3, 6};

        instances = new TrivialAllocation[]{
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 51, 72, 73, 74}, new int[]{0, 1, 2, 72}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 14, 114, 118, 138, 139, 298}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 14, 114, 118, 138, 298}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 6, 7, 229, 259, 265, 266, 267, 272, 297}, new int[]{0, 1, 2, 6, 7, 259, 265}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 29, 32, 33, 51}, new int[]{0, 32}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 6, 8, 9, 11, 13, 14, 16, 20, 22, 23}, new int[]{0, 1, 2, 3, 6, 8, 9, 11, 13, 16, 22, 23}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 7, 12}, new int[]{0, 1, 7, 12}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 3, 5, 6, 7, 8, 16, 17, 22, 23}, new int[]{0, 2, 3, 5, 6, 7, 8, 16}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 46}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 84, 85, 86, 87, 88, 91}, new int[]{0, 1, 84}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 17, 19}, new int[]{0, 1, 17, 19}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 12, 41, 44, 45, 48, 53, 54, 55, 197}, new int[]{0, 1, 2, 3, 6, 7, 41}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 6, 7, 128, 158, 164, 165, 166, 171, 196}, new int[]{0, 1, 2, 6, 7, 128, 158, 164, 165, 196}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 3, 17, 18, 43, 51, 59, 63, 74, 76, 77, 84, 85, 87, 100, 101, 321}, new int[]{0, 2, 3, 17, 18, 43, 51, 59, 63, 84, 85, 321}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 6, 7, 47, 49, 50, 51, 52, 59, 61, 62}, new int[]{0, 1, 2, 47}, new int[]{}),
                        new TrivialAllocation(new int[]{1, 2, 3, 7, 34, 38, 40, 97, 101, 102, 103}, new int[]{1, 2, 3, 34, 97}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 5, 10, 40, 44, 47, 49, 52, 248, 253, 254}, new int[]{0, 1, 2, 5, 253, 254}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 5, 7, 21, 34, 47, 48}, new int[]{0, 2, 5, 7, 21, 34, 47, 48}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 3, 42, 44, 45, 103, 105, 109}, new int[]{0, 2, 3, 42}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 3, 42, 44, 45, 103, 105, 140, 153, 154}, new int[]{0, 2, 3, 42, 140}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 3, 4, 36}, new int[]{0, 3, 4, 36}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 20, 23}, new int[]{0, 2, 20}, new int[]{4}),
                        new TrivialAllocation(new int[]{0, 1, 4, 6}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 27, 92, 96, 105, 115, 120, 121, 122, 123, 124, 273, 274, 276, 281, 282, 322},
                                        new int[]{0, 27, 92, 96, 105, 115, 120, 121, 122, 123, 124, 273, 274, 276, 281, 282, 322}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 6, 8, 13, 14, 16}, new int[]{0, 1, 2, 6, 8, 13, 14, 16}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 8, 13, 17, 20, 21, 22, 23, 102, 137, 140, 142, 143, 266, 434, 435, 440, 441, 442, 443},
                                        new int[]{0, 1, 8, 13, 17, 20, 21, 22, 23, 102, 137, 140, 142, 143, 266, 434, 435}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 14, 39, 52, 55, 56, 130, 133, 134, 141, 146, 159, 161}, new int[]{0, 14, 39, 52, 55, 56, 130, 133, 134, 141, 146, 159, 161}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 17, 58, 77, 79, 89, 110, 114, 118, 119, 151, 152, 153, 154}, new int[]{0, 1, 2, 17, 58, 77, 110}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 5, 8, 9, 10, 21, 24, 25}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 6, 8, 10, 12, 13, 15, 16, 17, 18, 19, 20, 21, 22, 55, 58}, new int[]{0, 1, 12, 13, 15, 17, 18, 19, 20, 21}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 6, 10, 12, 13, 150, 151, 152, 153, 189, 192}, new int[]{0, 1, 12, 13, 150, 151, 152}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 6, 10, 12, 13, 150, 151, 192, 195, 196, 282, 286, 295, 296, 298}, new int[]{0, 1, 12, 13, 150, 151, 195, 196, 295}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 38, 56, 57}, new int[]{0, 38}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 6, 12, 28, 29, 30, 31, 32, 33, 34, 35, 36, 40, 44, 70, 79, 81},
                                        new int[]{0, 1, 6, 12, 28, 29, 30, 31, 32, 33, 34, 35, 36, 40, 44, 70, 79, 81}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 8, 48, 49, 50, 52, 76}, new int[]{0, 48}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 4}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{1, 4}, new int[]{4}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 393, 394, 395, 396, 397}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 7, 12, 224, 226, 229, 230}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 25, 27, 43}, new int[]{0, 1, 27, 43}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 63, 64, 74, 77, 78, 79}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 63, 64, 74, 77, 78, 79}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 13, 14, 15, 16, 23, 32, 36}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 18, 20, 125, 126, 131, 133, 134, 135, 136, 180, 184, 187, 193, 198, 199}, new int[]{0, 1, 18, 126, 180}, new int[]{}),
                        new TrivialAllocation(new int[]{1, 2, 4, 5, 6, 7, 10, 11, 12}, new int[]{1, 2, 4, 5, 6, 7, 10, 11}, new int[]{12}),
                        new TrivialAllocation(new int[]{0, 8, 149, 151, 153, 155, 156, 157, 158}, new int[]{0, 8, 151, 157}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 29, 31, 33, 34, 39, 40}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 29, 31, 33}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 100, 102, 183, 186, 187, 188}, new int[]{0, 1, 2, 100, 102, 183, 186, 187, 188}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 8, 13, 17, 19, 20, 21, 22, 58, 93, 96, 98, 99, 222, 369, 370, 400, 407, 410, 411, 412, 420},
                                        new int[]{0, 1, 8, 13, 17, 19, 20, 21, 22, 58, 93, 96, 98, 99, 222, 369, 370, 400, 407, 410, 411, 412}, new int[]{420}),
                        new TrivialAllocation(new int[]{0, 7, 9, 11}, new int[]{0, 7, 11}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 8, 10, 30, 31, 32, 43, 44, 46, 98, 100, 102, 109, 185}, new int[]{0, 1, 98}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 9, 13, 161, 162, 164}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 7, 85, 89, 90, 91, 92, 133, 140, 141}, new int[]{0, 1, 2, 3, 7, 85, 89, 90, 91, 92, 133, 140, 141}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 7, 85, 91, 92, 133, 169, 175, 176, 177, 182}, new int[]{0, 1, 2, 3, 7, 85, 91, 92, 133, 169, 175, 176, 177, 182}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 5, 9, 16, 17, 18}, new int[]{0, 1, 9}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 5, 6, 9, 11, 12, 15, 38, 39, 44}, new int[]{0, 1, 38}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 6, 7, 8, 9, 11, 12, 34, 35, 49, 60, 204, 205, 206, 207, 208, 209, 210},
                                        new int[]{0, 1, 3, 6, 7, 8, 9, 11, 12, 34, 35, 49, 60, 204, 205, 206, 208, 209, 210}, new int[]{207}),
                        new TrivialAllocation(new int[]{0, 1, 8, 49, 297, 299, 318, 378, 380, 385, 386, 387, 388, 389, 394, 406, 407, 408, 412},
                                        new int[]{0, 1, 8, 49, 297, 299, 318, 378, 380, 385, 386, 387, 388, 389, 394, 406, 407, 408, 412}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3}, new int[]{0, 1}, new int[]{3}),
                        new TrivialAllocation(new int[]{0, 1, 5, 6, 37, 38, 39, 40, 41, 44, 51, 52}, new int[]{0, 1, 5, 6, 37, 38, 39, 40, 41, 44, 51, 52}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 35}, new int[]{0, 1, 2, 35}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 12, 13, 15}, new int[]{0, 1, 2, 3, 4}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 13, 60, 61, 62, 63, 94}, new int[]{0, 1, 2, 13, 60, 61, 62, 63, 94}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 13, 209, 211, 214, 219, 239, 242, 245, 249, 250, 251, 252}, new int[]{0, 13, 209, 211, 214, 219, 239, 242, 245, 249, 250}, new int[]{252}),
                        new TrivialAllocation(new int[]{0, 1, 44, 46, 53, 56, 57, 59, 60, 61, 63}, new int[]{0, 1, 44, 46, 53, 56, 57, 59, 60, 61, 63}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 5, 7, 8, 19, 20, 22, 23, 25, 26, 27, 28}, new int[]{0, 1, 2, 5, 7, 8, 19, 20, 22, 23, 25, 26}, new int[]{28}),
                        new TrivialAllocation(new int[]{0, 1, 2}, new int[]{0}, new int[]{2}),
                        new TrivialAllocation(new int[]{0, 1, 8, 13, 29, 30, 31, 32, 33, 34, 35, 36, 37, 43, 73, 82, 89, 90}, new int[]{0, 1, 8, 13, 29, 36, 37, 43, 73, 82, 89}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 5, 36, 38, 39, 46, 47, 48, 50, 52}, new int[]{0, 1, 5, 36, 38, 39, 46, 47, 48}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 38, 39, 40, 41, 42, 44, 45, 46, 47, 48}, new int[]{0, 1, 38, 39, 40}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 33, 36, 39}, new int[]{0, 1, 33, 36, 39}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 7, 8, 9, 10, 18, 23}, new int[]{0, 1, 9, 10, 18}, new int[]{}),
                        new TrivialAllocation(
                                        new int[]{0, 1, 2, 25, 26, 27, 391, 885, 896, 925, 945, 946, 1007, 1009, 1010, 1012, 1013, 1016, 1017, 1018, 1022, 1024, 1025, 1026, 1028, 1033, 1448, 1457},
                                        new int[]{0, 1, 2, 25, 26, 27, 391, 885, 896, 925, 945, 946, 1007, 1009, 1010, 1012, 1013, 1017, 1018, 1022, 1448, 1457}, new int[]{1033}),
                        new TrivialAllocation(new int[]{0, 1, 3, 5, 21, 22, 37}, new int[]{0, 1, 3, 5, 21, 22, 37}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 5, 6, 9, 12, 13, 20, 21}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 5, 8}, new int[]{0}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 15}, new int[]{0, 1, 2, 3, 15}, new int[]{2}),
                        new TrivialAllocation(new int[]{0, 1}, new int[]{0}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 5, 381, 419, 421, 426}, new int[]{0, 1, 381, 421}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 17, 29, 30, 33, 36, 39, 41, 42, 44}, new int[]{0, 1, 2, 3, 4, 5, 17, 29, 33, 36, 39, 41, 42, 44}, new int[]{3}),
                        new TrivialAllocation(new int[]{0, 1, 125, 126, 127, 128}, new int[]{0, 1, 125, 126, 127, 128}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 44, 48, 50, 51}, new int[]{0, 1, 44, 48, 50, 51}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 17, 18, 46, 71, 74, 77, 78, 79, 80, 114, 115, 120}, new int[]{0, 1, 2, 17, 18, 46, 71, 74, 77, 78, 79, 80, 114, 115, 120},
                                        new int[]{}),
                        new TrivialAllocation(new int[]{1, 2, 21}, new int[]{1, 2, 21}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 8, 11, 14, 15, 17, 18, 19, 22, 24, 27, 28}, new int[]{0, 1, 2, 3, 4, 15, 18, 19}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 5, 6, 17, 61, 123, 125, 126, 129, 137, 140, 143, 145, 146, 148, 149}, new int[]{0, 1, 2, 4, 123}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 5, 6, 17, 61, 123, 125, 126, 213, 214, 215, 216, 218}, new int[]{0, 1, 2, 4, 123, 126, 213, 214}, new int[]{}),
                        new TrivialAllocation(new int[]{4}, new int[]{}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 6, 65, 92, 100, 112, 115}, new int[]{0, 6, 65, 92, 100, 112, 115}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 105, 106, 107, 115, 117, 118}, new int[]{0, 105, 106}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 16, 25, 26}, new int[]{0, 1, 16}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3}, new int[]{0, 1, 3}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 6, 118, 119, 120, 123, 127, 129, 130}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 16, 24, 28, 30, 31, 32, 33, 38, 46, 48, 51, 52, 53}, new int[]{0, 1, 2, 3, 16, 24, 28, 30, 31, 32, 33, 38, 46, 48, 51, 52},
                                        new int[]{53}),
                        new TrivialAllocation(new int[]{0, 1, 3}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 48, 57}, new int[]{0, 1, 2, 3, 57}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 16, 24, 25, 32, 37, 40}, new int[]{0, 1, 2, 3, 16, 24, 32, 37}, new int[]{}),
                        new TrivialAllocation(new int[]{2, 20, 21}, new int[]{2, 20}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 3, 4, 5, 6, 7, 8, 9, 10, 13, 14, 17, 18, 508, 509, 510, 523, 526, 528, 529, 546, 550},
                                        new int[]{0, 3, 4, 5, 6, 7, 8, 9, 10, 13, 14, 17, 18, 508, 509, 510, 523, 526, 546}, new int[]{529}),
                        new TrivialAllocation(new int[]{0, 184, 241}, new int[]{0, 184, 241}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 11}, new int[]{0, 1, 2, 3}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 5, 6, 7, 8}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 8, 9, 47, 54, 59, 172, 182, 183, 184, 192, 193, 199, 203, 205, 208, 213, 214, 660}, new int[]{0, 59, 172, 199, 203, 205, 208}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 5, 7, 8, 9, 33, 35, 46, 47, 48, 49, 76, 77, 78, 79, 80, 525, 527, 538, 539, 540, 541},
                                        new int[]{0, 1, 2, 3, 5, 7, 8, 9, 33, 35, 46, 47, 48, 49, 76, 77, 78, 79, 80, 525, 538}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 9, 295, 298, 314, 406, 425, 434, 435, 437, 438, 813}, new int[]{0, 9, 295, 298, 314, 406, 425, 435, 438, 813}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 9, 74, 75, 77, 535, 543, 545, 580, 592, 597, 598, 599, 813}, new int[]{0, 74, 75, 77, 543, 592, 597}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 4, 123, 127, 128, 131}, new int[]{0, 4, 123, 127, 131}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 5}, new int[]{0, 1, 2, 5}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 8, 33, 70, 97, 98, 102, 104, 106}, new int[]{0, 1, 2, 8, 33, 70, 97, 98}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 5, 6, 8, 18, 19, 31, 33, 34, 35}, new int[]{0, 1, 2, 3, 5, 6}, new int[]{31}),
                        new TrivialAllocation(new int[]{0, 1, 2, 17, 20, 22, 25, 26, 27, 59, 63, 64, 65, 66, 67, 83, 84, 87, 102}, new int[]{0, 1, 2, 17, 25, 27, 59, 64, 66, 67, 83, 84, 87, 102},
                                        new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 28, 37, 51, 95, 96, 118, 121, 122, 123, 124, 127}, new int[]{0, 1, 28, 95, 96}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, new int[]{0, 1, 3, 4, 7}, new int[]{6}),
                        new TrivialAllocation(new int[]{0, 4, 15, 16, 31, 48, 50, 55, 61, 74, 104, 115, 116, 164}, new int[]{0, 15, 31, 48, 50, 55, 74, 104, 115, 116}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 24, 31, 212, 228, 229, 238}, new int[]{0, 1, 24, 212}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 10, 58, 60, 61}, new int[]{0, 1, 2, 10}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 3, 4, 12, 19, 60, 62}, new int[]{0, 3, 4, 12, 19}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 8, 10}, new int[]{0, 1, 2, 3}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 16, 17, 27, 36}, new int[]{0, 27, 36}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 10, 118, 119, 120}, new int[]{0, 10, 118, 119, 120}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 20, 28, 76, 79}, new int[]{0, 76, 79}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 6, 7, 9, 11}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{112, 113, 114, 115, 116, 118, 122, 123}, new int[]{112, 113, 114, 115, 118}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 3, 4, 5}, new int[]{0}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 11, 13}, new int[]{0, 1, 2, 11}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 9, 24}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 15, 18, 81, 84, 85, 92, 94, 97, 100, 101, 102, 103, 104, 118}, new int[]{0, 1, 2, 3, 15, 18, 81, 84, 85, 92, 94, 97, 100},
                                        new int[]{118}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 15, 18, 84, 85, 94, 97, 155, 194, 195, 196, 203, 209, 211, 212, 215, 219, 220, 225, 324},
                                        new int[]{0, 1, 2, 3, 15, 18, 84, 85, 94, 97, 155, 194, 195, 196, 203, 209, 324}, new int[]{225}),
                        new TrivialAllocation(new int[]{0, 1}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 129, 156}, new int[]{0, 1, 2, 4, 129, 156}, new int[]{}),
                        new TrivialAllocation(new int[]{1, 2, 3, 4}, new int[]{1, 2, 3}, new int[]{}),
                        new TrivialAllocation(new int[]{1, 2, 18, 19, 20}, new int[]{1, 2, 18}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 7, 10, 11, 15, 16, 17}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 4}, new int[]{0}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 4}, new int[]{0}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 5, 6, 10, 142, 146, 153, 154, 160, 188, 190, 191}, new int[]{0, 1, 4, 5, 6, 10, 142, 146, 153, 154, 160, 188}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 74}, new int[]{0, 1, 2, 3, 74}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 7, 8, 10, 11}, new int[]{0, 1, 2, 3, 4, 5, 10, 11}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 8, 25, 26, 27, 29}, new int[]{0, 1, 2, 3}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 6, 7, 49, 93, 95, 97, 98}, new int[]{0, 1, 2, 3, 4, 6}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 46, 50, 51, 106, 107, 110, 147, 148}, new int[]{0, 1, 2, 3, 46, 106, 147}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 14, 15, 25, 26, 35, 36, 55, 56, 57, 59}, new int[]{0, 26, 55, 56, 57}, new int[]{}),
                        new TrivialAllocation(new int[]{1, 13, 30, 31, 32, 33, 34}, new int[]{1, 13, 30}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8}, new int[]{0, 1, 2}, new int[]{7, 8}),
                        new TrivialAllocation(new int[]{4, 15, 16, 26, 29}, new int[]{4, 15, 16, 26, 29}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14}, new int[]{1, 2, 5, 6, 7, 8, 9, 10, 11, 12}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 5, 9, 10, 24, 87, 88, 91}, new int[]{0, 1, 4, 5, 9, 10, 24, 87, 88, 91}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 5, 9, 147, 158, 286, 287, 288}, new int[]{0, 1, 9, 147, 158, 286, 287}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 5, 9, 147, 151, 157, 158, 209, 210, 265, 266}, new int[]{0, 1, 4, 5, 9, 147, 151, 157, 158, 209, 210}, new int[]{266}),
                        new TrivialAllocation(new int[]{0, 44, 47, 48, 49, 50}, new int[]{0, 44, 47, 48, 49, 50}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 16, 26, 36, 44, 77, 114, 119}, new int[]{0, 1, 2, 16, 26, 36, 114, 119}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 16, 26, 36, 44, 122, 133, 134, 140, 141, 142, 143, 158, 179, 180, 181, 187, 188, 190, 191, 192, 196, 197, 198},
                                        new int[]{0, 1, 2, 16, 26, 36, 122, 140, 179, 180, 181, 187}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 7, 46, 48, 51, 147, 148, 161, 163, 164}, new int[]{0, 1, 2, 3, 4, 7, 46, 48, 51, 147, 148}, new int[]{147}),
                        new TrivialAllocation(new int[]{0, 1, 9, 13, 15, 16, 17, 20, 21}, new int[]{0, 1, 9, 13, 15, 16}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3, 6, 9, 11}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 49, 51, 55}, new int[]{0, 1, 2, 49}, new int[]{55}),
                        new TrivialAllocation(new int[]{0, 1, 2, 49, 59, 76, 78}, new int[]{0, 1, 2, 76, 78}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 25, 26, 27, 391, 873, 877, 878, 887, 892}, new int[]{0, 1, 2, 391, 873, 878, 887, 892}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 25, 26, 27, 30, 389, 390, 391, 409, 416, 648, 649, 650, 651, 652, 653, 654, 655, 675, 678, 680, 729, 731, 743, 763, 764, 772},
                                        new int[]{0, 1, 2, 3, 4, 5, 25, 26, 27, 30, 389, 390, 391, 409, 416, 648, 649, 650, 651, 652, 653, 654, 655, 675, 678, 680, 729, 731, 743, 763, 764, 772},
                                        new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 34, 165, 166, 171, 172, 173, 174, 181, 182, 186, 187, 195},
                                        new int[]{0, 1, 2, 3, 34, 165, 166, 171, 172, 173, 174, 181, 182, 186, 187}, new int[]{195}),
                        new TrivialAllocation(new int[]{0, 6, 65, 92, 100, 112, 115}, new int[]{0, 6, 92, 100, 112, 115}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 8}, new int[]{0}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 46, 49, 50, 64}, new int[]{0, 1, 2, 46, 50, 64}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 3, 5, 31, 32, 37, 39, 41, 43}, new int[]{0, 2, 3, 5, 31, 32, 37, 39, 41, 43}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 7, 10, 12}, new int[]{0, 1, 2, 4, 7, 10, 12}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 6, 126, 127, 130, 133}, new int[]{6, 126, 127, 133}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 7, 22, 23, 27, 28, 29, 32}, new int[]{0, 1, 4, 22}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 4, 49, 50, 51, 52, 53}, new int[]{0, 4, 49, 50, 51, 52, 53}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 27, 28, 29, 31, 32, 33, 35}, new int[]{0, 1, 2, 3, 4, 27, 28, 29, 31, 32, 33, 35}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 10, 19, 58, 68, 69, 70, 175, 186, 187, 191, 192, 194, 195, 197, 198, 199, 200, 201, 206},
                                        new int[]{0, 1, 2, 68, 69, 70, 175, 191, 194, 195, 198, 199, 200, 201}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 3, 15, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91}, new int[]{0, 15, 87, 88, 89, 90}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 5, 9, 48, 108, 110, 119, 120, 121, 167, 169, 215}, new int[]{0, 1, 2, 108, 121, 167}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 6}, new int[]{0, 6}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11}, new int[]{0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 9, 13, 47, 52, 53}, new int[]{0, 1, 2, 13}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 39, 49, 55, 62, 68, 69, 70, 71, 85, 119, 123, 124, 126, 128, 132, 133},
                                        new int[]{0, 1, 39, 49, 55, 62, 68, 69, 70, 71, 85, 126, 128, 132, 133}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 3}, new int[]{0, 1, 3}, new int[]{}),
                        new TrivialAllocation(new int[]{0}, new int[]{0}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 6, 121, 122, 128, 129, 130, 134, 135, 136}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 7, 153, 154}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 22, 37, 42, 77, 93, 94}, new int[]{0, 1, 2, 3, 22, 42, 77, 93, 94}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 12, 41, 42, 43, 46, 47, 48, 49, 56, 57, 61, 62, 63}, new int[]{0, 1, 4, 41, 42, 43}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 23, 24, 34, 35, 43, 44, 63, 64, 65, 68, 69, 73, 78}, new int[]{0, 2, 35, 63, 64, 65}, new int[]{}),
                        new TrivialAllocation(new int[]{9, 111, 123, 188, 193, 196}, new int[]{9, 111, 123, 188, 193, 196}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 6, 14, 15, 30, 31, 32}, new int[]{0, 1, 2, 3, 14, 15}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4}, new int[]{0, 1, 2}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 3, 6, 9, 12, 13, 18, 22, 23, 24, 25, 26, 27, 32, 33}, new int[]{0, 6, 18, 22}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 6, 41, 49, 54, 55, 56, 59, 79, 81, 82, 113}, new int[]{0, 1, 2, 3, 6, 41, 49, 54, 55, 56, 59, 79, 81, 82, 113}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 18, 24, 40, 41, 62, 63, 64, 163, 164}, new int[]{0, 18, 24, 40, 41, 62, 63}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 4, 5, 6, 18, 88, 150, 152, 153, 156, 158, 165, 168, 171, 174, 175, 176, 356}, new int[]{0, 1, 2, 4, 150}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 8, 11, 14, 15, 18, 19, 20, 23, 76}, new int[]{0, 1, 2, 3, 4, 5, 6, 8, 11, 14, 15, 18, 19, 20, 23, 76}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 38, 39, 76, 77, 78}, new int[]{0, 1, 38, 39, 76, 77, 78}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 91, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114}, new int[]{0, 2, 91}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 2, 91, 102, 103, 104, 105, 106, 107, 108, 109, 110, 123, 173, 174, 178, 192, 201, 203, 211, 235, 249},
                                        new int[]{0, 2, 91, 102, 103, 104, 105, 106, 107, 108, 109, 110, 123, 173, 174, 178, 192, 201, 203, 211, 249}, new int[]{235}),
                        new TrivialAllocation(new int[]{0, 27, 28, 68, 72, 76, 83, 85}, new int[]{0, 27, 28, 68, 72, 76, 83}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 39, 52, 61, 70, 79, 81, 83}, new int[]{0, 39, 79}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 14, 16, 41, 49, 51, 52, 53, 54, 56, 58, 59, 60, 61, 62, 63, 64, 68},
                                        new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 14, 16, 41, 49, 51, 52, 53, 54, 56, 58, 59, 60, 61, 62, 63}, new int[]{68}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9, 145, 265, 266, 269, 271, 300, 301, 308, 309, 310, 312, 313, 314, 315, 316, 317, 318, 319, 323},
                                        new int[]{0, 1, 2, 3, 4, 6, 7, 8, 9, 145, 265, 266, 269, 271, 300, 301, 308, 309, 310, 312, 313, 314, 315, 316, 317, 318}, new int[]{323}),
                        new TrivialAllocation(new int[]{0, 1, 4, 6, 7, 9, 145, 265, 266, 271, 300, 301, 308, 313, 356, 357},
                                        new int[]{0, 1, 4, 6, 7, 9, 145, 265, 266, 271, 300, 301, 308, 313, 356, 357}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 13, 16, 34, 46, 48, 49, 50, 77, 96, 122, 165, 174, 177, 178, 179, 183, 184, 185}, new int[]{0, 1, 13, 34, 46, 96, 122, 165}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 5, 31, 32, 33, 39, 115}, new int[]{0, 1, 2, 3, 5, 32}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 4, 9, 11, 12, 13, 97, 98}, new int[]{0, 1, 4, 9, 11, 12, 13, 97}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 7, 102, 103, 110, 111, 127, 128, 129, 130, 132, 137, 139, 140, 141, 142, 143, 144},
                                        new int[]{0, 1, 2, 102, 110, 139, 140, 141, 142, 143, 144}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 7, 102, 103, 111, 132, 137, 139, 153, 157}, new int[]{0, 1, 2, 102, 139, 157}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 9, 15, 17, 55, 174, 197, 277, 278, 310, 350, 352, 354, 355, 376}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 15, 55, 277, 350},
                                        new int[]{}),
                        new TrivialAllocation(new int[]{1, 2, 3, 4, 9, 12, 17, 28, 35, 38, 39, 41, 42, 43, 44, 46}, new int[]{1, 2, 3, 12, 17, 28, 41, 42, 43, 44}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15, 18, 19, 21, 22}, new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 14, 15, 18, 19}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 9, 18, 54, 55, 60, 62, 63, 65, 66, 77, 106}, new int[]{0, 1, 18, 55, 77, 106}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 9, 13, 18, 19, 26, 35}, new int[]{0, 1, 2, 3, 9, 13, 18, 19, 26, 35}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 5, 127, 128, 129, 131, 132, 133, 134, 135, 136}, new int[]{5, 127, 128, 129, 131, 132, 133, 134, 135, 136}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 24, 26, 28, 30, 41, 48, 53, 56, 63, 66, 71, 73, 74, 75, 81, 82, 83, 84, 85, 86, 87, 88, 90, 990, 992, 995, 1011, 1031, 1055, 1066, 1077,
                                        1114, 1138, 1158, 1176, 1177, 1178, 1180, 1181, 1753}, new int[]{0, 1, 24, 28, 66, 90, 992, 995, 1011, 1055, 1077, 1114, 1138, 1177}, new int[]{}),
                        new TrivialAllocation(
                                        new int[]{0, 1, 24, 26, 30, 41, 48, 56, 63, 71, 73, 74, 75, 81, 82, 83, 84, 85, 86, 87, 88, 121, 143, 154, 162, 197, 199, 201, 217, 354, 411, 671, 816, 839,
                                                        841, 1753},
                                        new int[]{0, 1, 24, 26, 30, 41, 48, 56, 63, 71, 73, 74, 75, 81, 82, 83, 84, 85, 86, 87, 88, 121, 143, 154, 162, 197, 199, 201, 217, 354, 411, 671, 1753},
                                        new int[]{841}),
                        new TrivialAllocation(
                                        new int[]{0, 1, 24, 26, 28, 30, 41, 48, 53, 56, 63, 66, 71, 73, 74, 75, 81, 82, 83, 84, 85, 86, 87, 88, 90, 990, 992, 995, 1011, 1031, 1055, 1066, 1077, 1114,
                                                        1138, 1158, 1190, 1210, 1219, 1221, 1223, 1226, 1254, 1257, 1260, 1753},
                                        new int[]{0, 1, 24, 28, 66, 90, 992, 995, 1011, 1055, 1077, 1114, 1138, 1190, 1226}, new int[]{}),
                        new TrivialAllocation(new int[]{17, 100, 173, 175, 176, 177, 178, 180, 182, 184}, new int[]{17}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 4, 7, 8}, new int[]{0, 1}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 10, 11, 12, 15, 85, 87, 88, 89, 90}, new int[]{0, 1, 2, 85, 87}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 11, 12, 15, 16, 29}, new int[]{0, 1, 11}, new int[]{}),
                        new TrivialAllocation(new int[]{8, 167, 170, 196, 197, 198, 199, 200, 376, 383, 428, 433, 435, 437},
                                        new int[]{8, 167, 170, 196, 197, 198, 199, 200, 376, 383, 428, 433, 435, 437}, new int[]{}),
                        new TrivialAllocation(new int[]{0, 1, 2, 3, 10, 14, 18, 19, 20, 21, 22, 32, 33, 34, 39, 41, 47, 48, 54, 59}, new int[]{0, 1, 2, 3, 18, 19, 33, 34, 41, 47, 54}, new int[]{}),
                        new TrivialAllocation(varsIn, varsOut, phiOut)};
    }

    @Setup(Level.Invocation)
    public void setupIteration() {
        for (TrivialAllocation i : instances) {
            i.setupIteration();
        }
    }

    @Benchmark
    public void naiveTrivialTraceAllocator() {
        for (TrivialAllocation i : instances) {
            NaiveTrivialTraceAllocator.allocate(b2, b0, i.livenessInfo, i.numVariables, i.jump);
        }
    }

    @Benchmark
    public void mappingTrivialTraceAllocator() {
        for (TrivialAllocation i : instances) {
            MappingTrivialTraceAllocator.allocate(b2, b0, i.livenessInfo, i.numVariables, i.jump);
        }
    }

    @Benchmark
    public void trivialTraceAllocator() {
        for (TrivialAllocation i : instances) {
            TrivialTraceAllocator.allocate(b2, b0, i.livenessInfo, i.jump);
        }
    }

}
