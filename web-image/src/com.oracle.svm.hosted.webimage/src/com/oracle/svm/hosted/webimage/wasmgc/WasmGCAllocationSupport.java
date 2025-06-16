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
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.lang.reflect.Array;

import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCProviders;
import com.oracle.svm.hosted.webimage.wasmgc.snippets.WasmGCAllocationSnippets;

import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;

public class WasmGCAllocationSupport {

    public static final SnippetRuntime.SubstrateForeignCallDescriptor DYNAMIC_NEW_ARRAY = SnippetRuntime.findForeignCall(WasmGCAllocationSupport.class, "dynamicNewArray", HAS_SIDE_EFFECT);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor NEW_MULTI_ARRAY = SnippetRuntime.findForeignCall(WasmGCAllocationSupport.class, "newMultiArray", HAS_SIDE_EFFECT);
    public static final SnippetRuntime.SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SnippetRuntime.SubstrateForeignCallDescriptor[]{DYNAMIC_NEW_ARRAY, NEW_MULTI_ARRAY};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    /**
     * Lowering for {@link DynamicNewArrayNode}.
     * <p>
     * Is behind a foreign call to reduce code size.
     * <p>
     * Foreign call: {@link #DYNAMIC_NEW_ARRAY}
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static Object dynamicNewArray(DynamicHub componentHub, int length) {
        Class<?> checkedComponentClass = getCheckedComponentClass(componentHub);
        return dynamicNewArrayImpl(checkedComponentClass, length);
    }

    /**
     * Intrinsified with a snippet in {@link WasmGCAllocationSnippets} because we can't call
     * arbitrary {@link NodeIntrinsic} methods outside of a snippet.
     */
    @SuppressWarnings("unused")
    private static Object dynamicNewArrayImpl(Class<?> componentType, int length) {
        throw VMError.shouldNotReachHere("This method should have been intrinsified");
    }

    private static Class<?> getCheckedComponentClass(DynamicHub componentType) {
        if (probability(EXTREMELY_FAST_PATH_PROBABILITY, componentType != null) && probability(EXTREMELY_FAST_PATH_PROBABILITY, componentType != DynamicHub.fromClass(void.class))) {
            DynamicHub arrayHub = componentType.getArrayHub();
            if (probability(EXTREMELY_FAST_PATH_PROBABILITY, arrayHub != null) && probability(EXTREMELY_FAST_PATH_PROBABILITY, arrayHub.isInstantiated())) {
                return DynamicHub.toClass(componentType);
            }
        }

        throw arrayHubErrorStub(componentType);
    }

    private static RuntimeException arrayHubErrorStub(DynamicHub componentType) {
        if (componentType == null) {
            throw new NullPointerException("Allocation type is null.");
        } else if (componentType == DynamicHub.fromClass(void.class)) {
            throw new IllegalArgumentException("Cannot allocate void array.");
        } else if (componentType.getArrayHub() == null || !componentType.getArrayHub().isInstantiated()) {
            throw MissingReflectionRegistrationUtils.reportArrayInstantiation(DynamicHub.toClass(componentType), 1);
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(componentType); // ExcludeFromJacocoGeneratedReport
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static Object newMultiArray(Class<?> clazz, int[] dimensions) {
        return newMultiArrayRecursion(clazz, 0, dimensions);
    }

    /**
     * Recursively allocates a multidimensional array.
     * <p>
     * The array is of type {@code clazz} and has the dimensions stored in {@code dimensions} from
     * index {@code idx} onward:
     *
     * <pre>{@code new clazz.elementType()[dimensions[idx]][...][dimensions[dimensions.length - 1]]}</pre>
     */
    private static Object newMultiArrayRecursion(Class<?> clazz, int idx, int[] dimensions) {
        int length = dimensions[idx];
        Object result = Array.newInstance(clazz.componentType(), length);

        if (idx < dimensions.length - 1) {
            // Fill each element with an array with one fewer dimensions
            Object[] objectArray = (Object[]) result;
            for (int i = 0; i < objectArray.length; i++) {
                objectArray[i] = newMultiArrayRecursion(clazz.getComponentType(), idx + 1, dimensions);
            }
        }

        return result;
    }

    /**
     * Unconditionally requests function templates required for dynamic object allocation.
     * <p>
     * This is required to support {@link DynamicNewInstanceNode}, which allocates an object of
     * arbitrary type. We need to request the template for all types for which
     * {@link #needsDynamicAllocationTemplate(HostedType)} returns {@code true} because those types
     * may be instantiated dynamically and need the function id stored in their {@link DynamicHub}
     * instance. The template cannot be a late template (which would make the pre-registration
     * unnecessary) because it needs access to the {@link DynamicHub} image heap object and thus
     * needs a {@link com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool} instance.
     */
    public static void preRegisterAllocationTemplates(WebImageWasmGCProviders providers) {
        for (HostedType type : ((HostedMetaAccess) providers.getMetaAccess()).getUniverse().getTypes()) {
            if (needsDynamicAllocationTemplate(type)) {
                providers.knownIds().instanceCreateTemplate.requestFunctionId(type.getJavaClass());
            }
        }
    }

    /**
     * Whether the given type requires a dynamic allocation function.
     * <p>
     * The {@link DynamicHub} instance for that type will store a pointer to that allocation
     * function in {@link com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds#newInstanceField}.
     * <p>
     * Any instance class (arrays handle dynamic allocations differently) that is unsafe allocated
     * requires this field to be set because the unsafe/dynamic allocation calls the function
     * reference in that field.
     */
    public static boolean needsDynamicAllocationTemplate(HostedType type) {
        return type.isInstanceClass() && type.getWrapped().isUnsafeAllocated();
    }
}
