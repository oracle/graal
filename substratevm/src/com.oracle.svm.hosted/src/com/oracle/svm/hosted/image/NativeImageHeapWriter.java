/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.MaterializedConstantFields;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Writes the native image heap into one or multiple {@link RelocatableBuffer}s.
 */
public final class NativeImageHeapWriter {

    private final NativeImageHeap heap;
    private final ImageHeapLayoutInfo heapLayout;
    private long sectionOffsetOfARelocatablePointer;

    public NativeImageHeapWriter(NativeImageHeap heap, ImageHeapLayoutInfo heapLayout) {
        this.heap = heap;
        this.heapLayout = heapLayout;
        this.sectionOffsetOfARelocatablePointer = -1;
    }

    /**
     * Write the model of the native image heap to the RelocatableBuffers that represent the native
     * image.
     */
    @SuppressWarnings("try")
    public long writeHeap(DebugContext debug, RelocatableBuffer buffer) {
        try (Indent perHeapIndent = debug.logAndIndent("NativeImageHeap.writeHeap:")) {
            for (ObjectInfo info : heap.getObjects()) {
                assert !(info.getConstant() instanceof SubstrateObjectConstant) || !heap.isBlacklisted(info.getObject());
                writeObject(info, buffer);
            }

            // Only static fields that are writable get written to the native image heap,
            // the read-only static fields have been inlined into the code.
            writeStaticFields(buffer);

            heap.getLayouter().writeMetadata(buffer.getByteBuffer(), 0);
        }
        return sectionOffsetOfARelocatablePointer;
    }

