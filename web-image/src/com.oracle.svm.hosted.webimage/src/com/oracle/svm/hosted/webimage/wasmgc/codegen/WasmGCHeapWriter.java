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

package com.oracle.svm.hosted.webimage.wasmgc.codegen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.heap.ImageHeapArray;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapInstance;
import com.oracle.graal.pointsto.heap.ImageHeapPrimitiveArray;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.Hybrid;
import com.oracle.svm.core.image.ImageHeapLayouter.ImageHeapLayouterCallback;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.MethodRef;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.MaterializedConstantFields;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;
import com.oracle.svm.hosted.webimage.wasm.ast.ActiveElements;
import com.oracle.svm.hosted.webimage.wasm.ast.Data;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.Global;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.ArrayCopy;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.ArrayNewFixed;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Const;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.StartFunction;
import com.oracle.svm.hosted.webimage.wasm.ast.Table;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.WasmModule;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds;
import com.oracle.svm.hosted.webimage.wasmgc.WasmFunctionIdConstant;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCAllocationSupport;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCUnsafeSupport;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.hosted.webimage.wasmgc.image.WasmGCHeapLayouter;
import com.oracle.svm.hosted.webimage.wasmgc.image.WasmGCImageHeapLayoutInfo;
import com.oracle.svm.hosted.webimage.wasmgc.image.WasmGCPartition;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmGCUtil;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Emits the Native Image heap into the Wasm module.
 * <p>
 * Due to WasmGC having its own object model, we cannot directly write objects into raw memory, the
 * objects need to be created during startup. The heap is created in two steps:
 * <ol>
 * <li>Allocate all objects with default values. See {@link #initializeObjects()}}</li>
 * <li>Fill object contents. See {@link #createImageHeapInstructions(WasmModule)}</li>
 * </ol>
 *
 * Objects are stored in and loaded from a global image heap object table. Objects referenced in the
 * IR are called "embedded" constants/objects, and they are available as global variables so that
 * they're available in the methods referencing them. These globals are set from the object table
 * during heap initialization.
 * <p>
 * Most objects have their contents emitted as constant instructions in the initialization
 * functions. These are called "pseudo" objects. As an optimization some objects (currently only
 * primitive arrays) have their contents emitted into data segments and use that to bulk-initialize
 * the entire object.
 * <p>
 * Static fields are represented as global variables. Static fields storing primitive types are
 * initialized to their initial value, fields with non-null Object values are set while the
 * remainder of the image heap is initialized (see
 * {@link #registerAndFillStaticFields(WasmModule)}).
 *
 * @see WasmGCHeapLayouter
 * @see WasmGCPartition
 */
public class WasmGCHeapWriter {

    /**
     * Wrapper around {@link ObjectInfo} with WasmGC-specific metadata.
     */
    public static final class ObjectData {
        private final ObjectInfo info;

        /**
         * The global variable that holds the object instance, if this is an embedded object
         * (referenced in the IR, e.g. in a {@link ConstantNode}), otherwise {@code null}.
         * <p>
         * Embedded objects use global variables because they need to accessible from within all
         * function bodies and a global read uses less space than a read from the object table and
         * the associated cast.
         *
         * @see NativeImageCodeCache#initAndGetEmbeddedConstants()
         */
        private final WasmId.Global globalVariable;

        /**
         * Index of this object into the global image heap table.
         * <p>
         * This will be a positive integer after {@link #initializeObjects()} finishes.
         */
        private int index = -1;

        public ObjectData(ObjectInfo info, WasmId.Global globalVariable) {
            this.info = info;
            this.globalVariable = globalVariable;
        }

        public ObjectInfo getInfo() {
            return info;
        }

        public boolean isEmbedded() {
            return this.globalVariable != null;
        }

        public int getIndex() {
            assert index > 0 : "Index not yet set";
            return index;
        }

        public void setIndex(int index) {
            assert this.index == -1 : "Index already set to " + this.index;
            assert index > 0 : "Invalid index: " + index;
            this.index = index;
        }

        public WasmId.Global getGlobalVariable() {
            assert isEmbedded() : "Only embedded objects use a global variable";
            return this.globalVariable;
        }

        public WasmGCPartition getPartition() {
            return (WasmGCPartition) info.getPartition();
        }

        public boolean isPseudo() {
            return getPartition().isPseudo();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');

            sb.append(getInfo().getClazz().getJavaClass()).append(", ");

            if (isEmbedded()) {
                sb.append("embedded, ");
            }

            if (isPseudo()) {
                sb.append("pseudo, ");
            }

            if (index > 0) {
                sb.append("table index: ").append(index).append(", ");
            }

            sb.append(info).append(']');
            return sb.toString();
        }
    }

    private final WebImageCodeCache codeCache;
    private final NativeImageHeap heap;
    private final WebImageWasmGCProviders providers;

    /**
     * Holds all {@link ObjectData} instances and allows fast lookup of {@link ObjectData} from the
     * inner {@link ObjectInfo}.
     * <p>
     * Is a {@link SequencedMap} to ensure a deterministic order of definitions.
     */
    private final SequencedMap<ObjectInfo, ObjectData> objectData = new LinkedHashMap<>();

    /**
     * Active elements that will populate the image heap object table.
     * <p>
     * All image heap objects allocations are added to this in {@link #initializeObjects()}.
     */
    private final ActiveElements objectElements;

    /**
     * I32 array, type of the array used to store table indices for Object arrays.
     */
    private final WasmId.ArrayType indexArrayType;

    /**
     * Stores the temporary arrays created in
     * {@link #createFixedArray(WasmId.Local, WasmId.ArrayType, Instruction[], Consumer)}.
     */
    private final WasmId.Local indexArray;

    /**
     * Type of {@link #dispatchArray}.
     */
    private final WasmId.ArrayType dispatchArrayType;
    /**
     * Stores access dispatch arrays (see {@link GCKnownIds#accessDispatchField}) while they are
     * filled with entries.
     */
    private final WasmId.Local dispatchArray;

    public WasmGCHeapWriter(WebImageCodeCache codeCache, WebImageWasmGCProviders providers) {
        this.codeCache = codeCache;
        this.heap = codeCache.nativeImageHeap;
        this.providers = providers;
        this.objectElements = new ActiveElements(0, providers.util().getJavaLangObjectType());
        this.indexArrayType = providers.knownIds().innerArrayTypes.get(JavaKind.Int);
        this.indexArray = providers.idFactory().newTemporaryVariable(indexArrayType.asNonNull());
        this.dispatchArrayType = providers.knownIds().accessDispatchFieldType;
        this.dispatchArray = providers.idFactory().newTemporaryVariable(dispatchArrayType.asNonNull());
    }

    public WasmGCImageHeapLayoutInfo layout() {
        collectObjectData();
        return (WasmGCImageHeapLayoutInfo) heap.getLayouter().layout(heap, WasmUtil.PAGE_SIZE, ImageHeapLayouterCallback.NONE);
    }

    public void write(WasmGCImageHeapLayoutInfo layout, WasmModule module) {
        byte[] dataSegment = writeDataSegment(layout);
        module.addData(new Data(providers.knownIds().dataSegmentId, dataSegment, "Image heap constants " + dataSegment.length + " bytes"));

        initializeObjects();
        Function initializeHeap = createInitializationFunction(module);
        module.addFunction(initializeHeap);
        module.setStartFunction(new StartFunction(initializeHeap.getId(), "Initialize Image Heap"));

        for (ObjectData data : objectData.values()) {
            if (data.isEmbedded()) {
                WasmId.Global global = data.getGlobalVariable();
                module.addGlobal(new Global(global, true, new Instruction.RefNull((WasmRefType) global.getVariableType()), data));
            }
        }

        module.addTable(new Table(providers.knownIds().imageHeapObjectTable, objectElements, "Image heap table"));
    }

    /**
     * Builds Wasm function that initializes the image heap.
     * <p>
     * Image heap objects are already allocated in a table (see {@link #initializeObjects()}). This
     * function fills any remaining fields in those objects (see
     * {@link #createImageHeapInstructions(WasmModule)}).
     */
    private Function createInitializationFunction(WasmModule module) {
        WebImageWasmIds.InternalFunction initializeImageHeap = providers.idFactory().newInternalFunction("initializeImageHeap");
        // Create start function and fill it with instructions to initialize the image heap
        Function initializeHeap = Function.createSimple(providers.idFactory(), initializeImageHeap, TypeUse.withoutResult(), "Initialize Image Heap");
        Instructions instructions = initializeHeap.getInstructions();
        // Filling of object fields and array elements
        instructions.addAll(createImageHeapInstructions(module));
        // Populating static fields with initial values
        instructions.addAll(registerAndFillStaticFields(module));
        // Patch the hub field for the static field base object to enable unsafe access
        instructions.addAll(patchStaticFieldBaseObjects());
        // Fill table with null values. The table will not be accessed after this point.
        instructions.add(new Instruction.TableFill(providers.knownIds().imageHeapObjectTable, Const.forInt(0), new Instruction.RefNull(providers.util().getJavaLangObjectType()),
                        Const.forInt(objectElements.size())));

        return initializeHeap;
    }

    public SequencedCollection<ObjectData> getObjects() {
        return objectData.sequencedValues();
    }

    public ObjectData getConstantInfo(JavaConstant constant) {
        return objectData.get(heap.getConstantInfo(constant));
    }

    private void collectObjectData() {
        Set<Constant> embeddedConstants = codeCache.getEmbeddedConstants().keySet();

        /*
         * Checks for whether an object is embedded have to be made through this set. It contains
         * exclusively uncompressed constants, while the embeddedConstants set also may contain
         * compressed constants.
         *
         * All constants stored in heap.getObjects() are also uncompressed, and looking in
         * embeddedConstants could produce a false-negative if the constant in embeddedConstant is
         * compressed.
         */
        Set<ObjectInfo> embeddedObjectInfos = HashSet.newHashSet(embeddedConstants.size());

        for (var constant : embeddedConstants) {
            if (constant instanceof JavaConstant javaConstant) {
                embeddedObjectInfos.add(heap.getConstantInfo(javaConstant));
            }
        }

        WasmIdFactory idFactory = providers.idFactory();
        WasmGCUtil util = providers.util();

        int num = 0;
        for (ObjectInfo info : heap.getObjects()) {
            boolean isEmbedded = embeddedObjectInfos.contains(info);
            HostedClass hType = info.getClazz();
            WasmRefType type = ((WasmRefType) util.typeForJavaType(hType)).asNullable();

            WasmId.Global globalVariable = null;
            if (isEmbedded) {
                globalVariable = idFactory.newGlobal(type, "const.heap" + num);
                num++;
            }

            ObjectData data = new ObjectData(info, globalVariable);
            assert !objectData.containsKey(info) : "Duplicate ObjectInfo " + info;
            objectData.put(info, data);
        }
    }

    /**
     * Fills data segment with object data from objects not assigned to the pseudo partition.
     */
    private byte[] writeDataSegment(WasmGCImageHeapLayoutInfo layoutInfo) {
        ByteBuffer bb = ByteBuffer.allocate((int) layoutInfo.getSerializedSize()).order(ByteOrder.LITTLE_ENDIAN);
        for (ObjectData data : objectData.values()) {
            if (!data.isPseudo()) {
                ImageHeapPrimitiveArray imageHeapArray = (ImageHeapPrimitiveArray) data.getInfo().getConstant();
                heap.hConstantReflection.forEachArrayElement(imageHeapArray, (element, index) -> {
                    int offset = (int) (data.info.getOffset() + (index * data.info.getClazz().getComponentType().getStorageKind().getByteCount()));
                    PrimitiveConstant primitive = (PrimitiveConstant) element;

                    switch (primitive.getJavaKind()) {
                        case Boolean -> bb.put(offset, (byte) (primitive.asBoolean() ? 1 : 0));
                        case Byte -> bb.put(offset, (byte) primitive.asInt());
                        case Short -> bb.putShort(offset, (short) primitive.asInt());
                        case Char -> bb.putChar(offset, (char) primitive.asInt());
                        case Int -> bb.putInt(offset, primitive.asInt());
                        case Float -> bb.putFloat(offset, primitive.asFloat());
                        case Long -> bb.putLong(offset, primitive.asLong());
                        case Double -> bb.putDouble(offset, primitive.asDouble());
                        default -> throw VMError.shouldNotReachHere(primitive.toString());
                    }
                });
            }
        }

        return bb.array();
    }

    /**
     * Fills image heap object table with allocations.
     * <p>
     * After this, each {@link ObjectData} is assigned an index into the table that can be used to
     * load the object.
     * <p>
     * The table contains a {@code null} value at index {@code 0}. This way, no extra logic has to
     * be inserted to deal with {@code null} references, code can just load a reference from the
     * object table for all indices and will correctly get {@code null} for index {@code 0}.
     */
    private void initializeObjects() {
        int nullIndex = objectElements.addElement(new Instruction.RefNull(providers.util().getJavaLangObjectType()).setComment("Null Object"));
        assert nullIndex == 0 : nullIndex + " image heap objects were added to image heap table before the null pointer";

        for (ObjectData data : getObjects()) {
            Instruction init = createInitInstruction(data);
            init.setComment(commentForObject(data));
            int objectIndex = objectElements.addElement(init);
            data.setIndex(objectIndex);
        }
    }

    private static boolean shouldGenerateStaticField(HostedField field) {
        /*
         * TODO GR-60442 Can we only check for field.isRead() here? Removing any static field that
         * is only written to.
         */
        return field.isStatic() && field.hasLocation() && field.isAccessed();
    }

    /**
     * Registers static fields as global variables and creates instructions to fill those fields
     * with their initial value (where this is not possible as part of the global declaration).
     * <p>
     * Static fields storing a non-null object value cannot be initialized to the right value during
     * declaration (because the image heap objects are not available at that point). Instead, they
     * are initialized while building the image heap.
     *
     * @return Additional instructions to set initial static field values.
     */
    private List<Instruction> registerAndFillStaticFields(WasmModule module) {
        /*
         * Instructions to set global fields that can't be set during declaration (non-primitive
         * fields).
         */
        List<Instruction> instructions = new ArrayList<>();

        for (HostedField field : heap.hUniverse.getFields()) {
            if (shouldGenerateStaticField(field)) {
                assert field.isWritten() || !field.isValueAvailable() || MaterializedConstantFields.singleton().contains(field.wrapped);

                WasmValType fieldType = providers.util().typeForJavaType(field.getType());
                WasmId.Global staticFieldId = providers.idFactory().forStaticField(fieldType, field);

                /*
                 * If the field is never read (only written), we must not read the field value
                 * because it could result in the heap scanner trying to create a new value and
                 * crash because it is already sealed. Instead, the field gets a default initial
                 * value.
                 */
                Instruction initialValue;
                if (field.isRead()) {
                    JavaConstant fieldValue = readFieldValue(null, field);

                    if (fieldValue instanceof PrimitiveConstant primitiveConstant) {
                        // Primitive fields can be initialized with their value immediately
                        initialValue = Const.forConstant(primitiveConstant);
                    } else {
                        WasmRefType fieldRefType = (WasmRefType) fieldType;
                        /*
                         * Object fields are initialized with a stub null value and later set to the
                         * value loaded from the object table.
                         */
                        initialValue = new Instruction.RefNull(fieldRefType);

                        /*
                         * Reference fields that are not null are set explicitly during image heap
                         * initialization.
                         */
                        if (!fieldValue.isDefaultForKind()) {
                            // This will be an i32 const index into the object table
                            Instruction tableIndex = getArgumentForValue(fieldValue);
                            instructions.add(staticFieldId.setter(WasmGCFunctionTemplates.FillHeapObject.getObject(providers.knownIds(), tableIndex, fieldRefType)));
                        }
                    }
                } else {
                    initialValue = fieldType instanceof WasmRefType fieldRefType ? new Instruction.RefNull(fieldRefType) : Const.defaultForType(fieldType);
                }

                module.addGlobal(new Global(staticFieldId, true, initialValue, "Static Field " + field.format("%T %H.%n")));
            }
        }

        return instructions;
    }

    /**
     * Creates instructions to instantiate the object for the given {@link ObjectData}.
     * <p>
     * Non-array objects are allocated without any content ({@code struct.new_default}). For arrays,
     * the wrapping struct is allocated using {@code struct.new}. The identity hash code is already
     * set and the inner array is also allocated (though still empty). The hub field (because the
     * hub instance is not allocated yet) and the array contents are set later.
     */
    private Instruction createInitInstruction(ObjectData data) {
        ObjectInfo objectInfo = data.getInfo();
        HostedClass hType = objectInfo.getClazz();
        ImageHeapConstant constant = objectInfo.getConstant();

        if (constant instanceof ImageHeapInstance) {
            return new Instruction.StructNew(providers.idFactory().newJavaStruct(hType));
        } else if (constant instanceof ImageHeapArray array) {
            JavaKind elementKind = hType.getComponentType().getJavaKind();
            int length = array.getLength();

            WasmId.ArrayType innerArrayType = providers.knownIds().innerArrayTypes.get(elementKind);

            Instruction innerArray = new Instruction.ArrayNew(innerArrayType, Const.forInt(length));

            WasmRefType hubRefType = providers.util().getHubObjectType();

            return new Instruction.StructNew(providers.knownIds().arrayStructTypes.get(elementKind), new Instruction.RefNull(hubRefType), Const.forInt(data.getInfo().getIdentityHashCode()),
                            innerArray);
        } else {
            throw VMError.shouldNotReachHere(constant.toString());
        }
    }

    /**
     * Creates instructions to populate the contents of all image heap objects.
     * <p>
     * To not exceed limitations on the size of Wasm functions, the instructions for filling up
     * objects are split up into multiple functions, with at most
     * {@link WebImageWasmOptions#getNumberOfImageHeapObjectsPerFunction()} image heap objects being
     * populated per function.
     * <p>
     * See {@link #fillHeapObject(ObjectData, Consumer)} for how individual objects are filled.
     *
     * @return Instructions that are required to populate all image heap objects. If the
     *         initialization is split up into multiple functions, this will contain calls to those
     *         functions.
     */
    private List<Instruction> createImageHeapInstructions(WasmModule module) {
        // Object initializations are split up into partitions of at most this many objects
        final int maxObjects = WebImageWasmOptions.getNumberOfImageHeapObjectsPerFunction();

        List<ObjectData> objects = new ArrayList<>(getObjects());
        int numPartitions = NumUtil.divideAndRoundUp(objects.size(), maxObjects);

        Instructions[] partitions = new Instructions[numPartitions];
        for (int i = 0; i < partitions.length; i++) {
            partitions[i] = new Instructions();
        }

        ListIterator<ObjectData> it = objects.listIterator();
        while (it.hasNext()) {
            int idx = it.nextIndex();
            ObjectData data = it.next();
            int partitionNumber = idx / maxObjects;

            fillHeapObject(data, partitions[partitionNumber]::add);
        }

        if (partitions.length == 1) {
            /*
             * If there is only a single partition, no extra function is created, instead all
             * initialization instructions are returned directly and thus inlined into the main
             * initialization function.
             */
            return partitions[0].get();
        } else {
            List<Instruction> callInstructions = new ArrayList<>(numPartitions);

            // Create one function per partition
            for (int partitionNumber = 0; partitionNumber < partitions.length; partitionNumber++) {
                WebImageWasmIds.InternalFunction partitionFunction = providers.idFactory().newInternalFunction("fillHeapObjects" + partitionNumber);
                Function initializeHeap = Function.createSimple(providers.idFactory(), partitionFunction, TypeUse.withoutResult(), "Fill image heap objects part " + partitionNumber);
                // Fill function with instructions from partition
                initializeHeap.getInstructions().addAll(partitions[partitionNumber].get());
                module.addFunction(initializeHeap);

                callInstructions.add(new Instruction.Call(partitionFunction));
            }
            return callInstructions;
        }
    }

    /**
     * Creates instructions to populate the contents of a given image heap object.
     * <p>
     * Instructions are passed to the {@code gen} callback.
     *
     * @see #fillObject(ObjectData, Consumer)
     * @see #fillArray(ObjectData, Consumer)
     */
    private void fillHeapObject(ObjectData data, Consumer<Instruction> gen) {
        ImageHeapConstant constant = data.getInfo().getConstant();

        if (data.isEmbedded()) {
            // Set the global variable associated with the embedded object from object table
            Instruction targetObject = getterForObject(data);
            gen.accept(new Instruction.GlobalSet(data.getGlobalVariable(), targetObject));
        }

        if (constant instanceof ImageHeapInstance) {
            fillObject(data, gen);
        } else if (constant instanceof ImageHeapArray) {
            fillArray(data, gen);
        } else {
            throw VMError.shouldNotReachHere(constant.toString());
        }
    }

    /**
     * Replace the hub field in the static field base objects (see {@link WasmGCUnsafeSupport}) with
     * a hub with a custom access dispatch array (see
     * {@link #patchStaticFieldBaseObject(Object, boolean, WasmId.Local, Consumer)}).
     */
    private List<Instruction> patchStaticFieldBaseObjects() {
        List<Instruction> instructions = new ArrayList<>();
        WasmId.Local pseudoHub = providers.idFactory().newTemporaryVariable(providers.util().getHubObjectType());

        patchStaticFieldBaseObject(WasmGCUnsafeSupport.STATIC_OBJECT_FIELD_BASE, false, pseudoHub, instructions::add);
        patchStaticFieldBaseObject(WasmGCUnsafeSupport.STATIC_PRIMITIVE_FIELD_BASE, true, pseudoHub, instructions::add);

        return instructions;
    }

    /**
     * Replace the hub field in the given object with a hub with a custom access dispatch array
     * pointing to accessor functions for the static fields (either primitive or object fields).
     * <p>
     * The hub instance is created from scratch and only has the access dispatch field set.
     * According to {@code sun.misc.Unsafe#staticFieldBase}, the base objects do not have to be real
     * object and so it is fine if its hub is incomplete as well.
     *
     * @param base The base object for which the hub field should be replaced.
     * @param isPrimitive Whether this is the base object for primitive static fields or for object
     *            static fields.
     * @param pseudoHubLocal Local variable to temporarily store the custom {@link DynamicHub}
     *            instance.
     */
    private void patchStaticFieldBaseObject(Object base, boolean isPrimitive, WasmId.Local pseudoHubLocal, Consumer<Instruction> gen) {
        WasmGCUtil util = providers.util();
        GCKnownIds knownIds = providers.knownIds();

        JavaConstant constant = providers.getSnippetReflection().forObject(base);
        ObjectData data = getConstantInfo(constant);

        List<HostedField> staticFields = new ArrayList<>();
        for (HostedField f : ((HostedMetaAccess) providers.getMetaAccess()).getUniverse().getFields()) {
            if (shouldGenerateStaticField(f) && f.getStorageKind().isPrimitive() == isPrimitive) {
                staticFields.add(f);
            }
        }

        gen.accept(pseudoHubLocal.setter(new Instruction.StructNew(util.getHubObjectId())));
        gen.accept(new Instruction.StructSet(util.getHubObjectId(), knownIds.accessDispatchField, pseudoHubLocal.getter(), createAccessDispatchArray(staticFields, gen)));
        gen.accept(new Instruction.StructSet(util.getJavaLangObjectId(), knownIds.hubField, WasmGCFunctionTemplates.FillHeapObject.getObject(knownIds, Const.forInt(data.index)),
                        pseudoHubLocal.getter()));

    }

    /**
     * Creates instructions to fill the fields for the given {@link ObjectData}.
     * <p>
     * The object is filled by creating a series of {@code struct.set} instructions, one for each
     * non-default field.
     *
     * @param data Object from which to take field values
     * @param gen Is invoked with each top-level instruction
     */
    private void fillObject(ObjectData data, Consumer<Instruction> gen) {
        assert data.isPseudo() : "Object instances cannot be serialized" + data.getInfo();

        ObjectInfo info = data.getInfo();
        HostedInstanceClass clazz = (HostedInstanceClass) info.getClazz();
        ImageHeapInstance instance = (ImageHeapInstance) info.getConstant();
        DynamicHubLayout dynamicHubLayout = heap.dynamicHubLayout;

        Instructions args = new Instructions();
        args.add(getObjectIndex(data));
        args.add(getHubIndex(data));
        /*
         * TODO GR-57345 Investigate how we can further reduce the need for setting the hashcode
         * field (usually all image heap objects have a non-zero hash code here already because
         * computation is force when the object is added)
         */
        args.add(Const.forInt(data.getInfo().getIdentityHashCode()).setComment("Identity hash code"));

        if (dynamicHubLayout.isDynamicHub(clazz)) {
            // The type this dynamic hub represents
            HostedType objectType = (HostedType) providers.getConstantReflection().asJavaType(instance);
            args.add(createHubAccessDispatchArray(objectType, gen));
            args.add(createHubVtableArray(instance));
            args.add(createHubTypeCheckSlots(instance));
            args.add(createNewInstanceFuncRef(objectType));
            args.add(createCloneFuncRef(objectType));
        } else if (HybridLayout.isHybrid(clazz)) {
            throw VMError.shouldNotReachHere("Found unsupported @" + Hybrid.class.getSimpleName() + " image heap object of type: " + clazz);
        }

        clazz = getFirstClassWithFields(clazz);

        for (HostedField field : WasmGCHeapWriter.getInstanceFields(clazz)) {
            args.add(getArgumentForField(info, field));
        }

        gen.accept(new Instruction.Call(providers.knownIds().fillHeapObjectTemplate.requestFunctionId(clazz), args).setComment(commentForObject(data)));
    }

    /**
     * @param info Can be {@code null} for static fields.
     */
    private JavaConstant readFieldValue(ObjectInfo info, HostedField field) {
        ImageHeapInstance instance = info == null ? null : (ImageHeapInstance) info.getConstant();
        try {
            return heap.hConstantReflection.readFieldValue(field, instance);
        } catch (AnalysisError.TypeNotFoundError ex) {
            throw NativeImageHeap.reportIllegalType(ex.getType(), info);
        }
    }

    /**
     * Reads the value of the given field from the given image heap object and returns a constant
     * Wasm instruction (or a relocation) representing that value.
     */
    private Instruction getArgumentForField(ObjectInfo info, HostedField field) {
        JavaConstant value = readFieldValue(info, field);

        return getArgumentForValue(value).setComment(field.format("%T %h.%n"));
    }

    /**
     * Converts the given value to a Wasm instruction representing that value.
     * <p>
     * Object values are represented as an {@code i32} index into the image heap object table.
     */
    private Instruction getArgumentForValue(JavaConstant value) {
        Instruction arg;
        if (value instanceof PatchedWordConstant patchedConstant) {
            if (patchedConstant.getWord() instanceof MethodPointer methodPointer) {
                arg = Instruction.Relocation.forConstant(new SubstrateMethodPointerConstant(methodPointer));
            } else {
                throw VMError.shouldNotReachHere("Pointers to memory should not appear in the WasmGC image heap: " + value);
            }
        } else if (value.getJavaKind() == JavaKind.Object) {
            if (value.isNull()) {
                /*
                 * null-values are represented as index 0, at which the object table stores a null
                 * reference
                 */
                arg = Const.forInt(0);
            } else {
                arg = Const.forInt(getConstantInfo(value).getIndex());
            }
        } else {
            arg = Const.forConstant((PrimitiveConstant) value);
        }

        return arg;
    }

    /**
     * Creates the sparse dispatch array for unsafe accesses for the given type.
     *
     * @param objectType The type this dynamic hub represents.
     * @param gen Used to append top-level instructions required to construct the array.
     * @return Instruction holding filled access dispatch table array (or a null ref if no dispatch
     *         array is necessary).
     * @see #createAccessDispatchArray(List, Consumer)
     * @see WasmGCUnsafeTemplates
     */
    private Instruction createHubAccessDispatchArray(HostedType objectType, Consumer<Instruction> gen) {
        if (!objectType.getWrapped().isAnySubtypeInstantiated()) {
            // Types that are never instantiated don't need dispatch tables
            return new Instruction.RefNull(dispatchArrayType);
        }

        List<HostedField> instanceFields = Arrays.stream(objectType.getInstanceFields(true)).filter(HostedField::hasLocation).toList();

        return createAccessDispatchArray(instanceFields, gen);
    }

    /**
     * Creates a sparse dispatch array for unsafe accesses to the given list of fields.
     *
     * @param gen Used to append top-level instructions required to construct the array.
     * @return Instruction holding filled access dispatch table array (or a null ref if no dispatch
     *         array is necessary).
     */
    private Instruction createAccessDispatchArray(List<HostedField> allFields, Consumer<Instruction> gen) {
        List<HostedField> readFields = allFields.stream().filter(HostedField::isRead).toList();
        List<HostedField> writtenFields = allFields.stream().filter(HostedField::isWritten).toList();

        if (readFields.isEmpty() && writtenFields.isEmpty()) {
            /*
             * If there are no fields, the access array is not needed at all. If it ever were
             * accessed it would trap anyway, so it might as well be null.
             */
            return new Instruction.RefNull(dispatchArrayType);
        }

        int maxOffset = Stream.concat(readFields.stream(), writtenFields.stream()).mapToInt(ResolvedJavaField::getOffset).max().getAsInt();

        int arrayLength = 2 * (maxOffset + 1);
        gen.accept(dispatchArray.setter(new Instruction.ArrayNew(dispatchArrayType, Const.forInt(arrayLength))));

        /*
         * Elements are filled one-by-one instead of using array.new_fixed because most elements are
         * never set.
         */

        for (HostedField field : readFields) {
            gen.accept(setDispatchArrayElement(dispatchArray, field, true));
        }

        for (HostedField field : writtenFields) {
            gen.accept(setDispatchArrayElement(dispatchArray, field, false));
        }

        return dispatchArray.getter();
    }

    /**
     * Creates vtable array by reading function pointers from {@code DynamicHub#vtable}.
     * <p>
     * All entries are relocations that will later be replaced with function table indices.
     *
     * @param instance Constant containing a {@link DynamicHub} instance
     * @return Instruction holding filled vtable array (or a null reference if no vtable is
     *         necessary).
     */
    private Instruction createHubVtableArray(ImageHeapInstance instance) {
        WasmId.ArrayType vtableFieldType = providers.knownIds().vtableFieldType;
        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();
        MethodRef[] vtable = (MethodRef[]) heap.readInlinedField(dynamicHubLayout.vTableField, instance);

        int vtableLength = vtable.length;

        if (vtableLength == 0) {
            /*
             * Empty vtables will never be accessed (would always result in an index out of bounds
             * trap anyway) and so the field can be left unset.
             */
            return new Instruction.RefNull(vtableFieldType);
        }

        Instruction[] vtableEntries = new Instruction[vtableLength];
        for (int i = 0; i < vtableLength; i++) {
            MethodPointer vtableSlot = (MethodPointer) vtable[i];
            vtableEntries[i] = Instruction.Relocation.forConstant(new SubstrateMethodPointerConstant((vtableSlot)));
        }

        return new ArrayNewFixed(vtableFieldType, vtableEntries);
    }

    /**
     * Creates the custom closed type world type check slots array by reading
     * {@code DynamicHub#closedTypeWorldTypeCheckSlotsField} into a Wasm array (as opposed to using
     * a Java array struct).
     *
     * @param instance Constant containing a {@link DynamicHub} instance
     * @return Instruction holding filled type check slots array.
     */
    private Instruction createHubTypeCheckSlots(ImageHeapInstance instance) {
        WasmId.ArrayType typeCheckSlotsFieldType = providers.knownIds().typeCheckSlotsFieldType;
        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();
        short[] typeIDSlots = (short[]) heap.readInlinedField(dynamicHubLayout.closedTypeWorldTypeCheckSlotsField, instance);
        int typeIDSlotsLength = typeIDSlots.length;

        Instruction[] typeCheckSlotEntries = new Instruction[typeIDSlotsLength];
        for (int i = 0; i < typeIDSlotsLength; i++) {
            typeCheckSlotEntries[i] = Const.forInt(typeIDSlots[i]);
        }

        return new ArrayNewFixed(typeCheckSlotsFieldType, typeCheckSlotEntries);
    }

    /**
     * Creates instruction to fill the {@link GCKnownIds#newInstanceField} field.
     *
     * @param objectType The type this dynamic hub represents.
     */
    private Instruction createNewInstanceFuncRef(HostedType objectType) {
        if (WasmGCAllocationSupport.needsDynamicAllocationTemplate(objectType)) {
            return new Instruction.RefFunc(providers.knownIds().instanceCreateTemplate.requestFunctionId(objectType.getJavaClass()));
        } else {
            return new Instruction.RefNull(providers.knownIds().newInstanceFieldType);
        }
    }

    /**
     * Creates instruction to fill the {@link GCKnownIds#cloneField} field.
     * <p>
     * Any non-cloneable type does not need a function pointer here and is assigned a {@code null}
     * reference.
     *
     * @param objectType The type this dynamic hub represents.
     */
    private Instruction createCloneFuncRef(HostedType objectType) {
        if (WasmGCCloneSupport.needsCloneTemplate(objectType)) {
            if (objectType instanceof HostedInstanceClass hostedInstanceClass) {
                return new Instruction.RefFunc(providers.knownIds().objectCloneTemplate.requestFunctionId(hostedInstanceClass));
            } else if (objectType.isArray()) {
                JavaKind componentKind = objectType.getComponentType().getStorageKind();
                return new Instruction.RefFunc(providers.knownIds().arrayCloneTemplate.requestFunctionId(componentKind));
            }
        }
        return new Instruction.RefNull(providers.knownIds().cloneFieldType);
    }

    /**
     * Produces an instruction to write a function table index into the given dispatch array.
     * <p>
     * The function table at that index will contain the read or write method for the given field.
     * <p>
     * Accessor functions for a field at offset X are placed at offset {@code 2*X} for reads and
     * {@code 2*X+1} for writes in the dispatch array.
     * <p>
     * Function table indices are relocations which will later be replaced with the correct
     * {@code i32} once the function table is populated.
     */
    private Instruction setDispatchArrayElement(WasmId.Local dispatchArray, HostedField field, boolean isRead) {
        WasmGCUnsafeTemplates.FieldAccess fieldAccessTemplate = providers.knownIds().fieldAccessTemplate;

        Instruction index = Const.forInt(isRead ? 2 * field.getOffset() : 2 * field.getOffset() + 1);
        WasmId.Func accessorFunction = isRead ? fieldAccessTemplate.requestReadFunctionId(field) : fieldAccessTemplate.requestWriteFunctionId(field);

        return new Instruction.ArraySet(dispatchArrayType, dispatchArray.getter(), index,
                        Instruction.Relocation.forConstant(new WasmFunctionIdConstant(accessorFunction)));
    }

    /**
     * Creates a call to {@link GCKnownIds#fillHeapArrayTemplate} to fill the elements for the given
     * {@link ObjectData array}.
     * <p>
     * Primitive arrays store their data in a data segment, the offset into which is passed to the
     * fill function. For object arrays, an {@code array(i32)} is created with an index into the
     * object table for each reference.
     *
     * @param data Array from which to take element values
     * @param gen Used to append top-level instructions.
     */
    private void fillArray(ObjectData data, Consumer<Instruction> gen) {
        ObjectInfo info = data.getInfo();
        HostedArrayClass clazz = (HostedArrayClass) info.getClazz();
        ImageHeapArray array = (ImageHeapArray) info.getConstant();
        HostedType componentType = clazz.getComponentType();
        JavaKind componentKind = componentType.getJavaKind();

        int length = array.getLength();

        /*
         * Final arg for the fill array template. Is the data segment offset for primitive arrays
         * and an array of object indices for object arrays.
         */
        Instruction finalArg;
        if (data.getPartition().isPseudo()) {
            Instruction[] instructions = new Instruction[length];
            heap.hConstantReflection.forEachArrayElement(array, (element, index) -> {
                if (element.isNull()) {
                    instructions[index] = Const.forInt(0);
                } else {
                    instructions[index] = Const.forInt(getConstantInfo(element).getIndex());
                }
            });

            finalArg = createFixedArray(indexArray, indexArrayType, instructions, gen);
        } else {
            finalArg = Const.forInt((int) data.getInfo().getOffset()).setComment("Data segment offset");
        }

        WasmId.Func fillFunction = providers.knownIds().fillHeapArrayTemplate.requestFunctionId(componentKind);
        gen.accept(new Instruction.Call(fillFunction,
                        getObjectIndex(data),
                        getHubIndex(data),
                        finalArg).setComment(commentForObject(data)));
    }

    /**
     * Generates instructions to copy elements represented by {@link Instruction instructions} in
     * {@code src} into {@code dest} array.
     * <p>
     * The range {@code [offset, offset + length)} is copied by creating a fixed array (using
     * {@link ArrayNewFixed array.new_fixed}) from {@code src} and using {@link ArrayCopy
     * array.copy} to copy them into {@code dest} array.
     */
    private static Instruction fillArray(WasmId.ArrayType arrayType, Instruction dest, Instruction[] src, int offset, int length) {
        Instruction[] slice = Arrays.copyOfRange(src, offset, offset + length);
        ArrayNewFixed tempArray = new ArrayNewFixed(arrayType, slice);
        return new ArrayCopy(arrayType, arrayType, dest, Const.forInt(offset), tempArray, Const.forInt(0), Const.forInt(length));
    }

    /**
     * Creates a Wasm array with fixed elements. Usually will just create {@code array.new_fixed}.
     * But if there are more than {@link ArrayNewFixed#MAX_LENGTH} elements, the array can't be
     * created in a single instruction. Instead, smaller temporary arrays are created (and stored in
     * {@code arrayLocal}) and then copied into the final array.
     *
     * @param arrayLocal Local variable of type {@code arrayType} that can be used to temporarily
     *            store the array if it can't be created in a single step.
     * @param arrayType The array type to be created.
     * @param instructions The array contents.
     * @param gen Additional instructions required to write to {@code arrayLocal} are passed to
     *            this.
     * @return An instruction that produces an array that contains {@code instructions} as its
     *         elements.
     */
    private static Instruction createFixedArray(WasmId.Local arrayLocal, WasmId.ArrayType arrayType, Instruction[] instructions, Consumer<Instruction> gen) {
        int length = instructions.length;

        if (length > ArrayNewFixed.MAX_LENGTH) {
            gen.accept(arrayLocal.setter(new Instruction.ArrayNew(arrayType, Const.forInt(length))));

            /*
             * Fills up the array by creating a temporary array (using array.new_fixed) and then
             * using array.copy.
             *
             * There are some implementation limits on the number of operands of array.new_fixed, so
             * we do this multiple times to not exceed the maximum length.
             */
            int offset = 0;
            while (offset < length) {
                int sliceLength = Math.min(length - offset, ArrayNewFixed.MAX_LENGTH);
                gen.accept(fillArray(arrayType, arrayLocal.getter(), instructions, offset, sliceLength));
                offset += ArrayNewFixed.MAX_LENGTH;
            }

            return arrayLocal.getter();
        } else {
            return new ArrayNewFixed(arrayType, instructions);
        }
    }

    private static Instruction getObjectIndex(ObjectData data) {
        return Const.forInt(data.getIndex()).setComment("Table index");
    }

    private Instruction getHubIndex(ObjectData data) {
        HostedClass clazz = data.getInfo().getClazz();
        JavaConstant hubConstant = providers.getConstantReflection().asJavaClass(clazz);
        return Const.forInt(getConstantInfo(hubConstant).getIndex()).setComment("Table index for hub: " + clazz.toJavaName());
    }

    private Instruction getterForObject(ObjectData data) {
        WasmRefType type = ((WasmRefType) providers.util().typeForJavaType(data.info.getClazz())).asNonNull();
        return new Instruction.RefCast(new Instruction.TableGet(providers.knownIds().imageHeapObjectTable, Const.forInt(data.getIndex())), type);
    }

    private String commentForObject(ObjectData data) {
        HostedClass clazz = data.getInfo().getClazz();
        StringBuilder comment = new StringBuilder(clazz.toJavaName());
        if (DynamicHubLayout.singleton().isDynamicHub(clazz)) {
            ResolvedJavaType t = providers.getConstantReflection().asJavaType(data.getInfo().getConstant());
            Class<?> representedClazz = OriginalClassProvider.getJavaClass(t);
            comment.append(" for ").append(representedClazz.getTypeName());
        }

        return comment.toString();
    }

    private static List<HostedField> filterFields(HostedClass clazz, List<HostedField> fields) {
        Stream<HostedField> stream = fields.stream().filter(HostedField::isRead);
        DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();
        if (dynamicHubLayout.isDynamicHub(clazz)) {
            stream = stream.filter(field -> !dynamicHubLayout.isIgnoredField(field));
        }

        return stream.toList();
    }

    /**
     * All relevant fields available in the given class (including in supertypes).
     * <p>
     * An instance own fields appear before the fields of the superclasses in the returned list.
     *
     * @see #getOwnInstanceFields(HostedClass)
     */
    public static List<HostedField> getInstanceFields(HostedClass clazz) {
        List<HostedField> fields = new ArrayList<>();

        for (HostedClass superType = clazz; superType != null; superType = superType.getSuperclass()) {
            fields.addAll(getOwnInstanceFields(superType));
        }

        return fields;
    }

    /**
     * All relevant fields declared in the given class (and not in a superclass).
     *
     * @see #filterFields(HostedClass, List)
     */
    public static List<HostedField> getOwnInstanceFields(HostedClass clazz) {
        return filterFields(clazz, List.of(clazz.getInstanceFields(false)));
    }

    /**
     * Determines the first class in the type hierarchy that declares its own fields (according to
     * {@link #getOwnInstanceFields(HostedClass)}) or {@link Object} if no class declares any
     * fields.
     * <p>
     * For example, if the given class does not declare any fields, this will find the first
     * superclass that does.
     * <p>
     * Classes that don't declare fields are not interesting when it comes to populating their
     * fields. Instead, they should be treated as if they were the type returned by this method.
     */
    public static HostedInstanceClass getFirstClassWithFields(HostedClass clazz) {
        HostedClass nextSuperClass = clazz;
        while (!nextSuperClass.isJavaLangObject() && WasmGCHeapWriter.getOwnInstanceFields(nextSuperClass).isEmpty()) {
            nextSuperClass = nextSuperClass.getSuperclass();
        }

        return (HostedInstanceClass) nextSuperClass;
    }
}
