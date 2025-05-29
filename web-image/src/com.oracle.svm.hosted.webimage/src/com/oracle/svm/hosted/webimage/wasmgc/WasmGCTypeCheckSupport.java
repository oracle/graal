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

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.webimage.wasm.WasmForeignCallDescriptor;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;

/**
 * Foreign call targets implementing functionality for type check nodes {@link InstanceOfNode},
 * {@link InstanceOfDynamicNode}, and {@link ClassIsAssignableFromNode}. Variants of these nodes for
 * which no equivalent {@code ref.test} Wasm instruction can be generated are generated as a call to
 * a method in this class.
 * <p>
 * Reimplements snippets in {@link com.oracle.svm.core.graal.snippets.TypeSnippets} as foreign call
 * targets.
 */
public class WasmGCTypeCheckSupport {
    public static final SubstrateForeignCallDescriptor IS_EXACT_OR_NULL = SnippetRuntime.findForeignCall(WasmGCTypeCheckSupport.class, "isExactTypeOrNull", CallSideEffect.NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor IS_EXACT_NON_NULL = SnippetRuntime.findForeignCall(WasmGCTypeCheckSupport.class, "isExactTypeNonNull", CallSideEffect.NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor INSTANCEOF = SnippetRuntime.findForeignCall(WasmGCTypeCheckSupport.class, "instanceOf", CallSideEffect.NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor INSTANCEOF_DYNAMIC = SnippetRuntime.findForeignCall(WasmGCTypeCheckSupport.class, "instanceOfDynamic", CallSideEffect.NO_SIDE_EFFECT);
    public static final SubstrateForeignCallDescriptor CLASS_IS_ASSIGNABLE_FROM = SnippetRuntime.findForeignCall(WasmGCTypeCheckSupport.class, "classIsAssignableFrom", CallSideEffect.NO_SIDE_EFFECT);

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    IS_EXACT_OR_NULL, IS_EXACT_NON_NULL, INSTANCEOF, INSTANCEOF_DYNAMIC, CLASS_IS_ASSIGNABLE_FROM
    };

    public static final WasmForeignCallDescriptor SLOT_TYPE_CHECK = new WasmForeignCallDescriptor("slotTypeCheck", boolean.class,
                    new Class<?>[]{short.class, short.class, short.class, DynamicHub.class});

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @Node.NodeIntrinsic(ForeignCallNode.class)
    private static native boolean slotTypeCheck0(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, short start, short range, short slot, Class<?> checkedHub);

    private static boolean slotTypeCheck(short start, short range, short slot, DynamicHub checkedHub) {
        return slotTypeCheck0(SLOT_TYPE_CHECK, start, range, slot, DynamicHub.toClass(checkedHub));
    }

    /**
     * Implements {@link InstanceOfNode} and {@link InstanceOfDynamicNode} if they require exact
     * type checks.
     *
     * @param object The object instance to check
     * @param allowsNull If {@code true}, type check succeeds for {@code null} values of
     *            {@code object}.
     * @param exactType The exact type to check against.
     */
    @AlwaysInline("allowsNull is a constant")
    protected static boolean isExactType(Object object, boolean allowsNull, DynamicHub exactType) {
        if (object == null) {
            return allowsNull;
        }

        return DynamicHub.fromClass(object.getClass()) == exactType;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    protected static boolean isExactTypeOrNull(Object object, DynamicHub exactType) {
        return isExactType(object, true, exactType);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    protected static boolean isExactTypeNonNull(Object object, DynamicHub exactType) {
        return isExactType(object, false, exactType);
    }

    /**
     * Implements functionality for {@link InstanceOfNode} for general non-exact type checks.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    protected static boolean instanceOf(Object object, boolean allowsNull, short start, short range, short slot) {
        if (object == null) {
            return allowsNull;
        }
        return slotTypeCheck(start, range, slot, DynamicHub.fromClass(object.getClass()));
    }

    /**
     * Implements functionality {@link InstanceOfDynamicNode} for general non-exact type checks.
     * <p>
     * Effectively computes {@code type.isAssignableFrom(object.getClass())} while taking null
     * values into account.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    protected static boolean instanceOfDynamic(DynamicHub type, Object object, boolean allowsNull) {
        return instanceOf(object, allowsNull, type.getTypeCheckStart(), type.getTypeCheckRange(), type.getTypeCheckSlot());
    }

    /**
     * Implements the functionality for {@link ClassIsAssignableFromNode}.
     * <p>
     * Effectively computes {@code thisClass.isAssignableFrom(otherClass)}
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    protected static boolean classIsAssignableFrom(DynamicHub thisClass, DynamicHub otherClass) {
        return slotTypeCheck(thisClass.getTypeCheckStart(), thisClass.getTypeCheckRange(), thisClass.getTypeCheckSlot(), otherClass);
    }
}