    private void writeStaticFields(RelocatableBuffer buffer) {
        /*
         * Write the values of static fields. The arrays for primitive and object fields are empty
         * and just placeholders. This ensures we get the latest version, since there can be
         * Features registered that change the value of static fields late in the native image
         * generation process.
         */
        ObjectInfo primitiveFields = heap.getObjectInfo(StaticFieldsSupport.getStaticPrimitiveFields());
        ObjectInfo objectFields = heap.getObjectInfo(StaticFieldsSupport.getStaticObjectFields());
        for (HostedField field : heap.getUniverse().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.hasLocation() && field.isRead()) {
                assert field.isWritten() || MaterializedConstantFields.singleton().contains(field.wrapped);
                ObjectInfo fields = (field.getStorageKind() == JavaKind.Object) ? objectFields : primitiveFields;
                writeField(buffer, fields, field, null, null);
            }
        }
    }

    private static Object readObjectField(HostedField field, JavaConstant receiver) {
        return SubstrateObjectConstant.asObject(field.readStorageValue(receiver));
    }

    private int referenceSize() {
        return heap.getObjectLayout().getReferenceSize();
    }

    private void mustBeReferenceAligned(int index) {
        assert (index % heap.getObjectLayout().getReferenceSize() == 0) : "index " + index + " must be reference-aligned.";
    }

    private static void verifyTargetDidNotChange(Object target, Object reason, Object targetInfo) {
        if (targetInfo == null) {
            throw NativeImageHeap.reportIllegalType(target, reason);
        }
    }

    private void writeField(RelocatableBuffer buffer, ObjectInfo fields, HostedField field, JavaConstant receiver, ObjectInfo info) {
        int index = fields.getIndexInBuffer(field.getLocation());
        JavaConstant value;
        try {
            value = field.readValue(receiver);
        } catch (AnalysisError.TypeNotFoundError ex) {
            throw NativeImageHeap.reportIllegalType(ex.getType(), info);
        }

        if (value.getJavaKind() == JavaKind.Object && heap.getMetaAccess().isInstanceOf(value, RelocatedPointer.class)) {
            addNonDataRelocation(buffer, index, (RelocatedPointer) SubstrateObjectConstant.asObject(value));
        } else {
            write(buffer, index, value, info != null ? info : field);
        }
    }

    private void write(RelocatableBuffer buffer, int index, JavaConstant con, Object reason) {
        if (con.getJavaKind() == JavaKind.Object) {
            writeReference(buffer, index, con, reason);
        } else {
            writePrimitive(buffer, index, con);
        }
    }

    private final boolean useHeapBase = NativeImageHeap.useHeapBase();
    private final CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);

    void writeReference(RelocatableBuffer buffer, int index, JavaConstant target, Object reason) {
        assert !(heap.getMetaAccess().isInstanceOf(target, WordBase.class)) : "word values are not references";
        mustBeReferenceAligned(index);
        if (target.isNonNull()) {
            ObjectInfo targetInfo = heap.getConstantInfo(target);
            verifyTargetDidNotChange(target, reason, targetInfo);
            if (useHeapBase) {
                int shift = compressEncoding.getShift();
                writeReferenceValue(buffer, index, targetInfo.getAddress() >>> shift);
            } else {
                addDirectRelocationWithoutAddend(buffer, index, referenceSize(), SubstrateObjectConstant.asObject(target));
            }
        }
    }

    private void writeConstant(RelocatableBuffer buffer, int index, JavaKind kind, JavaConstant constant, ObjectInfo info) {
        if (heap.getMetaAccess().isInstanceOf(constant, RelocatedPointer.class)) {
            addNonDataRelocation(buffer, index, (RelocatedPointer) SubstrateObjectConstant.asObject(constant));
            return;
        }

        final JavaConstant con;
        if (heap.getMetaAccess().isInstanceOf(constant, WordBase.class)) {
            Object value = heap.getUniverse().getSnippetReflection().asObject(Object.class, constant);
            con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), ((WordBase) value).rawValue());
        } else if (constant.isNull() && kind == FrameAccess.getWordKind()) {
            con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), 0);
        } else {
            con = constant;
        }
        write(buffer, index, con, info);
    }

    private void writeConstant(RelocatableBuffer buffer, int index, JavaKind kind, Object value, ObjectInfo info) {
        if (value instanceof RelocatedPointer) {
            addNonDataRelocation(buffer, index, (RelocatedPointer) value);
            return;
        }

        final JavaConstant con;
        if (value instanceof WordBase) {
            con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), ((WordBase) value).rawValue());
        } else if (value == null && kind == FrameAccess.getWordKind()) {
            con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), 0);
        } else {
            assert kind == JavaKind.Object || value != null : "primitive value must not be null";
            con = SubstrateObjectConstant.forBoxedValue(kind, value);
        }
        write(buffer, index, con, info);
    }

    private void writeObjectHeader(RelocatableBuffer buffer, int index, ObjectInfo obj) {
        mustBeReferenceAligned(index);

        DynamicHub hub = obj.getClazz().getHub();
        assert hub != null : "Null DynamicHub found during native image generation.";
        ObjectInfo hubInfo = heap.getObjectInfo(hub);
        assert hubInfo != null : "Unknown object " + hub.toString() + " found. Static field or an object referenced from a static field changed during native image generation?";

        ObjectHeader objectHeader = Heap.getHeap().getObjectHeader();
        if (NativeImageHeap.useHeapBase()) {
            long targetOffset = hubInfo.getAddress();
            long headerBits = objectHeader.encodeAsImageHeapObjectHeader(obj, targetOffset);
            writeReferenceValue(buffer, index, headerBits);
        } else {
            // The address of the DynamicHub target will be added by the link editor.
            long headerBits = objectHeader.encodeAsImageHeapObjectHeader(obj, 0L);
            addDirectRelocationWithAddend(buffer, index, hub, headerBits);
        }
    }

    private void addDirectRelocationWithoutAddend(RelocatableBuffer buffer, int index, int size, Object target) {
        assert size == 4 || size == 8;
        assert !NativeImageHeap.spawnIsolates() || heapLayout.isReadOnlyRelocatable(index);
        buffer.addRelocationWithoutAddend(index, size == 8 ? ObjectFile.RelocationKind.DIRECT_8 : ObjectFile.RelocationKind.DIRECT_4, target);
        if (sectionOffsetOfARelocatablePointer == -1) {
            sectionOffsetOfARelocatablePointer = index;
        }
    }

    private void addDirectRelocationWithAddend(RelocatableBuffer buffer, int index, DynamicHub target, long objectHeaderBits) {
        assert !NativeImageHeap.spawnIsolates() || heapLayout.isReadOnlyRelocatable(index);
        buffer.addRelocationWithAddend(index, referenceSize() == 8 ? ObjectFile.RelocationKind.DIRECT_8 : ObjectFile.RelocationKind.DIRECT_4, objectHeaderBits, target);
        if (sectionOffsetOfARelocatablePointer == -1) {
            sectionOffsetOfARelocatablePointer = index;
        }
    }

    /**
     * Adds a relocation for a code pointer or other non-data pointers.
     */
    private void addNonDataRelocation(RelocatableBuffer buffer, int index, RelocatedPointer pointer) {
        mustBeReferenceAligned(index);
        assert pointer instanceof CFunctionPointer : "unknown relocated pointer " + pointer;
        assert pointer instanceof MethodPointer : "cannot create relocation for unknown FunctionPointer " + pointer;
        int pointerSize = ConfigurationValues.getTarget().wordSize;
        addDirectRelocationWithoutAddend(buffer, index, pointerSize, pointer);
    }

    private static void writePrimitive(RelocatableBuffer buffer, int index, JavaConstant con) {
        ByteBuffer bb = buffer.getByteBuffer();
        switch (con.getJavaKind()) {
            case Boolean:
                bb.put(index, (byte) con.asInt());
                break;
            case Byte:
                bb.put(index, (byte) con.asInt());
                break;
            case Char:
                bb.putChar(index, (char) con.asInt());
                break;
            case Short:
                bb.putShort(index, (short) con.asInt());
                break;
            case Int:
                bb.putInt(index, con.asInt());
                break;
            case Long:
                bb.putLong(index, con.asLong());
                break;
            case Float:
                bb.putFloat(index, con.asFloat());
                break;
            case Double:
                bb.putDouble(index, con.asDouble());
                break;
            default:
                throw shouldNotReachHere(con.getJavaKind().toString());
        }
    }

    private void writeReferenceValue(RelocatableBuffer buffer, int index, long value) {
        if (referenceSize() == Long.BYTES) {
            buffer.getByteBuffer().putLong(index, value);
        } else if (referenceSize() == Integer.BYTES) {
            buffer.getByteBuffer().putInt(index, NumUtil.safeToInt(value));
        } else {
            throw shouldNotReachHere("Unsupported reference size: " + referenceSize());
        }
    }

    private void writeObject(ObjectInfo info, RelocatableBuffer buffer) {
        /*
         * Write a reference from the object to its hub. This lives at layout.getHubOffset() from
         * the object base.
         */
        ObjectLayout objectLayout = heap.getObjectLayout();
        final int indexInBuffer = info.getIndexInBuffer(objectLayout.getHubOffset());
        assert objectLayout.isAligned(indexInBuffer);

        writeObjectHeader(buffer, indexInBuffer, info);

        ByteBuffer bufferBytes = buffer.getByteBuffer();
        HostedClass clazz = info.getClazz();
        if (clazz.isInstanceClass()) {
            JavaConstant con = info.getConstant();

            HybridLayout<?> hybridLayout = heap.getHybridLayout(clazz);
            HostedField hybridArrayField = null;
            HostedField hybridTypeIDSlotsField = null;
            int maxBitIndex = -1;
            int maxTypeIDSlotIndex = -1;
            Object hybridArray = null;
            if (hybridLayout != null) {
                hybridArrayField = hybridLayout.getArrayField();
                hybridArray = readObjectField(hybridArrayField, con);

                hybridTypeIDSlotsField = hybridLayout.getTypeIDSlotsField();
                if (hybridTypeIDSlotsField != null) {
                    short[] typeIDSlots = (short[]) readObjectField(hybridTypeIDSlotsField, con);
                    if (typeIDSlots != null) {
                        int length = typeIDSlots.length;
                        for (int i = 0; i < length; i++) {
                            final int index = info.getIndexInBuffer(HybridLayout.getTypeIDSlotsFieldOffset(objectLayout)) + (i * 2);
                            if (index + 1 > maxTypeIDSlotIndex) {
                                maxTypeIDSlotIndex = index + 1; // Takes two bytes...
                            }
                            short value = typeIDSlots[i];
                            bufferBytes.putShort(index, value);
                        }
                    }
                }
            }

            /*
             * Write the regular instance fields.
             */
            for (HostedField field : clazz.getInstanceFields(true)) {
                if (!field.equals(hybridArrayField) &&
                                !field.equals(hybridTypeIDSlotsField) &&
                                field.isRead()) {
                    assert field.getLocation() >= 0;
                    assert info.getIndexInBuffer(field.getLocation()) > maxBitIndex;
                    assert info.getIndexInBuffer(field.getLocation()) > maxTypeIDSlotIndex;
                    writeField(buffer, info, field, con, info);
                }
            }
            bufferBytes.putInt(info.getIndexInBuffer(objectLayout.getIdentityHashCodeOffset()), info.getIdentityHashCode());
            if (hybridArray != null) {
                /*
                 * Write the hybrid array length and the array elements.
                 */
                int length = Array.getLength(hybridArray);
                bufferBytes.putInt(info.getIndexInBuffer(objectLayout.getArrayLengthOffset()), length);
                for (int i = 0; i < length; i++) {
                    final int elementIndex = info.getIndexInBuffer(hybridLayout.getArrayElementOffset(i));
                    final JavaKind elementStorageKind = hybridLayout.getArrayElementStorageKind();
                    final Object array = Array.get(hybridArray, i);
                    writeConstant(buffer, elementIndex, elementStorageKind, array, info);
                }
            }

        } else if (clazz.isArray()) {

            JavaKind kind = clazz.getComponentType().getStorageKind();
            JavaConstant constant = info.getConstant();
            if (constant instanceof ImageHeapConstant) {
                if (!clazz.getComponentType().isPrimitive()) {
                    AnalysisConstantReflectionProvider constantReflection = heap.getUniverse().getConstantReflectionProvider();
                    int length = constantReflection.readArrayLength(constant);
                    bufferBytes.putInt(info.getIndexInBuffer(objectLayout.getArrayLengthOffset()), length);
                    bufferBytes.putInt(info.getIndexInBuffer(objectLayout.getIdentityHashCodeOffset()), info.getIdentityHashCode());
                    constantReflection.forEachArrayElement(constant, (element, index) -> {
                        final int elementIndex = info.getIndexInBuffer(objectLayout.getArrayElementOffset(kind, index));
                        writeConstant(buffer, elementIndex, kind, element, info);
                    });
                } else {
                    throw VMError.shouldNotReachHere("Heap writing for primitive type ImageHeapArray not yet implemented.");
                }
            } else {
                Object array = info.getObject();
                int length = Array.getLength(array);
                bufferBytes.putInt(info.getIndexInBuffer(objectLayout.getArrayLengthOffset()), length);
                bufferBytes.putInt(info.getIndexInBuffer(objectLayout.getIdentityHashCodeOffset()), info.getIdentityHashCode());
                if (array instanceof Object[]) {
                    Object[] oarray = (Object[]) array;
                    assert oarray.length == length;
                    for (int i = 0; i < length; i++) {
                        final int elementIndex = info.getIndexInBuffer(objectLayout.getArrayElementOffset(kind, i));
                        Object element = maybeReplace(oarray[i], info);
                        assert (oarray[i] instanceof RelocatedPointer) == (element instanceof RelocatedPointer);
                        writeConstant(buffer, elementIndex, kind, element, info);
                    }
                } else {
                    int elementIndex = info.getIndexInBuffer(objectLayout.getArrayElementOffset(kind, 0));
                    int elementTypeSize = Unsafe.getUnsafe().arrayIndexScale(array.getClass());
                    assert elementTypeSize == kind.getByteCount();
                    Unsafe.getUnsafe().copyMemory(array, Unsafe.getUnsafe().arrayBaseOffset(array.getClass()), buffer.getBackingArray(),
                                    Unsafe.ARRAY_BYTE_BASE_OFFSET + elementIndex, length * elementTypeSize);
                }
            }
        } else {
            throw shouldNotReachHere();
        }
    }

    private Object maybeReplace(Object object, Object reason) {
        try {
            return heap.getAnalysisUniverse().replaceObject(object);
        } catch (AnalysisError.TypeNotFoundError ex) {
            throw NativeImageHeap.reportIllegalType(ex.getType(), reason);
        }
    }
}
