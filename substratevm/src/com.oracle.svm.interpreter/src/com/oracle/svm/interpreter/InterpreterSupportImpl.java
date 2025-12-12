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

import static com.oracle.svm.core.code.FrameSourceInfo.LINENUMBER_NATIVE;

import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.graal.code.PreparedArgumentType;
import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class InterpreterSupportImpl extends InterpreterSupport {
    private static final int MAX_SYMBOL_LOG_LENGTH = 255;

    private final int bciSlot;
    private final int interpretedMethodSlot;
    private final int interpretedFrameSlot;
    private final int intrinsicMethodSlot;
    private final int intrinsicFrameSlot;

    InterpreterSupportImpl(int bciSlot, int interpretedMethodSlot, int interpretedFrameSlot, int intrinsicMethodSlot, int intrinsicFrameSlot) {
        this.bciSlot = bciSlot;
        this.interpretedMethodSlot = interpretedMethodSlot;
        this.interpretedFrameSlot = interpretedFrameSlot;
        this.intrinsicMethodSlot = intrinsicMethodSlot;
        this.intrinsicFrameSlot = intrinsicFrameSlot;
    }

    @Override
    public PreparedSignature prepareSignature(ResolvedJavaMethod method) {
        InterpreterResolvedJavaMethod interpreterMethod = (InterpreterResolvedJavaMethod) method;

        InterpreterUnresolvedSignature signature = interpreterMethod.getSignature();
        boolean hasReceiver = interpreterMethod.hasReceiver();
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);
        int count = signature.getParameterCount(false);
        PreparedArgumentType[] argumentTypes = new PreparedArgumentType[count + (hasReceiver ? 1 : 0)];

        // The calling convention is always used with a caller perspective, i.e. sp is unmodified.
        SubstrateCallingConventionType callingConventionType = SubstrateCallingConventionKind.Java.toType(true);
        ResolvedJavaType accessingClass = interpreterMethod.getDeclaringClass();
        JavaType thisType = interpreterMethod.hasReceiver() ? accessingClass : null;
        JavaType returnType = signature.getReturnType(accessingClass);
        CallingConvention callingConvention = stubSection.registerConfig.getCallingConvention(callingConventionType, returnType, signature.toParameterTypes(thisType), stubSection.valueKindFactory);

        if (hasReceiver) {
            argumentTypes[0] = new PreparedArgumentType(JavaKind.Object, 0, true);
        }
        for (int i = 0; i < count; i++) {
            int index = i + (hasReceiver ? 1 : 0);
            AllocatableValue allocatableValue = callingConvention.getArgument(index);
            JavaKind argKind = signature.getParameterKind(i);
            int value = 0;
            if (allocatableValue instanceof StackSlot stackSlot) {
                // Both, in the enter- and leavestub we want the "outgoing semantics".
                value = stackSlot.getOffset(0);
            }
            boolean isRegister = !(allocatableValue instanceof StackSlot);
            argumentTypes[index] = new PreparedArgumentType(argKind, value, isRegister);
        }
        return new PreparedSignature(signature.getReturnKind(), argumentTypes, callingConvention.getStackSize());
    }

    @Override
    public boolean isInterpreterRoot(FrameInfoQueryResult frameInfo) {
        return isInterpreterBytecodeRoot(frameInfo) || isInterpreterIntrinsicRoot(frameInfo);
    }

    private static boolean isInterpreterBytecodeRoot(FrameInfoQueryResult frameInfo) {
        return Interpreter.Root.class.equals(frameInfo.getSourceClass());
    }

    private static boolean isInterpreterIntrinsicRoot(FrameInfoQueryResult frameInfo) {
        return Interpreter.IntrinsicRoot.class.equals(frameInfo.getSourceClass());
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

    private InterpreterResolvedJavaMethod readIntrinsicMethod(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo valueInfo = frameInfo.getValueInfos()[intrinsicMethodSlot];
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

    private InterpreterFrame readIntrinsicFrame(FrameInfoQueryResult frameInfo, Pointer sp) {
        FrameInfoQueryResult.ValueInfo valueInfo = frameInfo.getValueInfos()[intrinsicFrameSlot];
        return readObject(sp, Word.signed(valueInfo.getData()), valueInfo.isCompressedReference());
    }

    @Override
    public FrameSourceInfo getInterpretedMethodFrameInfo(FrameInfoQueryResult frameInfo, Pointer sp) {
        if (isInterpreterBytecodeRoot(frameInfo)) {
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
        if (isInterpreterIntrinsicRoot(frameInfo)) {
            InterpreterResolvedJavaMethod intrinsicMethod = readIntrinsicMethod(frameInfo, sp);
            InterpreterFrame interpreterFrame = readIntrinsicFrame(frameInfo, sp);
            Class<?> intrinsicClass = intrinsicMethod.getDeclaringClass().getJavaClass();
            String sourceMethodName = intrinsicMethod.getName();
            return new InterpreterFrameSourceInfo(intrinsicClass, sourceMethodName, LINENUMBER_NATIVE, -1, intrinsicMethod, interpreterFrame);
        }
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Used for crash log")
    public void logInterpreterFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp) {
        if (isInterpreterIntrinsicRoot(frameInfo)) {
            logInterpreterIntrinsicFrame(log, frameInfo, sp);
        } else if (isInterpreterBytecodeRoot(frameInfo)) {
            logInterpreterBytecodeFrame(log, frameInfo, sp);
        } else {
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    private void logInterpreterBytecodeFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp) {
        if (!frameInfo.hasLocalValueInfo()) {
            log.string("  missing local value info (bytecode)");
            return;
        }
        InterpreterResolvedJavaMethod interpretedMethod = readInterpretedMethod(frameInfo, sp);
        if (interpretedMethod == null) {
            log.string("  no interpreter method (bytecode)");
            return;
        }
        int bci = readBCI(frameInfo, sp);
        logInterpreterMethod(log, interpretedMethod, bci);
    }

    private void logInterpreterIntrinsicFrame(Log log, FrameInfoQueryResult frameInfo, Pointer sp) {
        if (!frameInfo.hasLocalValueInfo()) {
            log.string("  missing local value info (intrinsic)");
            return;
        }
        InterpreterResolvedJavaMethod intrinsicMethod = readIntrinsicMethod(frameInfo, sp);
        if (intrinsicMethod == null) {
            log.string("  no interpreter method (intrinsic)");
            return;
        }
        logInterpreterMethod(log, intrinsicMethod, -1);
    }

    private static void logInterpreterMethod(Log log, InterpreterResolvedJavaMethod interpretedMethod, int bci) {
        String sourceHolderName = interpretedMethod.getDeclaringClass().getJavaClass().getName();
        Symbol<Name> sourceMethodName = interpretedMethod.getSymbolicName();
        LineNumberTable lineNumberTable = interpretedMethod.getLineNumberTable();
        int sourceLineNumber = -1; // unknown
        if (lineNumberTable != null && bci >= 0) {
            sourceLineNumber = lineNumberTable.getLineNumber(bci);
        }
        log.spaces(2);
        log.string(sourceHolderName);
        log.character('.');
        logSymbol(log, sourceMethodName);
        String sourceFileName = interpretedMethod.getDeclaringClass().getSourceFileName();
        if (sourceFileName == null && sourceLineNumber >= 0) {
            sourceFileName = "Unknown Source";
        }
        if (sourceFileName != null) {
            log.character('(');
            log.string(sourceFileName);
            if (sourceLineNumber >= 0) {
                log.string(":");
                log.signed(sourceLineNumber);
            }
            log.character(')');
        }
        if (bci >= 0) {
            log.spaces(1);
            log.string("@bci ");
            log.signed(bci);
        }
    }

    private static void logSymbol(Log log, ByteSequence byteSequence) {
        int length = Math.min(byteSequence.length(), MAX_SYMBOL_LOG_LENGTH);
        for (int i = 0; i < length; i++) {
            int b = byteSequence.unsignedByteAt(i);
            if (0x20 <= b && b <= 0x7e) {
                // only log printable ascii
                log.character((char) b);
            } else {
                log.character('?');
            }
        }
        if (byteSequence.length() > MAX_SYMBOL_LOG_LENGTH) {
            log.string("...");
        }
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
