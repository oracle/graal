/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.ArrayUtil;
import com.oracle.svm.webimage.wasm.WasmForeignCallDescriptor;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.ForeignCallWithExceptionNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyNode;
import jdk.vm.ci.meta.JavaKind;

/**
 * Implements support for {@link ArrayCopyNode} and
 * {@link System#arraycopy(Object, int, Object, int, int)}.
 */
public class WasmGCArrayCopySupport {

    public static final SnippetRuntime.SubstrateForeignCallDescriptor ARRAYCOPY = SnippetRuntime.findForeignCall(WasmGCArrayCopySupport.class, "doArraycopy", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    private static final SnippetRuntime.SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SnippetRuntime.SubstrateForeignCallDescriptor[]{ARRAYCOPY};

    /**
     * These foreign calls are linked to
     * {@link com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds#arrayCopyTemplate} during
     * codegen.
     */
    public static final Map<WasmForeignCallDescriptor, JavaKind> COPY_FOREIGN_CALLS = new HashMap<>();
    public static final WasmForeignCallDescriptor COPY_BYTE = createCopyForeignCall(JavaKind.Byte);
    public static final WasmForeignCallDescriptor COPY_BOOLEAN = createCopyForeignCall(JavaKind.Boolean);
    public static final WasmForeignCallDescriptor COPY_SHORT = createCopyForeignCall(JavaKind.Short);
    public static final WasmForeignCallDescriptor COPY_CHAR = createCopyForeignCall(JavaKind.Char);
    public static final WasmForeignCallDescriptor COPY_INT = createCopyForeignCall(JavaKind.Int);
    public static final WasmForeignCallDescriptor COPY_LONG = createCopyForeignCall(JavaKind.Long);
    public static final WasmForeignCallDescriptor COPY_FLOAT = createCopyForeignCall(JavaKind.Float);
    public static final WasmForeignCallDescriptor COPY_DOUBLE = createCopyForeignCall(JavaKind.Double);
    public static final WasmForeignCallDescriptor COPY_OBJECT = createCopyForeignCall(JavaKind.Object);

    private static WasmForeignCallDescriptor createCopyForeignCall(JavaKind componentKind) {
        WasmForeignCallDescriptor descriptor = new WasmForeignCallDescriptor("arraycopy." + componentKind, void.class,
                        new Class<?>[]{Object.class, int.class, Object.class, int.class, int.class});

        COPY_FOREIGN_CALLS.put(descriptor, componentKind);

        return descriptor;
    }

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    public static void registerLowering(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        lowerings.put(ArrayCopyNode.class, new ArrayCopyLowering());
    }

    /**
     * The actual implementation of {@link System#arraycopy}, called via the foreign call
     * {@link #ARRAYCOPY}.
     * <p>
     * Will call a foreign call descriptor in {@link #COPY_FOREIGN_CALLS} if the copy can be
     * performed without store checks. Otherwise performs a slow path copy using
     * {@link JavaMemoryUtil#copyObjectArrayForwardWithStoreCheck(Object, int, Object, int, int)}.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static void doArraycopy(Object fromArray, int fromIndex, Object toArray, int toIndex, int length) {
        if (fromArray == null || toArray == null) {
            throw new NullPointerException();
        }
        DynamicHub fromHub = KnownIntrinsics.readHub(fromArray);
        Class<?> fromClass = DynamicHub.toClass(fromHub);
        DynamicHub toHub = KnownIntrinsics.readHub(toArray);
        int fromLayoutEncoding = fromHub.getLayoutEncoding();

        if (LayoutEncoding.isArray(fromLayoutEncoding)) {
            /*
             * If the array types are the same, no store checks are necessary (for primitive arrays
             * there are no store checks anyway).
             */
            if (fromHub == toHub) {
                ArrayUtil.boundsCheckInSnippet(fromArray, fromIndex, toArray, toIndex, length);
                if (fromClass == byte[].class) {
                    copy(COPY_BYTE, fromArray, fromIndex, toArray, toIndex, length);
                } else if (fromClass == boolean[].class) {
                    copy(COPY_BOOLEAN, fromArray, fromIndex, toArray, toIndex, length);
                } else if (fromClass == short[].class) {
                    copy(COPY_SHORT, fromArray, fromIndex, toArray, toIndex, length);
                } else if (fromClass == char[].class) {
                    copy(COPY_CHAR, fromArray, fromIndex, toArray, toIndex, length);
                } else if (fromClass == int[].class) {
                    copy(COPY_INT, fromArray, fromIndex, toArray, toIndex, length);
                } else if (fromClass == long[].class) {
                    copy(COPY_LONG, fromArray, fromIndex, toArray, toIndex, length);
                } else if (fromClass == float[].class) {
                    copy(COPY_FLOAT, fromArray, fromIndex, toArray, toIndex, length);
                } else if (fromClass == double[].class) {
                    copy(COPY_DOUBLE, fromArray, fromIndex, toArray, toIndex, length);
                } else {
                    assert LayoutEncoding.isObjectArray(fromLayoutEncoding);
                    copy(COPY_OBJECT, fromArray, fromIndex, toArray, toIndex, length);
                }
                return;
            } else if (LayoutEncoding.isObjectArray(fromLayoutEncoding) && LayoutEncoding.isObjectArray(toHub.getLayoutEncoding())) {
                ArrayUtil.boundsCheckInSnippet(fromArray, fromIndex, toArray, toIndex, length);
                /*
                 * If the target array is a subtype of the source array, no illegal stores can
                 * happen. Otherwise, slow path copy with store checks has to be performed.
                 */
                if (DynamicHub.toClass(toHub).isAssignableFrom(DynamicHub.toClass(fromHub))) {
                    copy(COPY_OBJECT, fromArray, fromIndex, toArray, toIndex, length);
                } else {
                    JavaMemoryUtil.copyObjectArrayForwardWithStoreCheck(fromArray, fromIndex, toArray, toIndex, length);
                }
                return;
            }
        }

        throw new ArrayStoreException();
    }

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native void copy(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, Object fromArray, int fromIndex, Object toArray, int toIndex, int length);

    static final class ArrayCopyLowering implements NodeLoweringProvider<ArrayCopyNode> {
        @Override
        public void lower(ArrayCopyNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ForeignCallWithExceptionNode call = graph.add(new ForeignCallWithExceptionNode(ARRAYCOPY, node.getSource(), node.getSourcePosition(), node.getDestination(),
                            node.getDestinationPosition(), node.getLength()));
            call.setStateAfter(node.stateAfter());
            call.setStateDuring(node.stateDuring());
            call.setBci(node.bci());
            graph.replaceWithExceptionSplit(node, call);
        }
    }
}
