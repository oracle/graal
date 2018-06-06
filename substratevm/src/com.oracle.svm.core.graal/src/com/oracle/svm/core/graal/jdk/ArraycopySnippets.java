/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
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

import com.oracle.svm.core.annotate.Uninterruptible;
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

    protected static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{ARRAYCOPY};

    /**
     * The actual implementation of {@link System#arraycopy}, called via the foreign call
     * {@link #ARRAYCOPY}.
     */
    @SubstrateForeignCallTarget
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
        if (fromIndex < 0 || toIndex < 0 || length < 0 || fromIndex + length > KnownIntrinsics.readArrayLength(fromArray) || toIndex + length > KnownIntrinsics.readArrayLength(toArray)) {
            throw new IndexOutOfBoundsException();
        }
    }

    private static void primitiveCopyForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int le) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(le, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(le, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(le));
        UnsignedWord size = elementSize.multiply(length);
        UnsignedWord i = WordFactory.zero();

        if (size.and(1).notEqual(0)) {
            ObjectAccess.writeByte(toArray, toOffset.add(i), ObjectAccess.readByte(fromArray, fromOffset.add(i)));
            size = size.subtract(1);
            i = i.add(1);
        }
        if (size.and(3).notEqual(0)) {
            ObjectAccess.writeShort(toArray, toOffset.add(i), ObjectAccess.readShort(fromArray, fromOffset.add(i)));
            size = size.subtract(2);
            i = i.add(2);
        }
        if (size.and(7).notEqual(0)) {
            ObjectAccess.writeInt(toArray, toOffset.add(i), ObjectAccess.readInt(fromArray, fromOffset.add(i)));
            size = size.subtract(4);
            i = i.add(4);
        }
        while (i.belowThan(size)) {
            ObjectAccess.writeLong(toArray, toOffset.add(i), ObjectAccess.readLong(fromArray, fromOffset.add(i)));
            i = i.add(8);
        }
    }

    private static void primitiveCopyBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int le) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(le, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(le, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(le));
        UnsignedWord size = elementSize.multiply(length);

        if (size.and(1).notEqual(0)) {
            size = size.subtract(1);
            ObjectAccess.writeByte(toArray, toOffset.add(size), ObjectAccess.readByte(fromArray, fromOffset.add(size)));
        }
        if (size.and(3).notEqual(0)) {
            size = size.subtract(2);
            ObjectAccess.writeShort(toArray, toOffset.add(size), ObjectAccess.readShort(fromArray, fromOffset.add(size)));
        }
        if (size.and(7).notEqual(0)) {
            size = size.subtract(4);
            ObjectAccess.writeInt(toArray, toOffset.add(size), ObjectAccess.readInt(fromArray, fromOffset.add(size)));
        }
        while (size.aboveThan(0)) {
            size = size.subtract(8);
            ObjectAccess.writeLong(toArray, toOffset.add(size), ObjectAccess.readLong(fromArray, fromOffset.add(size)));
        }
    }

    private static void objectCopyForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int le) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(le, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(le, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(le));
        UnsignedWord size = elementSize.multiply(length);
        objectCopyForwardUninterruptibly(fromArray, fromOffset, toArray, toOffset, elementSize, size);
    }

    @Uninterruptible(reason = "Only the first writeObject has a write-barrier.")
    private static void objectCopyForwardUninterruptibly(Object fromArray, UnsignedWord fromOffset, Object toArray, UnsignedWord toOffset, UnsignedWord elementSize, UnsignedWord size) {
        UnsignedWord copied = WordFactory.zero();
        // Loop-peel the first iteration so I can use BarrieredAccess
        // to put a write barrier on the destination.
        // TODO: I am explicitly not making the first read have a read barrier.
        if (copied.belowThan(size)) {
            BarrieredAccess.writeObject(toArray, toOffset.add(copied), ObjectAccess.readObject(fromArray, fromOffset.add(copied)));
            copied = copied.add(elementSize);

            while (copied.belowThan(size)) {
                ObjectAccess.writeObject(toArray, toOffset.add(copied), ObjectAccess.readObject(fromArray, fromOffset.add(copied)));
                copied = copied.add(elementSize);
            }
        }
    }

    private static void objectCopyBackward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length, int le) {
        UnsignedWord fromOffset = LayoutEncoding.getArrayElementOffset(le, fromIndex);
        UnsignedWord toOffset = LayoutEncoding.getArrayElementOffset(le, toIndex);
        UnsignedWord elementSize = WordFactory.unsigned(LayoutEncoding.getArrayIndexScale(le));
        UnsignedWord size = elementSize.multiply(length);
        objectCopyBackwardsUninterruptibly(fromArray, fromOffset, toArray, toOffset, elementSize, size);
    }

    @Uninterruptible(reason = "Only the first writeObject has a write-barrier.")
    private static void objectCopyBackwardsUninterruptibly(Object fromArray, UnsignedWord fromOffset, Object toArray, UnsignedWord toOffset, UnsignedWord elementSize, UnsignedWord size) {
        // Loop-peel the first iteration so I can use BarrieredAccess
        // to put a write barrier on the destination.
        // TODO: I am explicitly not making the first read have a read barrier.
        UnsignedWord remaining = size;
        if (remaining.aboveThan(0)) {
            remaining = remaining.subtract(elementSize);
            BarrieredAccess.writeObject(toArray, toOffset.add(remaining), ObjectAccess.readObject(fromArray, fromOffset.add(remaining)));

            while (remaining.aboveThan(0)) {
                remaining = remaining.subtract(elementSize);
                ObjectAccess.writeObject(toArray, toOffset.add(remaining), ObjectAccess.readObject(fromArray, fromOffset.add(remaining)));
            }
        }
    }

    private static void objectStoreCheckCopyForward(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
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

    protected ArraycopySnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);
        lowerings.put(SubstrateArraycopyNode.class, new ArraycopyLowering());
    }

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
}
