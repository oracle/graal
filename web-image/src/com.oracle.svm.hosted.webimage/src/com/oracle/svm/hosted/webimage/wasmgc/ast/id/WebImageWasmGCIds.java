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

package com.oracle.svm.hosted.webimage.wasmgc.ast.id;

import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.hosted.webimage.wasm.ast.id.ResolverContext;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Defines {@link WasmId}s that are used exclusively in the WasmGC backend.
 */
public final class WebImageWasmGCIds {

    /**
     * Id for any struct type based on a non-array Java object type.
     */
    public static class JavaStruct extends WasmId.StructType {

        public final ResolvedJavaType javaType;

        public JavaStruct(ResolvedJavaType type) {
            this.javaType = type;
            assert type.isInstanceClass() : type;
        }

        @Override
        public String doResolve(ResolverContext ctxt) {
            return ctxt.namingConvention.identForType(javaType);
        }

        @Override
        public String toInnerString() {
            return javaType.toJavaName(true);
        }
    }

    /**
     * Id for any struct that represents a Java array type.
     * <p>
     * One array struct exist for each {@link JavaKind} (except valueless void and illegal).
     * <p>
     * Arrays are represented as Wasm structs that are subtypes of {@link GCKnownIds#baseArrayType}
     * (which in turn subtypes {@linkplain Object java.lang.Object}) to make them subtypes of
     * {@link Object} in the type hierarchy.
     * <p>
     * The struct contains a field with a reference to a Wasm array (see
     * {@link WebImageWasmGCIds.JavaInnerArray}) that holds the array data.
     */
    public static class JavaArrayStruct extends WasmId.StructType {

        public final JavaKind componentKind;

        public JavaArrayStruct(JavaKind componentKind) {
            this.componentKind = componentKind;

            assert componentKind.isPrimitive() || componentKind.isObject() : componentKind;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            // TODO GR-41720 there is currently nothing to ensure that these field names are unique.
            return "struct.array." + componentKind.getJavaName();
        }
    }

    /**
     * Id for struct fields that correspond to some Java field.
     */
    public static class JavaField extends WasmId.Field {

        public final ResolvedJavaField field;

        public JavaField(ResolvedJavaField field) {
            this.field = field;
        }

        @Override
        public String doResolve(ResolverContext ctxt) {
            return ctxt.namingConvention.identForType(field.getDeclaringClass()) + "_" + ctxt.namingConvention.identForProperty(field);
        }

        @Override
        public String toInnerString() {
            return field.format("%H.%n");
        }
    }

    /**
     * Simple named function type. The
     * {@link com.oracle.svm.hosted.webimage.wasmgc.ast.FunctionType} definition for this id has to
     * be created manually and added to the module.
     * <p>
     * Mainly useful if {@link com.oracle.svm.webimage.wasm.types.WasmUtil} is not yet available
     * when creating this id. Otherwise, create a
     * {@link com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor} and use
     * {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds.DescriptorFuncType}.
     */
    public static class InternalFuncType extends WasmId.FuncType {
        private final String name;

        public InternalFuncType(String name) {
            this.name = name;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            /*
             * TODO GR-41720 there is currently nothing to ensure that these struct names are
             * unique.
             */
            return "func." + name;
        }
    }

    /**
     * Simple named struct that does not correspond to a Java type.
     * <p>
     * Used to add non-java structs (e.g. the base array struct)
     */
    public static class InternalStruct extends WasmId.StructType {

        public final String name;

        public InternalStruct(String name) {
            this.name = name;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            /*
             * TODO GR-41720 there is currently nothing to ensure that these struct names are
             * unique.
             */
            return "struct." + name;
        }
    }

    /**
     * Simple named struct field that does not correspond to a Java field.
     * <p>
     * Used to add non-java fields to structs (e.g. vtable).
     */
    public static class InternalField extends WasmId.Field {

        public final String name;

        public InternalField(String name) {
            this.name = name;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            // TODO GR-41720 there is currently nothing to ensure that these field names are unique.
            return "field." + name;
        }
    }

    /**
     * Identifier for the Wasm array types that store the Java array data.
     * <p>
     * There is one array type per kind. Each primitive array gets its own array type and all object
     * arrays are represented using the same {@code Object} array type.
     *
     * @see GCKnownIds#innerArrayTypes
     */
    public static class JavaInnerArray extends WasmId.ArrayType {

        public final JavaKind kind;

        public JavaInnerArray(JavaKind kind) {
            this.kind = kind;
            assert kind.isPrimitive() || kind == JavaKind.Object : kind;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "array." + kind;
        }
    }

    /**
     * Simple named array.
     */
    public static class InternalArray extends WasmId.ArrayType {

        public final String name;

        public InternalArray(String name) {
            this.name = name;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            /*
             * TODO GR-41720 there is currently nothing to ensure that these arrays names are
             * unique.
             */
            return "array." + name;
        }
    }

    public static class StaticField extends WasmId.Global {
        public final ResolvedJavaField field;

        public StaticField(WasmValType variableType, ResolvedJavaField field) {
            super(variableType,
                            "field.static." + WebImageNamingConvention.getInstance().identForType(field.getDeclaringClass()) + "." + WebImageNamingConvention.getInstance().identForProperty(field));
            this.field = field;

            assert field.isStatic() : field;
        }
    }
}
