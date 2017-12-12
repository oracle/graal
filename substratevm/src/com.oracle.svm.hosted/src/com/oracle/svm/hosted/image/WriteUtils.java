/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.PrimitiveWriteUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.MethodPointer;

public class WriteUtils extends PrimitiveWriteUtils {

    /*
     * Write methods that unwrap the RelocatableBuffer. These methods do not need to set up
     * relocations.
     */

    public static void writePrimitive(RelocatableBuffer buffer, int index, JavaConstant con) {
        PrimitiveWriteUtils.writePrimitive(buffer.getBuffer(), index, con);
    }

    public static void writePointer(RelocatableBuffer buffer, int index, long value, int size) {
        PrimitiveWriteUtils.writePointer(buffer.getBuffer(), index, value, size);
    }

    /*
     * Write methods on RelocatableBuffer. These methods may need to set up relocations.
     */

    public static void writeField(RelocatableBuffer buffer, int index, HostedField field, JavaConstant receiver, NativeImageHeap heap, ObjectInfo info) {
        JavaConstant value = field.readValue(receiver);
        if (value.getJavaKind() == JavaKind.Object && SubstrateObjectConstant.asObject(value) instanceof RelocatedPointer) {
            // A RelocatedPointer needs relocation information.
            addNonDataRelocation(buffer, index, (RelocatedPointer) SubstrateObjectConstant.asObject(value), heap);
        } else {
            // Other Constants get written without relocation information.
            write(buffer, index, value, heap, field.getName(), info != null ? info : field);
        }
    }

    public static void write(RelocatableBuffer buffer, int index, JavaConstant con, NativeImageHeap heap, String label, Object reason) {
        if (con.getJavaKind() == JavaKind.Object) {
            writeReference(buffer, index, SubstrateObjectConstant.asObject(con), heap, label, reason);
        } else {
            writePrimitive(buffer, index, con);
        }
    }

    public static void writeConstant(RelocatableBuffer buffer, int index, JavaKind kind, Object value, NativeImageHeap heap, String label, ObjectInfo info) {
        if (value instanceof RelocatedPointer) {
            final RelocatedPointer pointer = (RelocatedPointer) value;
            writeRelocatedPointer(buffer, index, pointer, heap, label);
        } else {
            JavaConstant con;
            if (value instanceof WordBase) {
                con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), ((WordBase) value).rawValue());
            } else if (value == null && kind == FrameAccess.getWordKind()) {
                con = JavaConstant.forIntegerKind(FrameAccess.getWordKind(), 0);
            } else {
                assert kind == JavaKind.Object || value != null : "primitive value must not be null";
                con = SubstrateObjectConstant.forBoxedValue(kind, value);
            }
            write(buffer, index, con, heap, label, info);
        }
    }

    public static void writeRelocatedPointer(RelocatableBuffer buffer, int index, RelocatedPointer pointer, NativeImageHeap heap, String label) {
        // A RelocatedPointer needs relocation data.
        addNonDataRelocation(buffer, index, pointer, heap);
        // Add a link in the heap graph.
        addHeapPrinterLink(heap, pointer, label);
    }

    public static void writeReference(RelocatableBuffer buffer, int index, Object target, NativeImageHeap heap, String label, Object reason) {
        assert !(target instanceof WordBase) : "word values are not references";
        final int objectSize = heap.getLayout().sizeInBytes(JavaKind.Object);
        assert heap.getLayout().isAligned(index) : "index " + index + " must be aligned.";
        if (target != null) {
            verifyTargetDidNotChange(target, reason, heap.objects.get(target));
            buffer.addDirectRelocationWithoutAddend(index, objectSize, target);
            // Add a link in the heap graph.
            addHeapPrinterLink(heap, target, label);
        }
    }

    private static void verifyTargetDidNotChange(Object target, Object reason, Object targetInfo) {
        if (targetInfo == null) {
            throw UserError.abort("Static field or an object referenced from a static field changed during native image generation?\n" +
                            "  object:" + target + "\n" +
                            "  reachable through:\n" +
                            fillReasonStack(new StringBuilder(), reason));
        }
    }

    private static StringBuilder fillReasonStack(StringBuilder msg, Object reason) {
        if (reason instanceof ObjectInfo) {
            ObjectInfo info = (ObjectInfo) reason;
            msg.append("    object: ").append(info.getObject()).append("\n");
            return fillReasonStack(msg, info.reason);
        } else {
            return msg.append("    root: ").append(reason).append("\n");
        }
    }

    public static void addHeapPrinterLink(NativeImageHeap heap, Object target, String label) {
        assert !(target instanceof NativeImageHeap.ObjectInfo) : "Probably you passed a targetInfo where you wanted a target.";
        if ((heap.getHeapPrinter() != null) && (label != null)) {
            NativeImageHeap.ObjectInfo targetInfo = heap.objects.get(target);
            if (targetInfo != null) {
                heap.getHeapPrinter().addLink(targetInfo, label);
            }
        }
    }

    // This is quite like writeReference, but it writes a DynamicHub.
    // It gets an ObjectHeader implementation argument that it uses to note
    // that this object was allocated in the native image heap.
    public static void writeDynamicHub(RelocatableBuffer buffer, int index, DynamicHub target, NativeImageHeap heap, ObjectHeader ohi) {
        assert target != null : "Null DynamicHub found during native image generation.";
        // DynamicHubs are the size of Object references.
        final int objectSize = heap.getLayout().sizeInBytes(JavaKind.Object);
        assert heap.getLayout().isAligned(index) : "index " + index + " must be aligned.";
        final NativeImageHeap.ObjectInfo targetInfo = heap.objects.get(target);
        assert targetInfo != null : "Unknown object " + target.toString() + " found. Static field or an object referenced from a static field changed during native image generation?";
        // Note that this object is allocated on the native image heap.
        final long bootImageHeapBits = ohi.setBootImageOnLong(0L);
        // The address of the DynamicHub target will have to be added by the link editor.
        buffer.addDirectRelocationWithAddend(index, objectSize, bootImageHeapBits, target);
    }

    /**
     * Adds a relocation for a code pointer or other non-data pointers.
     */
    public static void addNonDataRelocation(RelocatableBuffer buffer, int index, RelocatedPointer pointer, NativeImageHeap heap) {
        final int objectSize = heap.getLayout().sizeInBytes(JavaKind.Object);
        assert heap.getLayout().isAligned(index) : "index " + index + " must be aligned.";
        assert pointer instanceof CFunctionPointer : "unknown relocated pointer " + pointer;
        assert pointer instanceof MethodPointer : "cannot create relocation for unknown FunctionPointer " + pointer;
        final HostedMethod method = ((MethodPointer) pointer).getMethod();
        if (!method.isCodeAddressOffsetValid()) {
            // A method which is inserted in vtables but is not compiled because it is inlined,
            // does not have a code address offset. It needs no relocation.
            return;
        }
        // Other methods need relocation.
        buffer.addDirectRelocationWithoutAddend(index, objectSize, pointer);
        return;
    }
}
