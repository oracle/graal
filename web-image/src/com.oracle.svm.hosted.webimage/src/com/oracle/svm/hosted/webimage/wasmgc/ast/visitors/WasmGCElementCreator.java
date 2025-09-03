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

package com.oracle.svm.hosted.webimage.wasmgc.ast.visitors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.graalvm.webimage.api.JSValue;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.WasmModule;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds;
import com.oracle.svm.hosted.webimage.wasm.ast.visitors.WasmElementCreator;
import com.oracle.svm.hosted.webimage.wasmgc.ast.ArrayType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.FieldType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.FunctionType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.RecTypeGroupBuilder;
import com.oracle.svm.hosted.webimage.wasmgc.ast.RecursiveGroup;
import com.oracle.svm.hosted.webimage.wasmgc.ast.StructType;
import com.oracle.svm.hosted.webimage.wasmgc.ast.TypeDefinition;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.WebImageWasmGCIds;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCCloneSupport;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCProviders;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmPackedType;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmStorageType;
import com.oracle.svm.webimage.wasm.types.WasmValType;
import com.oracle.svm.webimage.wasmgc.WasmExtern;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

public class WasmGCElementCreator extends WasmElementCreator {

    private final WebImageWasmGCProviders wasmProviders;
    private final WasmIdFactory idFactory;
    private final GCKnownIds knownIds;

    protected Set<WasmId> processedIds = new HashSet<>();

    /**
     * Recursive group containing all type definitions.
     * <p>
     * A recursive group is needed because types may have cyclic references.
     * <p>
     * TODO GR-47021 partition into minimal rec groups
     */
    protected RecTypeGroupBuilder recGroupBuilder = RecursiveGroup.builder();

    public WasmGCElementCreator(WebImageWasmGCProviders wasmProviders) {
        super();
        this.wasmProviders = wasmProviders;
        this.idFactory = wasmProviders.idFactory();
        this.knownIds = wasmProviders.knownIds();
    }

    @Override
    protected void registerType(WasmId.Type id) {
        if (!processedIds.add(id)) {
            return;
        }

        if (id == knownIds.baseArrayType) {
            registerBaseArrayType();
        } else if (id == knownIds.accessDispatchFieldType) {
            registerAccessDispatchArray();
        } else if (id == knownIds.vtableFieldType) {
            registerVtableArray();
        } else if (id == knownIds.typeCheckSlotsFieldType) {
            registerTypeCheckSlotsArray();
        } else if (id == knownIds.newInstanceFieldType) {
            registerNewInstanceFuncType();
        } else if (id == knownIds.cloneFieldType) {
            registerCloneFuncType();
        } else {
            switch (id) {
                case WebImageWasmGCIds.JavaStruct javaStruct -> registerNewJavaStructType(javaStruct);
                case WebImageWasmGCIds.JavaArrayStruct javaArrayStruct -> registerNewJavaArrayStruct(javaArrayStruct);
                case WebImageWasmGCIds.JavaInnerArray javaInnerArray -> registerNewJavaInnerArray(javaInnerArray);
                case WebImageWasmIds.DescriptorFuncType funcType -> registerNewFunctionType(funcType);
                default -> throw GraalError.shouldNotReachHere(id.toString());
            }
        }
    }

    /**
     * Adds the given definition to the {@link #recGroupBuilder} and ensures any types referenced in
     * the definition are also registered.
     */
    protected void registerTypeDefinition(TypeDefinition definition) {
        recGroupBuilder.addTypeDefinition(definition);

        if (definition.supertype != null) {
            registerType(definition.supertype);
        }

        switch (definition) {
            case ArrayType arrayType -> visitType(arrayType.elementType.storageType);
            case StructType structType -> {
                for (var field : structType.fields) {
                    visitType(field.fieldType.storageType);
                }
            }
            case FunctionType funcType -> visitTypeUse(funcType.typeUse);
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(definition);
        }
    }

    @Override
    protected void registerNewFunctionType(WebImageWasmIds.DescriptorFuncType funcType) {
        registerTypeDefinition(funcType.createTypeDefinition(null));
    }

