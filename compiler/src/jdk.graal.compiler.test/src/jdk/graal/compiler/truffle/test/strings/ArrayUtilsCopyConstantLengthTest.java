/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsSingleKillNode;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ArrayUtilsCopyConstantLengthTest extends ArrayUtilsCopyTest {

    private static final int[] LENGTH_FILTER = {1, 2, 7, 8, 9, 15, 16};

    private final Object[] constantArgs = new Object[5];

    public ArrayUtilsCopyConstantLengthTest(int[] source, int sourceIndex, int destinationIndex, int length) {
        super(source, sourceIndex, destinationIndex, length);
    }

    @Parameters(name = "{index}: sourceIndex: {1}, destinationIndex: {2}, length: {3}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        int[] source = initializedSource();
        for (int sourceIndex : new int[]{0, 1, 7, 15}) {
            for (int destinationIndex : new int[]{0, 3, 9}) {
                for (int length : LENGTH_FILTER) {
                    if (sourceIndex + length <= source.length && destinationIndex + length <= ARRAY_LENGTH) {
                        ret.add(new Object[]{source, sourceIndex, destinationIndex, length});
                    }
                }
            }
        }
        return ret;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        addConstantParameterBinding(conf, constantArgs);
        return super.editGraphBuilderConfiguration(conf);
    }

    private static final ThreadLocal<InstalledCode[]> CACHE = ThreadLocal.withInitial(() -> new InstalledCode[LENGTH_FILTER.length]);

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean ignoreForceCompile, boolean ignoreInstallAsDefault,
                    OptionValues options) {
        InstalledCode[] cache = CACHE.get();
        int cacheIndex = indexOf(LENGTH_FILTER, length);
        InstalledCode installedCode = cache[cacheIndex];
        while (installedCode == null || !installedCode.isValid()) {
            installedCode = super.getCode(installedCodeOwner, graph, true, false, options);
            cache[cacheIndex] = installedCode;
        }
        return installedCode;
    }

    @Override
    @Test
    public void testCopy() {
        int maxVectorSizeBytes = maxVectorSizeBytes();
        Assume.assumeTrue(ArrayCopyWithConversionsNode.canLowerConstantLengthCopy(maxVectorSizeBytes, Stride.S4, Stride.S4, false, length));
        constantArgs[4] = length;
        ArgSupplier destination = ArrayUtilsCopyTest::initializedDestination;
        test(getResolvedJavaMethod("copy"), null, source, sourceIndex, destination, destinationIndex, length);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        int chunkCount = expectedChunkCount(Stride.S4.log2);
        Assert.assertEquals(0, graph.getNodes().filter(ArrayCopyWithConversionsSingleKillNode.class).count());
        Assert.assertEquals(chunkCount, countCopyReads(graph));
        Assert.assertEquals(chunkCount, countCopyWrites(graph));
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        int chunkCount = expectedChunkCount(Stride.S4.log2);
        Assert.assertEquals(0, graph.getNodes().filter(ArrayCopyWithConversionsSingleKillNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(MemoryAnchorNode.class).count());
        Assert.assertEquals(chunkCount, countCopyReads(graph));
        Assert.assertEquals(chunkCount, countCopyWrites(graph));
    }

    private static int countCopyReads(StructuredGraph graph) {
        LocationIdentity location = NamedLocationIdentity.getArrayLocation(JavaKind.Int);
        int count = 0;
        for (ReadNode read : graph.getNodes().filter(ReadNode.class)) {
            if (read.getLocationIdentity().equals(location)) {
                count++;
            }
        }
        return count;
    }

    private static int countCopyWrites(StructuredGraph graph) {
        LocationIdentity location = NamedLocationIdentity.getArrayLocation(JavaKind.Int);
        int count = 0;
        for (WriteNode write : graph.getNodes().filter(WriteNode.class)) {
            if (write.getLocationIdentity().equals(location)) {
                count++;
            }
        }
        return count;
    }

    private int expectedChunkCount(int log2Stride) {
        int byteLength = length << log2Stride;
        int chunkSize = Integer.highestOneBit(Math.min(byteLength, maxVectorSizeBytes()));
        return (byteLength + chunkSize - 1) / chunkSize;
    }

    private int maxVectorSizeBytes() {
        return ((VectorLoweringProvider) getProviders().getLowerer()).getVectorArchitecture().getMaxVectorLength(IntegerStamp.create(Byte.SIZE));
    }
}
