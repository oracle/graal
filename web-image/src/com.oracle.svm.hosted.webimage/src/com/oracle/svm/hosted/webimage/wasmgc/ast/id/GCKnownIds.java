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

import java.util.EnumMap;
import java.util.List;

import com.oracle.svm.hosted.webimage.wasm.ast.Export;
import com.oracle.svm.hosted.webimage.wasm.ast.id.KnownIds;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmFunctionTemplate;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCCloneSupport;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCFunctionTemplates;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCJSBodyTemplates;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCUnsafeTemplates;
import com.oracle.svm.webimage.wasmgc.WasmExtern;

import jdk.vm.ci.meta.JavaKind;

public class GCKnownIds extends KnownIds {

    /**
     * Identifiers for all Wasm struct types used to represent Java arrays.
     * <p>
     * Has one entry for each primitive and for {@code Object}.
     */
    public final EnumMap<JavaKind, WebImageWasmGCIds.JavaArrayStruct> arrayStructTypes = new EnumMap<>(JavaKind.class);

    /**
     * Identifiers for all Wasm array types used to represent the inner Java arrays.
     * <p>
     * Has one entry for each primitive and for {@code Object}.
     */
    public final EnumMap<JavaKind, WebImageWasmGCIds.JavaInnerArray> innerArrayTypes = new EnumMap<>(JavaKind.class);

    /**
     * Internal field for the array wrapper structs that points to the inner array.
     */
    public final WebImageWasmGCIds.InternalField innerArrayField;

    /**
     * Identifier for array struct that is the supertype of all array structs.
     * <p>
     * For operations that are <em>exactly</em> the same for all arrays (e.g. reading the length), a
     * common supertype for all arrays is required. Otherwise, we would have to implement reading
     * the length for each array type individually.
     */
    public final WasmId.StructType baseArrayType;

    /**
     * Field storing the {@link com.oracle.svm.core.hub.DynamicHub} of the object.
     * <p>
     * This field is added to all struct definitions.
     */
    public final WasmId.Field hubField;

    /**
     * Field storing the identity hash code for objects.
     * <p>
     * This field is added to all struct definitions.
     */
    public final WebImageWasmGCIds.InternalField identityHashCodeField;

    /**
     * Custom field in {@link com.oracle.svm.core.hub.DynamicHub} containing an array that is used
     * for offset-based field accesses.
     * <p>
     * It's an i32 array and the actual
     * {@link com.oracle.svm.hosted.webimage.wasmgc.ast.StructType.Field} is created in
     * {@link com.oracle.svm.hosted.webimage.wasmgc.ast.visitors.WasmGCElementCreator}.
     *
     * @see WasmGCUnsafeTemplates
     */
    public final WasmId.Field accessDispatchField;
    public final WasmId.ArrayType accessDispatchFieldType;

    /**
     * Custom field of {@link com.oracle.svm.core.hub.DynamicHub} storing the class's vtable.
     */
    public final WasmId.Field vtableField;
    public final WasmId.ArrayType vtableFieldType;

    /**
     * Custom field of {@link com.oracle.svm.core.hub.DynamicHub} storing the class's closed type
     * world type check slots.
     */
    public final WasmId.Field typeCheckSlotsField;
    public final WasmId.ArrayType typeCheckSlotsFieldType;

    /**
     * Custom field of {@link com.oracle.svm.core.hub.DynamicHub} storing a function pointer that
     * allocates an uninitialized object.
     */
    public final WasmId.Field newInstanceField;
    public final WasmId.FuncType newInstanceFieldType;

    /**
     * Custom field of {@link com.oracle.svm.core.hub.DynamicHub} storing a function pointer that
     * clones an object of that type.
     */
    public final WasmId.Field cloneField;
    public final WasmId.FuncType cloneFieldType;

    /**
     * Custom field in {@link WasmExtern} storing a reference to a host object ({@code externref}).
     *
     * @see WasmExtern
     */
    public final WebImageWasmGCIds.InternalField embedderField;

    /**
     * Custom field in {@link org.graalvm.webimage.api.JSValue} storing a {@link WasmExtern}
     * instance holding the JS object associated with the value.
     */
    public final WebImageWasmGCIds.InternalField jsNativeValueField;

    /**
     * Table holding all image heap objects while the image heap is initialized.
     *
     * @see com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCHeapWriter
     */
    public final WasmId.Table imageHeapObjectTable;

    /**
     * Identifier for data segment storing image heap data.
     */
    public final WasmId.Data dataSegmentId;