    protected void registerNewJavaStructType(WebImageWasmGCIds.JavaStruct javaStruct) {
        ResolvedJavaType javaType = javaStruct.javaType;

        List<ResolvedJavaField> instanceFields = getInstanceFields(javaType);
        List<StructType.Field> fields = new ArrayList<>(getBaseFields());

        /*
         * The JSValue class gets an additional non-java field holding the externref right after the
         * base fields so that subtyping still works for any subclasses with their own fields.
         */
        if (wasmProviders.getMetaAccess().lookupJavaType(JSValue.class).isAssignableFrom(javaType)) {
            fields.add(new StructType.Field(knownIds.jsNativeValueField, FieldType.mutable(wasmProviders.util().typeForJavaClass(WasmExtern.class)), "JavaScript Native Value"));
        }

        for (ResolvedJavaField field : instanceFields) {
            WasmId.Field fieldId = idFactory.newJavaField(field);
            ResolvedJavaType fieldType = field.getType().resolve(javaType);

            WasmStorageType wasmType = wasmProviders.util().storageTypeForJavaType(fieldType);
            fields.add(new StructType.Field(fieldId, FieldType.mutable(wasmType), field.format("%H.%n(%T)")));
        }

        if (javaType.equals(wasmProviders.getMetaAccess().lookupJavaType(DynamicHub.class))) {
            fields.addAll(getExtraHubFields());
        }

        // The WasmExtern class gets an additional non-java field holding the externref
        if (javaType.equals(wasmProviders.getMetaAccess().lookupJavaType(WasmExtern.class))) {
            fields.add(new StructType.Field(knownIds.embedderField, FieldType.immutable(WasmRefType.EXTERNREF), "Internal field holding a reference to an embedder object"));
        }

        boolean isFinal = false;

        if (javaType instanceof HostedType hostedType) {
            /*
             * The final flag does not directly correspond to Java's final modifier. Due to the
             * closed world assumption any leaf nodes in the inheritance tree are final.
             */
            isFinal = hostedType.getSubTypes().length == 0;
        }

        WasmId.StructType superClassId = null;
        ResolvedJavaType superClass = javaType.getSuperclass();
        if (superClass != null) {
            superClassId = idFactory.newJavaStruct(superClass);
        }

        TypeDefinition def = new StructType(javaStruct, superClassId, isFinal, fields, javaType.toJavaName(true));
        registerTypeDefinition(def);
    }

    protected void registerNewJavaArrayStruct(WebImageWasmGCIds.JavaArrayStruct javaArrayStruct) {
        JavaKind componentKind = javaArrayStruct.componentKind;

        WasmId.StructType superClassId = knownIds.baseArrayType;
        WebImageWasmGCIds.JavaInnerArray javaInnerArray = knownIds.innerArrayTypes.get(componentKind);

        List<StructType.Field> fields = getArrayFields(javaInnerArray.asNonNull());

        WebImageWasmGCIds.JavaArrayStruct arrayStruct = knownIds.arrayStructTypes.get(componentKind);
        TypeDefinition def = new StructType(arrayStruct, superClassId, true, fields, componentKind.getJavaName() + "[]");
        registerTypeDefinition(def);
    }

    protected void registerNewJavaInnerArray(WebImageWasmGCIds.JavaInnerArray javaInnerArray) {
        JavaKind kind = javaInnerArray.kind;

        FieldType fieldType = FieldType.mutable(wasmProviders.util().storageTypeForKind(kind));
        TypeDefinition def = new ArrayType(javaInnerArray, null, true, fieldType, "Inner array for " + kind + "[]");
        registerTypeDefinition(def);
    }

    protected void registerBaseArrayType() {
        WasmId.StructType objectId = wasmProviders.util().getJavaLangObjectId();

        List<StructType.Field> fields = getArrayFields(WasmRefType.Kind.ARRAY.nonNull());

        TypeDefinition def = new StructType(knownIds.baseArrayType, objectId, false, fields, "Base type for all array wrapper structs");
        registerTypeDefinition(def);
    }

    protected void registerAccessDispatchArray() {
        TypeDefinition def = new ArrayType(knownIds.accessDispatchFieldType, null, true, FieldType.mutable(WasmPrimitiveType.i32), "Holds function table indices for unsafe access dispatch");
        registerTypeDefinition(def);
    }

