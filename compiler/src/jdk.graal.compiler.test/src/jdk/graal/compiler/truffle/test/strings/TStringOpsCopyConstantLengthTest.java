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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.ArrayCopyWithConversionsNode;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsCopyConstantLengthTest extends TStringOpsTest<ArrayCopyWithConversionsNode> {

    private static final int[] LENGTH_FILTER = {1, 2, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64};
    private static final int ARRAY_LENGTH = 128;

    private final Object[] constantArgs = new Object[5];

    private final byte[] source;
    private final int sourceIndex;
    private final int destinationIndex;
    private final int length;

    public TStringOpsCopyConstantLengthTest(byte[] source, int sourceIndex, int destinationIndex, int length) {
        super(ArrayCopyWithConversionsNode.class);
        this.source = source;
        this.sourceIndex = sourceIndex;
        this.destinationIndex = destinationIndex;
        this.length = length;
    }

    @Parameters(name = "{index}: sourceIndex: {1}, destinationIndex: {2}, length: {3}")
    public static List<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        byte[] source = initializedSource();
        for (int sourceIndex : new int[]{0, 1, 7, 15, 64}) {
            for (int destinationIndex : new int[]{0, 3, 9, 64}) {
                for (int length : LENGTH_FILTER) {
                    if (sourceIndex + length <= source.length && destinationIndex + length <= ARRAY_LENGTH) {
                        ret.add(new Object[]{source, sourceIndex, destinationIndex, length});
                    }
                }
            }
        }
        return ret;
    }

    private static byte[] initializedSource() {
        byte[] source = new byte[ARRAY_LENGTH];
        for (int i = 0; i < source.length; i++) {
            source[i] = (byte) (0x30 + i);
        }
        return source;
    }

    public static byte[] copy(byte[] source, int sourceIndex, byte[] destination, int destinationIndex, int length) {
        intrinsicCopy(source, byteArrayBaseOffset() + sourceIndex, destination, byteArrayBaseOffset() + destinationIndex, length);
        return destination;
    }

    private static void intrinsicCopy(byte[] source, long sourceOffset, byte[] destination, long destinationOffset, int length) {
        int sourceIndex = (int) (sourceOffset - byteArrayBaseOffset());
        int destinationIndex = (int) (destinationOffset - byteArrayBaseOffset());
        for (int i = 0; i < length; i++) {
            destination[destinationIndex + i] = source[sourceIndex + i];
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        invocationPlugins.register(TStringOpsCopyConstantLengthTest.class,
                        new InvocationPlugin("intrinsicCopy", byte[].class, long.class, byte[].class, long.class, int.class) {
                            @Override
                            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode source,
                                            ValueNode sourceOffset, ValueNode destination, ValueNode destinationOffset, ValueNode length) {
                                b.add(new ArrayCopyWithConversionsNode(source, sourceOffset, destination, destinationOffset, length, Stride.S1, Stride.S1));
                                return true;
                            }
                        });
        super.registerInvocationPlugins(invocationPlugins);
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

    @Test
    public void testCopy() {
        int maxVectorSizeBytes = maxVectorSizeBytes();
        Assume.assumeTrue(ArrayCopyWithConversionsNode.canLowerConstantLengthCopy(maxVectorSizeBytes, Stride.S1, Stride.S1, false, length));
        constantArgs[4] = length;
        getCode(getResolvedJavaMethod("copy"));
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertEquals(1, graph.getNodes().filter(ArrayCopyWithConversionsNode.class).count());
        Assert.assertEquals(0, countCopyReads(graph));
        Assert.assertEquals(0, countCopyWrites(graph));
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        int chunkCount = expectedChunkCount();
        Assert.assertEquals(0, graph.getNodes().filter(ArrayCopyWithConversionsNode.class).count());
        Assert.assertEquals(0, graph.getNodes().filter(MemoryAnchorNode.class).count());
        Assert.assertEquals(chunkCount, countCopyReads(graph));
        Assert.assertEquals(chunkCount, countCopyWrites(graph));
    }

    private static int countCopyReads(StructuredGraph graph) {
        int count = 0;
        for (ReadNode read : graph.getNodes().filter(ReadNode.class)) {
            if (read.getLocationIdentity().equals(LocationIdentity.any())) {
                count++;
            }
        }
        return count;
    }

    private static int countCopyWrites(StructuredGraph graph) {
        int count = 0;
        for (WriteNode write : graph.getNodes().filter(WriteNode.class)) {
            if (write.getLocationIdentity().equals(LocationIdentity.any())) {
                count++;
            }
        }
        return count;
    }

    private int expectedChunkCount() {
        int chunkSize = ArrayCopyWithConversionsNode.constantLengthCopyChunkSize(length, maxVectorSizeBytes());
        return (length + chunkSize - 1) / chunkSize;
    }

    private int maxVectorSizeBytes() {
        return ((VectorLoweringProvider) getProviders().getLowerer()).getVectorArchitecture().getMaxVectorLength(IntegerStamp.create(Byte.SIZE));
    }
}
