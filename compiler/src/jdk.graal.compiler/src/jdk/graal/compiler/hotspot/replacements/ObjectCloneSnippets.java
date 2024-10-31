/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;

import java.util.EnumMap;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.extended.PublishWritesNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyCallNode;
import jdk.vm.ci.meta.JavaKind;

public class ObjectCloneSnippets implements Snippets {

    public static class Templates extends AbstractTemplates {

        final EnumMap<JavaKind, SnippetInfo> arrayCloneMethods = new EnumMap<>(JavaKind.class);

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, HotSpotProviders providers) {
            super(options, providers);
            arrayCloneMethods.put(JavaKind.Boolean, snippet(providers, ObjectCloneSnippets.class, "booleanArrayClone"));
            arrayCloneMethods.put(JavaKind.Byte, snippet(providers, ObjectCloneSnippets.class, "byteArrayClone"));
            arrayCloneMethods.put(JavaKind.Char, snippet(providers, ObjectCloneSnippets.class, "charArrayClone"));
            arrayCloneMethods.put(JavaKind.Short, snippet(providers, ObjectCloneSnippets.class, "shortArrayClone"));
            arrayCloneMethods.put(JavaKind.Int, snippet(providers, ObjectCloneSnippets.class, "intArrayClone"));
            arrayCloneMethods.put(JavaKind.Float, snippet(providers, ObjectCloneSnippets.class, "floatArrayClone"));
            arrayCloneMethods.put(JavaKind.Long, snippet(providers, ObjectCloneSnippets.class, "longArrayClone"));
            arrayCloneMethods.put(JavaKind.Double, snippet(providers, ObjectCloneSnippets.class, "doubleArrayClone"));
            arrayCloneMethods.put(JavaKind.Object, snippet(providers, ObjectCloneSnippets.class, "objectArrayClone"));
        }
    }

    @Snippet
    public static boolean[] booleanArrayClone(boolean[] src) {
        boolean[] result = (boolean[]) NewArrayNode.newUninitializedArray(Boolean.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Boolean, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (boolean[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static byte[] byteArrayClone(byte[] src) {
        byte[] result = (byte[]) NewArrayNode.newUninitializedArray(Byte.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Byte, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (byte[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static short[] shortArrayClone(short[] src) {
        short[] result = (short[]) NewArrayNode.newUninitializedArray(Short.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Short, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (short[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static char[] charArrayClone(char[] src) {
        char[] result = (char[]) NewArrayNode.newUninitializedArray(Character.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Char, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (char[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static int[] intArrayClone(int[] src) {
        int[] result = (int[]) NewArrayNode.newUninitializedArray(Integer.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Int, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (int[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static float[] floatArrayClone(float[] src) {
        float[] result = (float[]) NewArrayNode.newUninitializedArray(Float.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Float, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (float[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static long[] longArrayClone(long[] src) {
        long[] result = (long[]) NewArrayNode.newUninitializedArray(Long.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Long, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (long[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static double[] doubleArrayClone(double[] src) {
        double[] result = (double[]) NewArrayNode.newUninitializedArray(Double.TYPE, src.length);
        ArrayCopyCallNode.disjointArraycopy(src, 0, result, 0, src.length, JavaKind.Double, LocationIdentity.init(), HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        MembarNode.memoryBarrier(MembarNode.FenceKind.ALLOCATION_INIT, LocationIdentity.INIT_LOCATION);
        return (double[]) PublishWritesNode.publishWrites(result);
    }

    @Snippet
    public static Object[] objectArrayClone(Object[] src) {
        /* Since this snippet is lowered early the array must be initialized */
        Object[] result = (Object[]) DynamicNewArrayNode.newArray(GraalDirectives.guardingNonNull(src.getClass().getComponentType()), src.length, JavaKind.Object);
        ArrayCopyCallNode.disjointUninitializedArraycopy(src, 0, result, 0, src.length, JavaKind.Object, HotSpotReplacementsUtil.getHeapWordSize(INJECTED_VMCONFIG));
        return result;
    }
}