    protected void registerVtableArray() {
        TypeDefinition def = new ArrayType(knownIds.vtableFieldType, null, true, FieldType.immutable(WasmPrimitiveType.i64), "Holds function table indices for vtable dispatch");
        registerTypeDefinition(def);
    }

    protected void registerTypeCheckSlotsArray() {
        TypeDefinition def = new ArrayType(knownIds.typeCheckSlotsFieldType, null, true, FieldType.immutable(WasmPackedType.i16), "Closed type world type check slots");
        registerTypeDefinition(def);
    }

    protected void registerNewInstanceFuncType() {
        TypeDefinition def = new FunctionType(knownIds.newInstanceFieldType, null, false, TypeUse.withResult(wasmProviders.util().getJavaLangObjectId().asNonNull()), null);
        registerTypeDefinition(def);
    }

    protected void registerCloneFuncType() {
        TypeDefinition def = new FunctionType(knownIds.cloneFieldType, null, true, WasmGCCloneSupport.getCloneFieldTypeUse(wasmProviders.util()), null);
        registerTypeDefinition(def);
    }

    protected StructType.Field getInnerArrayField(WasmValType fieldType) {
        return new StructType.Field(knownIds.innerArrayField, FieldType.immutable(fieldType), "Inner array field for array structs");
    }

    /**
     * Fields common to all objects (including arrays) that are not Java fields (e.g. identity hash
     * code).
     */
    protected List<StructType.Field> getBaseFields() {
        WasmRefType hubWasmType = wasmProviders.util().getHubObjectType();
        return List.of(
                        new StructType.Field(knownIds.hubField, FieldType.mutable(hubWasmType), "Dynamic Hub"),
                        new StructType.Field(knownIds.identityHashCodeField, FieldType.mutable(WasmPrimitiveType.i32), "Identity Hash Code"));
    }

    protected List<StructType.Field> getExtraHubFields() {
        return List.of(
                        new StructType.Field(knownIds.accessDispatchField, FieldType.mutable(knownIds.accessDispatchFieldType.asNullable()), "Access dispatch table"),
                        new StructType.Field(knownIds.vtableField, FieldType.mutable(knownIds.vtableFieldType.asNullable()), "Vtable holding indices into the global function table"),
                        new StructType.Field(knownIds.typeCheckSlotsField, FieldType.mutable(knownIds.typeCheckSlotsFieldType.asNullable()), "Closed type world type check slots"),
                        new StructType.Field(knownIds.newInstanceField, FieldType.mutable(knownIds.newInstanceFieldType.asNullable()), "Function pointer to create a new instance of this type"),
                        new StructType.Field(knownIds.cloneField, FieldType.mutable(knownIds.cloneFieldType.asNullable()), "Function pointer to clone object of this type"));
    }

    /**
     * Struct fields for the array structs.
     * <p>
     * Array struct have all {@link #getBaseFields() base fields} and a field pointing to the inner
     * array.
     */
    protected List<StructType.Field> getArrayFields(WasmValType innerArrayType) {
        List<StructType.Field> fields = new ArrayList<>(getBaseFields());
        fields.add(getInnerArrayField(innerArrayType));
        return fields;
    }

    /**
     * Returns all fields in the type (including superclasses) so that the superclasses' field list
     * is a prefix of their subclasses.
     * <p>
     * This is required to satisfy Wasm's structural subtyping rules for checking type definitions.
     * <p>
     * It seems that {@link ResolvedJavaType#getInstanceFields(boolean)} does not guarantee that, it
     * only guarantees that it returns the field in the same order everytime for each individual
     * type.
     *
     * TODO GR-56746 Consolidate with and reuse
     * {@link com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCHeapWriter#getOwnInstanceFields(HostedClass)}
     * here to filter out fields. We may also be able to filter out fields that are never accessed.
     */
    public static List<ResolvedJavaField> getInstanceFields(ResolvedJavaType javaType) {
        List<ResolvedJavaField> fields = new LinkedList<>();

        for (ResolvedJavaType superType = javaType; superType != null; superType = superType.getSuperclass()) {
            for (ResolvedJavaField field : superType.getInstanceFields(false)) {
                fields.addFirst(field);
            }
        }

        return fields;
    }

    @Override
    public void visitModule(WasmModule m) {
        super.visitModule(m);
        m.addRecursiveGroup(recGroupBuilder.build("All type definitions"));
    }
}
