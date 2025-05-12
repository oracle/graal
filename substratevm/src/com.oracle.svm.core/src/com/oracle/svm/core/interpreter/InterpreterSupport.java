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
package com.oracle.svm.core.interpreter;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.UnknownPrimitiveField;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/* Enables unoptimized execution of AOT compiled methods with an interpreter. The SVM
 * constraints apply, e.g. this itself does not enable class loading. */
public abstract class InterpreterSupport {
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private CFunctionPointer leaveStubPointer;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private int leaveStubLength;

    @Fold
    public static boolean isEnabled() {
        return ImageSingletons.contains(InterpreterSupport.class);
    }

    @Fold
    public static InterpreterSupport singleton() {
        return ImageSingletons.lookup(InterpreterSupport.class);
    }

    /*
     * Check if a given argument matches the inner class Interpreter.Root (holder of the interpreter
     * dispatch loop).
     */
    public abstract boolean isInterpreterRoot(Class<?> clazz);

    /**
     * Transforms an interpreter (root) frame into a frame of the interpreted method. The passed
     * frame must be an interpreter root e.g. {@code isInterpreterRoot(frameInfo.getSourceClass())}
     * otherwise a fatal exception is thrown.
     *
     * @param frameInfo interpreter root frame
     * @param sp stack pointer of the interpreter frame
     * @return a frame representing the interpreted method
     */
    public abstract FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp);

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setLeaveStubPointer(CFunctionPointer leaveStubPointer, int length) {
        assert singleton().leaveStubPointer == null : "multiple leave stub methods registered";
        singleton().leaveStubPointer = leaveStubPointer;
        singleton().leaveStubLength = length;
    }

    /**
     * Determines if a given address is within the program code of the interpreter leave stub.
     *
     * Stackslot layout of leaveInterpreterStub:
     * 
     * <pre>
     *     1. base address of outgoing stack args
     *     2. variable stack size
     *     3. GC reference map
     *     4. padding
     * </pre>
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isInInterpreterLeaveStub(CodePointer ip) {
        Pointer start = (Pointer) singleton().leaveStubPointer;
        Pointer end = start.add(singleton().leaveStubLength);
        return start.belowOrEqual((UnsignedWord) ip) && end.aboveOrEqual((UnsignedWord) ip);
    }

    /**
     * GC helper to visit stack slots of a leaveInterpreterStub frame. Frames of this stub require
     * special handling, as they do not have a fixed frame map. The reference map of each frame is
     * part of the frame itself.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called by GC walker", mayBeInlined = true)
    public static void walkInterpreterLeaveStubFrame(ObjectReferenceVisitor visitor, Pointer actualSP, Pointer sp) {
        int wordSize = FrameAccess.wordSize();
        long gcReferenceMap = actualSP.readLong(2 * wordSize);

        /* Visit object references passed on the stack */
        int referenceIndex = 0;
        while (gcReferenceMap != 0) {
            int trail0 = Long.numberOfTrailingZeros(gcReferenceMap);
            referenceIndex += trail0;
            gcReferenceMap >>= trail0;

            /* Constant offset due to "deopt slot" */
            int baseOffset = wordSize;
            Pointer objRef = sp.add(baseOffset + wordSize * referenceIndex);
            callVisitor(visitor, objRef, 1);

            referenceIndex++;
            gcReferenceMap >>= 1;
        }
    }

    @Uninterruptible(reason = "Bridge between uninterruptible and potentially interruptible code.", mayBeInlined = true, calleeMustBe = false)
    private static void callVisitor(ObjectReferenceVisitor visitor, Pointer firstObjRef, int count) {
        visitor.visitObjectReferences(firstObjRef, false, FrameAccess.uncompressedReferenceSize(), null, count);
    }

    /**
     * Array index is the unique identifier (aka. "sourceMethodId") for a compiled method, where
     * index 0 means "unknown". This is used to build a mapping from compiled methods to interpreter
     * methods.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public abstract void buildMethodIdMapping(ResolvedJavaMethod[] encodedMethods);
}