    public final WasmGCFunctionTemplates.ToExtern toExternTemplate;
    public final WasmGCFunctionTemplates.WrapExtern wrapExternTemplate;
    public final WasmGCFunctionTemplates.ArrayElementLoad arrayLoadTemplate;
    public final WasmGCFunctionTemplates.ArrayLength arrayLengthTemplate;
    public final WasmGCFunctionTemplates.ArrayElementStore arrayStoreTemplate;
    public final WasmGCFunctionTemplates.ArrayCreate arrayCreateTemplate;
    public final WasmGCFunctionTemplates.InstanceCreate instanceCreateTemplate;
    public final WasmGCFunctionTemplates.AllocatingBox allocatingBoxTemplate;
    public final WasmGCFunctionTemplates.GetFunctionIndex getFunctionIndexTemplate;
    public final WasmGCFunctionTemplates.IndirectCallBridge indirectCallBridgeTemplate;
    public final WasmGCFunctionTemplates.FillHeapObject fillHeapObjectTemplate;
    public final WasmGCFunctionTemplates.FillHeapArray fillHeapArrayTemplate;
    public final WasmGCFunctionTemplates.ArrayCopy arrayCopyTemplate;
    public final WasmGCFunctionTemplates.Throw throwTemplate;
    public final WasmGCFunctionTemplates.UnsafeCreate unsafeCreateTemplate;

    public final WasmGCUnsafeTemplates.FieldAccess fieldAccessTemplate;
    public final WasmGCUnsafeTemplates.ArrayAccess arrayAccessTemplate;
    public final WasmGCUnsafeTemplates.DispatchAccess dispatchAccessTemplate;
    public final WasmGCUnsafeTemplates.GetDispatchIndex getDispatchIndexTemplate;

    public final WasmGCCloneSupport.GenericCloneTemplate genericCloneTemplate;
    public final WasmGCCloneSupport.ObjectCloneTemplate objectCloneTemplate;
    public final WasmGCCloneSupport.ArrayCloneTemplate arrayCloneTemplate;

    public final WasmGCJSBodyTemplates.ExtractJSValue extractJSValueTemplate;
    public final WasmGCJSBodyTemplates.IsJavaObject isJavaObjectTemplate;

    private final List<Export> functionExports;

