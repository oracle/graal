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

import org.graalvm.compiler.core.common.CompressEncoding;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.RelocatedPointer;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.PrimitiveWriteUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.image.NativeImageHeap.ObjectInfo;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.MethodPointer;

public class WriteUtils extends PrimitiveWriteUtils {

    private static boolean useHeapBase() {
        return SubstrateOptions.UseHeapBaseRegister.getValue() && ImageSingletons.lookup(CompressEncoding.class).hasBase();
    }

    private static int objectSize(NativeImageHeap heap) {
        return heap.getLayout().sizeInBytes(JavaKind.Object, false);
    }

    private static void mustBeAligned(NativeImageHeap heap, int index) {
        assert heap.getLayout().isAligned(index) : "index " + index + " must be aligned.";
    }

    private static long targetHeapOffset(ObjectInfo target) {
        return target.getOffsetInSection();
    }

    /*
     * Write methods that unwrap the RelocatableBuffer. These methods do not need to set up
     * relocations.
     */

    private static void writePrimitive(RelocatableBuffer buffer, int index, JavaConstant con) {
        PrimitiveWriteUtils.writePrimitive(buffer.getBuffer(), index, con);
    }

    private static void writePointer(RelocatableBuffer buffer, int index, long value, int size) {
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
            write(buffer, index, value, heap, info != null ? info : field);
        }
    }

    public static void write(RelocatableBuffer buffer, int index, JavaConstant con, NativeImageHeap heap, Object reason) {
        if (con.getJavaKind() == JavaKind.Object) {
            writeReference(buffer, index, SubstrateObjectConstant.asObject(con), heap, reason);
        } else {
            writePrimitive(buffer, index, con);
        }
    }

    public static void writeConstant(RelocatableBuffer buffer, int index, JavaKind kind, Object value, NativeImageHeap heap, ObjectInfo info) {
        if (value instanceof RelocatedPointer) {
            addNonDataRelocation(buffer, index, (RelocatedPointer) value, heap);
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
        write(buffer, index, con, heap, info);
    }

    public static void writeReference(RelocatableBuffer buffer, int index, Object target, NativeImageHeap heap, Object reason) {
        assert !(target instanceof WordBase) : "word values are not references";
        mustBeAligned(heap, index);
        if (target != null) {
            ObjectInfo targetInfo = heap.objects.get(target);
            verifyTargetDidNotChange(target, reason, targetInfo);
            int size = objectSize(heap);
            if (useHeapBase()) {
                CompressEncoding compressEncoding = ImageSingletons.lookup(CompressEncoding.class);
                int shift = compressEncoding.getShift();
                writePointer(buffer, index, targetHeapOffset(targetInfo) >>> shift, size);
            } else {
                buffer.addDirectRelocationWithoutAddend(index, size, target);
            }
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
        }
        return msg.append("    root: ").append(reason).append("\n");
    }

    // This is quite like writeReference, but it writes a DynamicHub.
    public static void writeDynamicHub(RelocatableBuffer buffer, int index, DynamicHub target, NativeImageHeap heap, long objectHeaderBits) {
        assert target != null : "Null DynamicHub found during native image generation.";
        mustBeAligned(heap, index);

        ObjectInfo targetInfo = heap.objects.get(target);
        assert targetInfo != null : "Unknown object " + target.toString() + " found. Static field or an object referenced from a static field changed during native image generation?";

        int objectHeaderSize = objectSize(heap);
        // Note that this object is allocated on the native image heap.
        if (useHeapBase()) {
            long targetOffset = targetHeapOffset(targetInfo);
            writePointer(buffer, index, targetOffset | objectHeaderBits, objectHeaderSize);
        } else {
            // The address of the DynamicHub target will have to be added by the link editor.
            // DynamicHubs are the size of Object references.
            buffer.addDirectRelocationWithAddend(index, objectHeaderSize, objectHeaderBits, target);
        }
    }

    /**
     * Adds a relocation for a code pointer or other non-data pointers.
     */
    private static void addNonDataRelocation(RelocatableBuffer buffer, int index, RelocatedPointer pointer, NativeImageHeap heap) {
        mustBeAligned(heap, index);
        assert pointer instanceof CFunctionPointer : "unknown relocated pointer " + pointer;
        assert pointer instanceof MethodPointer : "cannot create relocation for unknown FunctionPointer " + pointer;

        HostedMethod method = ((MethodPointer) pointer).getMethod();
        if (method.isCodeAddressOffsetValid()) {
            // Only compiled methods inserted in vtables require relocation.
            buffer.addDirectRelocationWithoutAddend(index, objectSize(heap), pointer);
        }
    }
}
