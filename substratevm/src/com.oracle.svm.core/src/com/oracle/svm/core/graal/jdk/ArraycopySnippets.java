/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.jdk;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.ForeignCallWithExceptionNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

public final class ArraycopySnippets extends SubstrateTemplates implements Snippets {
    private static final SubstrateForeignCallDescriptor ARRAYCOPY = SnippetRuntime.findForeignCall(ArraycopySnippets.class, "doArraycopy", false, LocationIdentity.ANY_LOCATION);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{ARRAYCOPY};

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    protected ArraycopySnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);
        lowerings.put(SubstrateArraycopyNode.class, new ArraycopyLowering());
        lowerings.put(ArrayCopyWithExceptionNode.class, new ArrayCopyWithExceptionLowering());
    }

    /**
     * The actual implementation of {@link System#arraycopy}, called via the foreign call
     * {@link #ARRAYCOPY}.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void doArraycopy(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        if (fromArray == null || toArray == null) {
            throw new NullPointerException();
        }
        DynamicHub fromHub = KnownIntrinsics.readHub(fromArray);
        DynamicHub toHub = KnownIntrinsics.readHub(toArray);

        if (LayoutEncoding.isPrimitiveArray(fromHub.getLayoutEncoding())) {
            if (fromArray == toArray && fromIndex < toIndex) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                primitiveCopyBackward(fromArray, fromIndex, fromArray, toIndex, length, fromHub.getLayoutEncoding());
                return;
            } else if (fromHub == toHub) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                primitiveCopyForward(fromArray, fromIndex, toArray, toIndex, length, fromHub.getLayoutEncoding());
                return;
            }
        } else if (LayoutEncoding.isObjectArray(fromHub.getLayoutEncoding())) {
            if (fromArray == toArray && fromIndex < toIndex) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                objectCopyBackward(fromArray, fromIndex, fromArray, toIndex, length, fromHub.getLayoutEncoding());
                return;
            } else if (fromHub == toHub || toHub.isAssignableFromHub(fromHub)) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                objectCopyForward(fromArray, fromIndex, toArray, toIndex, length, fromHub.getLayoutEncoding());
                return;
            } else if (LayoutEncoding.isObjectArray(toHub.getLayoutEncoding())) {
                boundsCheck(fromArray, fromIndex, toArray, toIndex, length);
                objectStoreCheckCopyForward(fromArray, fromIndex, toArray, toIndex, length);
                return;
            }
        }
        throw new ArrayStoreException();
    }

    private static void boundsCheck(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        if (fromIndex < 0 || toIndex < 0 || length < 0 || fromIndex > KnownIntrinsics.readArrayLength(fromArray) - length || toIndex > KnownIntrinsics.readArrayLength(toArray) - length) {
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    public static void primitiveCopyForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(layoutEncoding));
        UnsignedWord size = elementSize.multiply(length);

        primitiveCopyForward(fromArray, fromOffset, toArray, toOffset, size);
    }

    public static void primitiveCopyForward(Object from, UnsignedWord fromOffset, Object to, UnsignedWord toOffset, UnsignedWord size) {
        UnsignedWord i = WordFactory.zero();
        if (size.and(1).notEqual(0)) {
            ObjectAccess.writeByte(to, toOffset.add(i), ObjectAccess.readByte(from, fromOffset.add(i)));
            i = i.add(1);
        }
        if (size.and(2).notEqual(0)) {
            ObjectAccess.writeShort(to, toOffset.add(i), ObjectAccess.readShort(from, fromOffset.add(i)));
            i = i.add(2);
        }
        if (size.and(4).notEqual(0)) {
            ObjectAccess.writeInt(to, toOffset.add(i), ObjectAccess.readInt(from, fromOffset.add(i)));
            i = i.add(4);
        }
        while (i.belowThan(size)) {
            ObjectAccess.writeLong(to, toOffset.add(i), ObjectAccess.readLong(from, fromOffset.add(i)));
            i = i.add(8);
        }
    }

    private static void primitiveCopyBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(layoutEncoding));
        UnsignedWord size = elementSize.multiply(length);

        if (size.and(1).notEqual(0)) {
            size = size.subtract(1);
            ObjectAccess.writeByte(toArray, toOffset.add(size), ObjectAccess.readByte(fromArray, fromOffset.add(size)));
        }
        if (size.and(2).notEqual(0)) {
            size = size.subtract(2);
            ObjectAccess.writeShort(toArray, toOffset.add(size), ObjectAccess.readShort(fromArray, fromOffset.add(size)));
        }
        if (size.and(4).notEqual(0)) {
            size = size.subtract(4);
            ObjectAccess.writeInt(toArray, toOffset.add(size), ObjectAccess.readInt(fromArray, fromOffset.add(size)));
        }
        while (size.aboveThan(0)) {
            size = size.subtract(8);
            ObjectAccess.writeLong(toArray, toOffset.add(size), ObjectAccess.readLong(fromArray, fromOffset.add(size)));
        }
    }

    public static void objectCopyForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(layoutEncoding));
        UnsignedWord size = elementSize.multiply(length);

        UnsignedWord copied = WordFactory.zero();
        while (copied.belowThan(size)) {
            BarrieredAccess.writeObject(toArray, toOffset.add(copied), BarrieredAccess.readObject(fromArray, fromOffset.add(copied)));
            copied = copied.add(elementSize);
        }
    }

    private static void objectCopyBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int layoutEncoding) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(layoutEncoding));
        UnsignedWord size = elementSize.multiply(length);

        UnsignedWord remaining = size;
        while (remaining.aboveThan(0)) {
            remaining = remaining.subtract(elementSize);
            BarrieredAccess.writeObject(toArray, toOffset.add(remaining), BarrieredAccess.readObject(fromArray, fromOffset.add(remaining)));
        }
    }

    public static void objectStoreCheckCopyForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        /*
         * This performs also an array bounds check in every loop iteration. However, since we do a
         * store check in every loop iteration, we are slow anyways.
         */
        Object[] from = (Object[]) fromArray;
        Object[] to = (Object[]) toArray;
        for (int i = 0; i < length; i++) {
            to[toIndex + i] = from[fromIndex + i];
        }
    }

    @Snippet
    private static void arraycopySnippet(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        callArraycopy(ARRAYCOPY, fromArray, fromIndex, toArray, toIndex, length);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callArraycopy(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object fromArray, int fromIndex, Object toArray, int toIndex, int length);

    final class ArraycopyLowering implements NodeLoweringProvider<SubstrateArraycopyNode> {
        private final SnippetInfo arraycopyInfo = snippet(ArraycopySnippets.class, "arraycopySnippet");

        @Override
        public void lower(SubstrateArraycopyNode node, LoweringTool tool) {
            Arguments args = new Arguments(arraycopyInfo, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("fromArray", node.getSource());
            args.add("fromIndex", node.getSourcePosition());
            args.add("toArray", node.getDestination());
            args.add("toIndex", node.getDestinationPosition());
            args.add("length", node.getLength());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    final class ArrayCopyWithExceptionLowering implements NodeLoweringProvider<ArrayCopyWithExceptionNode> {
        @Override
        public void lower(ArrayCopyWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ForeignCallWithExceptionNode call = graph.add(new ForeignCallWithExceptionNode(ARRAYCOPY, node.getSource(), node.getSourcePosition(), node.getDestination(),
                            node.getDestinationPosition(), node.getLength()));
            call.setStateAfter(node.stateAfter());
            call.setBci(node.getBci());
            graph.replaceWithExceptionSplit(node, call);
        }
    }
}
