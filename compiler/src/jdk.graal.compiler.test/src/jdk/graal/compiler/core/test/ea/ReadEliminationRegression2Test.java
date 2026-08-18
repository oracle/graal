/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.core.test.ea;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.ReadNode;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ReadEliminationRegression2Test extends GraalCompilerTest {

    @Test
    public void test() {
        ResolvedJavaMethod method = getResolvedJavaMethod(TIFFImageWriterMock.class, "initializeScaleTables");
        test(method, new TIFFImageWriterMock(8, 10));
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        int numBandsReads = 0;
        for (ReadNode read : graph.getNodes(ReadNode.TYPE)) {
            if (read.getLocationIdentity() instanceof FieldLocationIdentity field && field.getField().getName().equals("numBands")) {
                numBandsReads++;
            }
        }
        /* We expect all but one read of numBands to be read eliminated. */
        Assert.assertEquals("number of reads from numBands field", 1, numBandsReads);
    }

    /* Based on com.sun.imageio.plugins.tiff.TIFFImageWriter */
    public static final class TIFFImageWriterMock {
        private int bitDepth;
        private int numBands;
        private int[] sampleSize = null;
        public int scalingBitDepth = -1;
        public boolean isRescaling = false;
        private byte[][] scale = null;
        public byte[] scale0 = null;

        private TIFFImageWriterMock(int bitDepth, int numBands) {
            this.bitDepth = bitDepth;
            this.numBands = numBands;
        }

        public void initializeScaleTables() {
            for (int b = 0; GraalDirectives.injectIterationCount(11, b < numBands); b++) {
                if (GraalDirectives.injectBranchProbability(0.5, sampleSize[b] != bitDepth)) {
                    isRescaling = true;
                    break;
                }
            }

            this.scalingBitDepth = bitDepth;
            int maxOutSample = (1 << bitDepth) - 1;
            if (GraalDirectives.injectBranchProbability(0.9, bitDepth <= 8)) {
                scale = new byte[numBands][];
                /* Manually duplicated structure: Bypass the loop completely if numBands == 0. */
                if (GraalDirectives.injectBranchProbability(0.5, 1 < numBands)) {
                    for (int b = 0; b < numBands; b++) {
                        int maxInSample = (1 << sampleSize[b]) - 1;
                        int halfMaxInSample = maxInSample / 2;
                        scale[b] = new byte[maxInSample + 1];
                        for (int s = 0; s <= maxInSample; s++) {
                            scale[b][s] = (byte) ((s * maxOutSample + halfMaxInSample) / maxInSample);
                        }
                    }
                    scale0 = scale[0];
                } else {
                    /*
                     * Read from a default-initialized new array. The bounds check must use the
                     * correct array length. That length is the scalar alias of a LoadField of
                     * numBands, not the LoadField itself.
                     */
                    scale0 = scale[0];
                }
            }
        }
    }
}
