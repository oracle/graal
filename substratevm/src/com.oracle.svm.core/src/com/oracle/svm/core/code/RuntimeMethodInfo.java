/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.c.PinnedArray;
import com.oracle.svm.core.c.PinnedObjectArray;

import jdk.vm.ci.code.InstalledCode;

@RawStructure
public interface RuntimeMethodInfo extends PointerBase {
    /**
     * The object "fields" of this class, managed as an array for simplicity.
     *
     * [0] String: The {@linkplain InstalledCode#getName() name of the InstalledCode}. Stored here
     * so it remains available even after the code is no longer available. Note that the String is
     * not pinned, so this field must not be accessed during garbage collection.
     *
     * [1] WeakReference<SubstrateInstalledCode>: The handle to the compiled code for the outside
     * world. We only have a weak reference to it, to avoid keeping code alive. Note that the both
     * the InstalledCode and the weak reference are not pinned, so this field must not be accessed
     * during garbage collection.
     *
     * [2] InstalledCodeObserverHandle[]: observers for installation and removal of this code.
     */
    @RawField
    void setObjectFields(PinnedObjectArray<?> fields);

    /** @see #setObjectFields */
    @RawField
    PinnedObjectArray<?> getObjectFields();

    @RawField
    int getTier();

    @RawField
    void setTier(int tier);

    @RawField
    CodePointer getCodeStart();

    @RawField
    UnsignedWord getCodeSize();

    @RawField
    PinnedArray<Byte> getReferenceMapEncoding();

    @RawField
    void setCodeStart(CodePointer codeStart);

    @RawField
    void setCodeSize(UnsignedWord codeSize);

    @RawField
    PinnedArray<Byte> getCodeInfoIndex();

    @RawField
    void setCodeInfoIndex(PinnedArray<Byte> codeInfoIndex);

    @RawField
    PinnedArray<Byte> getCodeInfoEncodings();

    @RawField
    void setCodeInfoEncodings(PinnedArray<Byte> codeInfoEncodings);

    @RawField
    void setReferenceMapEncoding(PinnedArray<Byte> referenceMapEncoding);

    @RawField
    PinnedArray<Byte> getFrameInfoEncodings();

    @RawField
    void setFrameInfoEncodings(PinnedArray<Byte> frameInfoEncodings);

    @RawField
    PinnedObjectArray<Object> getFrameInfoObjectConstants();

    @RawField
    void setFrameInfoObjectConstants(PinnedObjectArray<Object> frameInfoObjectConstants);

    @RawField
    PinnedObjectArray<Class<?>> getFrameInfoSourceClasses();

    @RawField
    void setFrameInfoSourceClasses(PinnedObjectArray<Class<?>> frameInfoSourceClasses);

    @RawField
    PinnedObjectArray<String> getFrameInfoSourceMethodNames();

    @RawField
    void setFrameInfoSourceMethodNames(PinnedObjectArray<String> frameInfoSourceMethodNames);

    @RawField
    PinnedObjectArray<String> getFrameInfoNames();

    @RawField
    void setFrameInfoNames(PinnedObjectArray<String> frameInfoNames);

    @RawField
    boolean getCodeConstantsLive();

    @RawField
    void setCodeConstantsLive(boolean codeConstantsLive);

    @RawField
    PinnedArray<Byte> getObjectsReferenceMapEncoding();

    @RawField
    void setObjectsReferenceMapEncoding(PinnedArray<Byte> objectsReferenceMapEncoding);

    @RawField
    long getObjectsReferenceMapIndex();

    @RawField
    void setObjectsReferenceMapIndex(long objectsReferenceMapIndex);

    @RawField
    PinnedArray<Integer> getDeoptimizationStartOffsets();

    @RawField
    void setDeoptimizationStartOffsets(PinnedArray<Integer> deoptimizationStartOffsets);

    @RawField
    PinnedArray<Byte> getDeoptimizationEncodings();

    @RawField
    void setDeoptimizationEncodings(PinnedArray<Byte> deoptimizationEncodings);

    @RawField
    PinnedObjectArray<Object> getDeoptimizationObjectConstants();

    @RawField
    void setDeoptimizationObjectConstants(PinnedObjectArray<Object> deoptimizationObjectConstants);
}
