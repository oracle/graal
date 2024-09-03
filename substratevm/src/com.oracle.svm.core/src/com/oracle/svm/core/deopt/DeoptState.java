/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import jdk.internal.misc.Unsafe;
import jdk.graal.compiler.core.common.util.TypeConversion;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.lang.reflect.Array;

import static com.oracle.svm.core.deopt.Deoptimizer.fatalDeoptimizationError;

public class DeoptState {

    /**
     * The current position when walking over the source stack frames.
     */
    final Pointer sourceSp;

    final IsolateThread targetThread;

    /**
     * All objects, which are materialized during deoptimization.
     */
    Object[] materializedObjects;

    public DeoptState(Pointer sourceSp, IsolateThread targetThread) {
        this.sourceSp = sourceSp;
        this.targetThread = targetThread;
        this.materializedObjects = null;
    }

    /**
     * Reads the value of a local variable in the given frame. If the local variable is a virtual
     * object, the object (and all other objects reachable from it) are materialized.
     *
     * @param idx the number of the local variable.
     * @param sourceFrame the frame to access, which must be an inlined frame of the physical frame
     *            that this deoptimizer has been created for.
     */
    public JavaConstant readLocalVariable(int idx, FrameInfoQueryResult sourceFrame) {
        if (!(idx >= 0 && idx < sourceFrame.getNumLocals())) {
            throw fatalDeoptimizationError(String.format("Invalid idx: %s", idx), sourceFrame);
        }
        if (idx < sourceFrame.getValueInfos().length) {
            return readValue(sourceFrame.getValueInfos()[idx], sourceFrame);
        } else {
            return JavaConstant.forIllegal();
        }
    }

    protected JavaConstant readValue(FrameInfoQueryResult.ValueInfo valueInfo, FrameInfoQueryResult sourceFrame) {
        switch (valueInfo.getType()) {
            case Constant:
            case DefaultConstant:
                return valueInfo.getValue();
            case StackSlot:
            case Register:
                return readConstant(sourceSp, WordFactory.signed(valueInfo.getData()), valueInfo.getKind(), valueInfo.isCompressedReference(), sourceFrame);
            case ReservedRegister:
                if (ReservedRegisters.singleton().getThreadRegister() != null && ReservedRegisters.singleton().getThreadRegister().number == valueInfo.getData()) {
                    return JavaConstant.forIntegerKind(ConfigurationValues.getWordKind(), targetThread.rawValue());
                } else if (ReservedRegisters.singleton().getHeapBaseRegister() != null && ReservedRegisters.singleton().getHeapBaseRegister().number == valueInfo.getData()) {
                    return JavaConstant.forIntegerKind(ConfigurationValues.getWordKind(), CurrentIsolate.getIsolate().rawValue());
                } else {
                    throw fatalDeoptimizationError("Unexpected reserved register: " + valueInfo.getData(), sourceFrame);
                }

            case VirtualObject:
                Object obj = materializeObject(TypeConversion.asS4(valueInfo.getData()), sourceFrame);
                return SubstrateObjectConstant.forObject(obj, valueInfo.isCompressedReference());
            case Illegal:
                return JavaConstant.forIllegal();
            default:
                throw fatalDeoptimizationError("Unexpected type: " + valueInfo.getType(), sourceFrame);
        }
    }

    /**
     * Materializes a virtual object.
     *
     * @param virtualObjectId the id of the virtual object to materialize
     * @return the materialized object
     */
    private Object materializeObject(int virtualObjectId, FrameInfoQueryResult sourceFrame) {
        if (materializedObjects == null) {
            materializedObjects = new Object[sourceFrame.getVirtualObjects().length];
        }
        if (materializedObjects.length != sourceFrame.getVirtualObjects().length) {
            throw fatalDeoptimizationError(String.format("MaterializedObjects length (%s) does not match sourceFrame", materializedObjects.length), sourceFrame);
        }

        Object obj = materializedObjects[virtualObjectId];
        if (obj != null) {
            return obj;
        }
        DeoptimizationCounters.counters().virtualObjectsCount.inc();

        FrameInfoQueryResult.ValueInfo[] encodings = sourceFrame.getVirtualObjects()[virtualObjectId];
        DynamicHub hub = (DynamicHub) SubstrateObjectConstant.asObject(readValue(encodings[0], sourceFrame));
        ObjectLayout objectLayout = ConfigurationValues.getObjectLayout();

        int curIdx;
        UnsignedWord curOffset;
        int layoutEncoding = hub.getLayoutEncoding();
        if (LayoutEncoding.isArray(layoutEncoding)) {
            /* For arrays, the second encoded value is the array length. */
            int length = readValue(encodings[1], sourceFrame).asInt();
            obj = Array.newInstance(DynamicHub.toClass(hub.getComponentHub()), length);
            curOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
            curIdx = 2;
        } else {
            if (!LayoutEncoding.isPureInstance(layoutEncoding)) {
                throw fatalDeoptimizationError("Non-pure instance layout encoding: " + layoutEncoding, sourceFrame);
            }
            try {
                obj = Unsafe.getUnsafe().allocateInstance(DynamicHub.toClass(hub));
            } catch (InstantiationException ex) {
                throw fatalDeoptimizationError("Instantiation exception: " + ex, sourceFrame);
            }
            curOffset = WordFactory.unsigned(objectLayout.getFirstFieldOffset());
            curIdx = 1;
        }

        materializedObjects[virtualObjectId] = obj;
        if (Deoptimizer.testGCinDeoptimizer) {
            Heap.getHeap().getGC().collect(GCCause.TestGCInDeoptimizer);
        }

        while (curIdx < encodings.length) {
            FrameInfoQueryResult.ValueInfo value = encodings[curIdx];
            JavaKind kind = value.getKind();
            JavaConstant con = readValue(value, sourceFrame);
            Deoptimizer.writeValueInMaterializedObj(obj, curOffset, con, sourceFrame);
            curOffset = curOffset.add(objectLayout.sizeInBytes(kind));
            curIdx++;
        }

        return obj;
    }

    private static JavaConstant readConstant(Pointer addr, SignedWord offset, JavaKind kind, boolean compressed, FrameInfoQueryResult frameInfo) {
        switch (kind) {
            case Boolean:
                return JavaConstant.forBoolean(addr.readByte(offset) != 0);
            case Byte:
                return JavaConstant.forByte(addr.readByte(offset));
            case Char:
                return JavaConstant.forChar(addr.readChar(offset));
            case Short:
                return JavaConstant.forShort(addr.readShort(offset));
            case Int:
                return JavaConstant.forInt(addr.readInt(offset));
            case Long:
                return JavaConstant.forLong(addr.readLong(offset));
            case Float:
                return JavaConstant.forFloat(addr.readFloat(offset));
            case Double:
                return JavaConstant.forDouble(addr.readDouble(offset));
            case Object:
                Word p = ((Word) addr).add(offset);
                Object obj = ReferenceAccess.singleton().readObjectAt(p, compressed);
                return SubstrateObjectConstant.forObject(obj, compressed);
            default:
                throw fatalDeoptimizationError("Unexpected constant kind: " + kind, frameInfo);
        }
    }

}
