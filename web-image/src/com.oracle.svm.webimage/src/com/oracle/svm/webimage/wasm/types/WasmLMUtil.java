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

package com.oracle.svm.webimage.wasm.types;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.AbstractPointerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Specialization of {@link WasmUtil} for the Wasm linear-memory (LM) based backend
 * <p>
 * Here, objects are represented as 32-bit pointers.
 */
public class WasmLMUtil extends WasmUtil {

    /**
     * By convention, WebImage uses i32 for representing references in WASM, because WASM memory
     * addresses are 32 bits.
     */
    public static final WasmPrimitiveType POINTER_TYPE = WasmPrimitiveType.i32;
    public static final JavaKind POINTER_KIND = JavaKind.Int;

    public WasmLMUtil(CoreProviders providers, GraphBuilderConfiguration.Plugins graphBuilderPlugins) {
        super(providers, graphBuilderPlugins);
    }

    @Override
    public WasmPrimitiveType mapType(JavaKind kind) {
        return (WasmPrimitiveType) super.mapType(kind);
    }

    @Override
    public WasmPrimitiveType typeForJavaClass(Class<?> clazz) {
        return POINTER_TYPE;
    }

    /**
     * Any {@code Object} type maps to {@linkplain Object java.lang.Object}.
     */
    @Override
    public ResolvedJavaType canonicalizeJavaType(ResolvedJavaType type) {
        ResolvedJavaType canonical = super.canonicalizeJavaType(type);

        if (canonical.getJavaKind() == JavaKind.Object) {
            return providers.getMetaAccess().lookupJavaType(Object.class);
        }

        return canonical;
    }

    @Override
    public WasmStorageType storageTypeForJavaType(JavaType type) {
        ResolvedJavaType resolvedType = canonicalizeJavaType(type.resolve(null));
        return storageTypeForKind(resolvedType.getJavaKind());
    }

    @Override
    public WasmPrimitiveType typeForNode(ValueNode n) {
        return mapType(kindForNode(n));
    }

    @Override
    protected WasmPrimitiveType storageTypeForObjectStamp(AbstractObjectStamp stamp) {
        return POINTER_TYPE;
    }

    @Override
    protected JavaKind kindForStamp(Stamp stamp) {
        if (stamp.isPointerStamp()) {
            return POINTER_KIND;
        }

        return super.kindForStamp(stamp);
    }

    /**
     * What kind is used when reading or writing the given stamp from or to memory.
     */
    @Override
    public JavaKind memoryKind(Stamp accessStamp) {
        if (accessStamp instanceof AbstractPointerStamp) {
            return POINTER_KIND;
        } else {
            return super.memoryKind(accessStamp);
        }
    }
}