    public GCKnownIds(WasmIdFactory idFactory) {
        super(idFactory);

        JavaKind[] arrayComponentKinds = new JavaKind[]{JavaKind.Boolean, JavaKind.Byte, JavaKind.Short, JavaKind.Char, JavaKind.Int, JavaKind.Float, JavaKind.Long, JavaKind.Double, JavaKind.Object};

        for (JavaKind kind : arrayComponentKinds) {
            this.arrayStructTypes.computeIfAbsent(kind, idFactory::newJavaArrayStruct);
        }

        for (JavaKind kind : arrayComponentKinds) {
            this.innerArrayTypes.computeIfAbsent(kind, idFactory::newJavaInnerArray);
        }

        this.innerArrayField = idFactory.newInternalField("inner");
        this.baseArrayType = idFactory.newInternalStruct("baseArray");

        this.hubField = idFactory.newInternalField("dynamicHub");
        this.identityHashCodeField = idFactory.newInternalField("identityHashCode");

        this.accessDispatchField = idFactory.newInternalField("accessDispatch");
        this.accessDispatchFieldType = idFactory.newInternalArray("accessDispatchTable");

        this.vtableField = idFactory.newInternalField("vtable");
        this.vtableFieldType = idFactory.newInternalArray("vtable");

        this.typeCheckSlotsField = idFactory.newInternalField("closedTypeWorldTypeCheckSlots");
        this.typeCheckSlotsFieldType = idFactory.newInternalArray("closedTypeWorldTypeCheckSlots");

        this.newInstanceField = idFactory.newInternalField("newInstance");
        this.newInstanceFieldType = idFactory.newInternalFuncType("newInstance");

        this.cloneField = idFactory.newInternalField("clone");
        this.cloneFieldType = idFactory.newInternalFuncType("clone");

        this.embedderField = idFactory.newInternalField("embedderObject");

        this.jsNativeValueField = idFactory.newInternalField("jsNativeValue");

        this.imageHeapObjectTable = idFactory.newTable();

        this.dataSegmentId = idFactory.newDataSegment("imageHeap");

        this.toExternTemplate = new WasmGCFunctionTemplates.ToExtern(idFactory);
        this.wrapExternTemplate = new WasmGCFunctionTemplates.WrapExtern(idFactory);
        this.arrayLoadTemplate = new WasmGCFunctionTemplates.ArrayElementLoad(idFactory);
        this.arrayLengthTemplate = new WasmGCFunctionTemplates.ArrayLength(idFactory);
        this.arrayStoreTemplate = new WasmGCFunctionTemplates.ArrayElementStore(idFactory);
        this.arrayCreateTemplate = new WasmGCFunctionTemplates.ArrayCreate(idFactory);
        this.instanceCreateTemplate = new WasmGCFunctionTemplates.InstanceCreate(idFactory);
        this.allocatingBoxTemplate = new WasmGCFunctionTemplates.AllocatingBox(idFactory);
        this.getFunctionIndexTemplate = new WasmGCFunctionTemplates.GetFunctionIndex(idFactory);
        this.indirectCallBridgeTemplate = new WasmGCFunctionTemplates.IndirectCallBridge(idFactory);
        this.fillHeapObjectTemplate = new WasmGCFunctionTemplates.FillHeapObject(idFactory);
        this.fillHeapArrayTemplate = new WasmGCFunctionTemplates.FillHeapArray(idFactory);
        this.arrayCopyTemplate = new WasmGCFunctionTemplates.ArrayCopy(idFactory);
        this.throwTemplate = new WasmGCFunctionTemplates.Throw(idFactory);
        this.unsafeCreateTemplate = new WasmGCFunctionTemplates.UnsafeCreate(idFactory);

        this.fieldAccessTemplate = new WasmGCUnsafeTemplates.FieldAccess(idFactory);
        this.arrayAccessTemplate = new WasmGCUnsafeTemplates.ArrayAccess(idFactory);
        this.dispatchAccessTemplate = new WasmGCUnsafeTemplates.DispatchAccess(idFactory);
        this.getDispatchIndexTemplate = new WasmGCUnsafeTemplates.GetDispatchIndex(idFactory);

        this.genericCloneTemplate = new WasmGCCloneSupport.GenericCloneTemplate(idFactory);
        this.objectCloneTemplate = new WasmGCCloneSupport.ObjectCloneTemplate(idFactory);
        this.arrayCloneTemplate = new WasmGCCloneSupport.ArrayCloneTemplate(idFactory);

        this.extractJSValueTemplate = new WasmGCJSBodyTemplates.ExtractJSValue(idFactory);
        this.isJavaObjectTemplate = new WasmGCJSBodyTemplates.IsJavaObject(idFactory);

        functionExports = List.of(
                        Export.forFunction(unsafeCreateTemplate.requestFunctionId(), "unsafe.create", "Create uninitialized instance of given class"),
                        Export.forFunction(wrapExternTemplate.requestFunctionId(), "extern.wrap", "Wrap externref in WasmExtern"),
                        Export.forFunction(toExternTemplate.requestFunctionId(), "extern.unwrap", "Unwrap Java object to externref"),
                        Export.forFunction(toExternTemplate.requestFunctionId(), "extern.isjavaobject", "Check if reference is a Java Object"),
                        Export.forFunction(arrayLoadTemplate.requestFunctionId(JavaKind.Char), "array.char.read", "Read element of char array"),
                        Export.forFunction(arrayLoadTemplate.requestFunctionId(JavaKind.Object), "array.object.read", "Read element of Object array"),
                        Export.forFunction(arrayLengthTemplate.requestFunctionId(), "array.length", "Length of a Java array"),
                        Export.forFunction(arrayStoreTemplate.requestFunctionId(JavaKind.Char), "array.char.write", "Write element of char array"),
                        Export.forFunction(arrayStoreTemplate.requestFunctionId(JavaKind.Object), "array.object.write", "Write element of Object array"),
                        Export.forFunction(arrayCreateTemplate.requestFunctionId(char.class), "array.char.create", "Create char array"),
                        Export.forFunction(arrayCreateTemplate.requestFunctionId(String.class), "array.string.create", "Create String array"));
    }

    /**
     * Looks up the struct type for the array with the given component kind or the base array
     * struct, if {@code componentKind} is {@code null}.
     */
    public WasmId.StructType getArrayStructType(JavaKind componentKind) {
        return componentKind == null ? baseArrayType : arrayStructTypes.get(componentKind);
    }

    @Override
    public List<WasmFunctionTemplate<?>> getFunctionTemplates() {
        return List.of(wrapExternTemplate,
                        arrayCreateTemplate,
                        instanceCreateTemplate,
                        allocatingBoxTemplate,
                        getFunctionIndexTemplate,
                        indirectCallBridgeTemplate,
                        arrayAccessTemplate,
                        dispatchAccessTemplate,
                        getDispatchIndexTemplate,
                        arrayCopyTemplate,
                        throwTemplate,
                        unsafeCreateTemplate,
                        genericCloneTemplate,
                        extractJSValueTemplate,
                        isJavaObjectTemplate);
    }

    @Override
    public List<WasmFunctionTemplate<?>> getLateFunctionTemplates() {
        return List.of(toExternTemplate,
                        arrayLoadTemplate,
                        arrayLengthTemplate,
                        arrayStoreTemplate,
                        fieldAccessTemplate,
                        fillHeapObjectTemplate,
                        fillHeapArrayTemplate,
                        objectCloneTemplate,
                        arrayCloneTemplate);
    }

    @Override
    public List<Export> getExports() {
        return functionExports;
    }
}
