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

package com.oracle.svm.hosted.webimage.wasmgc.types;

import java.util.List;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmProviders;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCProviders;
import com.oracle.svm.webimage.wasm.types.WasmStorageType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Specialization of {@link WasmUtil} for the WasmGC-based backend.
 * <p>
 * Objects are represented as references to concrete Wasm struct types.
 * <p>
 * The abstract heap type hierarchy of the
 * <a href="https://github.com/WebAssembly/gc/blob/main/proposals/gc/MVP.md#heap-types-1">Wasm GC
 * proposal</a> is as follows:
 *
 * <pre>{@code
 *       any  extern  func
 *        |
 *        eq
 *     /  |   \
 * i31  struct  array
 * }</pre>
 * <p>
 * We extend it with a {@code struct} type for each of the
 * {@linkplain #canonicalizeJavaType(ResolvedJavaType) canonical java types} while maintaining the
 * type hierarchy of the original types:
 *
 * <pre>{@code
 *             any  extern  func
 *              |
 *              eq
 *           /  |   \
 *       i31  struct  array
 *              |
 *           Object (:= java.lang.Object and interface types)
 *          /      \
 *         /        +---------------+
 * Instance Classes                 |
 *   /     |     \                Base Array
 * ...    ...    ...             /          \
 *         |           boolean[], byte[],    Object[]
 * ...    ...    ...   short[],char[],       (:= all non-primitive arrays)
 *                     int[], long[],
 *                     float[], double[]
 * }</pre>
 *
 * In Java {@code A <: B} guarantees that {@code A[] <: B[]}. In Wasm this is only true for
 * immutable arrays, so the type hierarchy of object arrays cannot be mimicked. Thus, we represent
 * all of them as a single wasm type.
 */
public class WasmGCUtil extends WasmUtil {

    private final WebImageWasmGCProviders wasmProviders;

    public WasmGCUtil(WebImageWasmProviders providers) {
        super(providers, ((HostedProviders) providers.getProviders()).getGraphBuilderPlugins());
        this.wasmProviders = (WebImageWasmGCProviders) providers;
    }

    @Override
    public WasmRefType typeForJavaClass(Class<?> clazz) {
        return (WasmRefType) super.typeForJavaClass(clazz);
    }

    @Override
    public WasmRefType getJavaLangObjectType() {
        return (WasmRefType) super.getJavaLangObjectType();
    }

    @Override
    public WasmRefType getHubObjectType() {
        return (WasmRefType) super.getHubObjectType();
    }

    @Override
    public WasmRefType getThrowableType() {
        return (WasmRefType) super.getThrowableType();
    }

    /**
     * Produces the canonical representation of the given type in this backend.
     * <p>
     * For the WasmGC backend, interfaces cannot be directly represented, so they are treated as
     * {@link Object}s.
     * <p>
     * Additionally, the array type hierarchy is simplified to only include primitive arrays and
     * {@code Object[]}.
     */
    @Override
    public ResolvedJavaType canonicalizeJavaType(ResolvedJavaType type) {
        ResolvedJavaType canonical = super.canonicalizeJavaType(type);

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        if (canonical.isInterface()) {
            return metaAccess.lookupJavaType(Object.class);
        } else if (canonical.isArray()) {
            if (canonical.getComponentType().isPrimitive()) {
                // primitive arrays are already canonical
                return canonical;
            } else {
                // Object[] is the canonical type for all non-primitive arrays.
                return metaAccess.lookupJavaType(Object[].class);
            }
        } else {
            return canonical;
        }
    }

    public boolean isJavaLangObject(WasmRefType refType) {
        return refType.equalsWithoutNullability(getJavaLangObjectType());
    }

    /**
     * The {@link WasmId} to refer to the {@code java.lang.Object} struct.
     */
    public WasmId.StructType getJavaLangObjectId() {
        return wasmProviders.idFactory().newJavaStruct(providers.getMetaAccess().lookupJavaType(Object.class));
    }

    public WasmId.StructType getHubObjectId() {
        return wasmProviders.idFactory().newJavaStruct(providers.getMetaAccess().lookupJavaType(DynamicHub.class));
    }

    /**
     * Creates a function type id for a {@link TypeUse} derived from a Java method signature.
     * <p>
     * Any declaration of functions that represent Java methods and any indirect calls that target a
     * Java method should use this method to get the proper function type id.
     * <p>
     * In WasmGC, function signatures follow a type hierarchy, mainly to enable reflective calls. A
     * function type returning a subtype of {@link Object} is a direct subtype of the function type
     * with the same parameter types, but returning {@link Object} (covariant return types). There
     * is no deeper hierarchy, function types returning {@link Integer} and {@link Number} are not
     * related.
     * <p>
     * This is needed for reflective calls. At the core of reflective calls is a method-pointer call
     * in a {@link com.oracle.svm.hosted.reflect.ReflectionExpandSignatureMethod}, specialized on
     * the parameter types and the return {@link JavaKind kind}. This means a reflective call to
     * methods returning {@link Object}, {@link String}, or any other object will happen at the same
     * call site ({@code call_indirect} in this case), thus that call site requires a function type
     * compatible with all possible target functions. That function type is the one returning
     * {@link Object}, which is also the supertype of all function types with the same parameter
     * types and returning a non-Object reference.
     *
     * @param typeUse The {@link TypeUse} derived from the Java method.
     */
    public WasmId.FuncType functionIdForMethod(TypeUse typeUse) {
        WasmIdFactory idFactory = wasmProviders.idFactory();
        WasmValType resultType = typeUse.getSingleResult();
        if (resultType != null && resultType.isRef()) {
            WasmRefType javaLangObjectType = wasmProviders.util().getJavaLangObjectType();
            TypeUse baseTypeUse = new TypeUse(typeUse.params, List.of(javaLangObjectType));
            WebImageWasmIds.DescriptorFuncType baseFuncType = idFactory.newFuncType(new FunctionTypeDescriptor(null, false, baseTypeUse));

            if (typeUse.hasResults() && resultType.equals(javaLangObjectType)) {
                return baseFuncType;
            } else {
                return idFactory.newFuncType(new FunctionTypeDescriptor(baseFuncType, true, typeUse));
            }
        }

        return idFactory.newFuncType(FunctionTypeDescriptor.createSimple(typeUse));
    }

    @Override
    public WasmStorageType storageTypeForJavaType(JavaType type) {
        ResolvedJavaType resolvedType = canonicalizeJavaType(type.resolve(null));

        if (resolvedType.isPrimitive()) {
            return storageTypeForKind(resolvedType.getJavaKind());
        } else if (resolvedType.isArray()) {
            JavaKind componentKind = resolvedType.getComponentType().getJavaKind();

            /*
             * Primitive arrays are represented as their own type while all object arrays map to
             * Object[].
             */
            return wasmProviders.knownIds().arrayStructTypes.get(componentKind).asNullable();
        } else {
            assert resolvedType.isInstanceClass();
            return wasmProviders.idFactory().newJavaStruct(resolvedType).asNullable();
        }
    }

    @Override
    public WasmValType typeForNode(ValueNode n) {
        JavaKind kind = kindForNode(n);

        if (kind.isPrimitive()) {
            return mapPrimitiveType(kind);
        }

        assert kind == JavaKind.Object : kind;

        return typeForStamp(n.stamp(NodeView.DEFAULT));
    }

    @Override
    protected JavaKind kindForStamp(Stamp stamp) {
        if (stamp.isNonObjectPointerStamp()) {
            throw GraalError.shouldNotReachHere("Pointer stamps are not supported in the WasmGC backend: " + stamp);
        }

        return super.kindForStamp(stamp);
    }

    @Override
    public JavaKind kindForNode(ValueNode n) {
        if (n instanceof OffsetAddressNode) {
            // TODO GR-59146 Properly support OffsetAddressNodes
            return JavaKind.Int;
        }

        return super.kindForNode(n);
    }

    /**
     * WasmGC has two kinds of memories: object memory (inside structs and arrays) and linear
     * memory.
     * <p>
     * The focus of this method is to model how values in object memory are accessed. For primitive
     * values this is the same for both kinds of memory (see superclass). Objects can only be stored
     * in object memory, where they are native WasmGC objects, which we represent using
     * {@link JavaKind#Object}. Any other non-object pointers are not supported.
     */
    @Override
    public JavaKind memoryKind(Stamp accessStamp) {
        if (accessStamp instanceof AbstractObjectStamp) {
            return JavaKind.Object;
        } else if (accessStamp instanceof AbstractPointerStamp) {
            throw GraalError.unimplemented("AbstractPointerStamp: " + accessStamp);
        } else {
            return super.memoryKind(accessStamp);
        }
    }
}
