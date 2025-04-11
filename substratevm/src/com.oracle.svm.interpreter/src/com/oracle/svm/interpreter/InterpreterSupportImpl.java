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

package com.oracle.svm.interpreter;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class InterpreterSupportImpl extends InterpreterSupport {
    private final int bciSlot;
    private final int interpretedMethodSlot;
    private final int interpretedFrameSlot;

    InterpreterSupportImpl(int bciSlot, int interpretedMethodSlot, int interpretedFrameSlot) {
        this.bciSlot = bciSlot;
        this.interpretedMethodSlot = interpretedMethodSlot;
        this.interpretedFrameSlot = interpretedFrameSlot;
    }

    @Override
    public boolean isInterpreterRoot(Class<?> clazz) {
        return Interpreter.Root.class.equals(clazz);
    }

    private static int readInt(Pointer addr, SignedWord offset) {
        return addr.readInt(offset);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readObject(Pointer addr, SignedWord offset, boolean compressed) {
        Word p = ((Word) addr).add(offset);
        Object obj = ReferenceAccess.singleton().readObjectAt(p, compressed);
        return (T) obj;
    }

    private InterpreterResolvedJavaMethod readInterpretedMethod(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo valueInfo = frameInfo.getValueInfos()[interpretedMethodSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    private int readBCI(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo valueInfo = frameInfo.getValueInfos()[bciSlot];
        return readInt(sp, Word.signed(valueInfo.getData()));
    }

    private InterpreterFrame readInterpreterFrame(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo valueInfo = frameInfo.getValueInfos()[interpretedFrameSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    @Override
    public FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp) {
        if (!isInterpreterRoot(frameInfo.getSourceClass())) {
            throw VMError.shouldNotReachHereAtRuntime();
        }
        InterpreterResolvedJavaMethod interpretedMethod = readInterpretedMethod(frameInfo, sp);
        int bci = readBCI(frameInfo, sp);
        InterpreterFrame interpreterFrame = readInterpreterFrame(frameInfo, sp);
        Class<?> interpretedClass = interpretedMethod.getDeclaringClass().getJavaClass();
        String sourceMethodName = interpretedMethod.getName();
        LineNumberTable lineNumberTable = interpretedMethod.getLineNumberTable();

        int sourceLineNumber = -1; // unknown
        if (lineNumberTable != null) {
            sourceLineNumber = lineNumberTable.getLineNumber(bci);
        }
        return new InterpreterFrameSourceInfo(interpretedClass, sourceMethodName, sourceLineNumber, bci, interpretedMethod, interpreterFrame);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void buildMethodIdMapping(ResolvedJavaMethod[] encodedMethods) {
        if (InterpreterOptions.DebuggerWithInterpreter.getValue()) {
            assert ImageSingletons.contains(DebuggerSupport.class);
            ImageSingletons.lookup(DebuggerSupport.class).buildMethodIdMapping(encodedMethods);
        }
    }
}
