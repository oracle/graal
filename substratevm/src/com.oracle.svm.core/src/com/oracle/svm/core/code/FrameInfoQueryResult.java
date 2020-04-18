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

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.meta.SharedMethod;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class FrameInfoQueryResult {

    public enum ValueType {
        /**
         * The {@link JavaKind#Illegal} value. The {@link ValueInfo#data} field is ignored.
         */
        Illegal(false),

        /**
         * A {@link StackSlot} value. The {@link ValueInfo#data} is the frame offset of the stack
         * slot.
         */
        StackSlot(true),

        /**
         * A {@link Register} value. The {@link ValueInfo#data} is the frame offset of the stack
         * slot in the callee where the register value was spilled to according to the
         * {@link CalleeSavedRegisters}.
         */
        Register(true),

        /**
         * A {@link Constant} value. The {@link ValueInfo#data} is the primitive data value of the
         * constant for {@link JavaKind#isPrimitive()} values, or the index into the object constant
         * array for {@link JavaKind#Object} values.
         */
        Constant(true),

        /**
         * A {@link Constant} that has the {@link Constant#isDefaultForKind() default} value. The
         * {@link ValueInfo#data} field is ignored.
         */
        DefaultConstant(false),

        /**
         * A {@link VirtualObject}. The The {@link ValueInfo#data} is the id of the virtual object,
         * i.e., the index into the {@link #virtualObjects}.
         */
        VirtualObject(true);

        protected final boolean hasData;

        ValueType(boolean hasData) {
            this.hasData = hasData;
        }
    }

    public static class ValueInfo {
        protected ValueType type;
        protected JavaKind kind;
        protected boolean isCompressedReference; // for JavaKind.Object
        protected boolean isEliminatedMonitor;
        protected long data;
        protected JavaConstant value;
        protected String name;
        /** Index of {@link #name} in {@link FrameInfoDecoder#decodeFrameInfo frameInfoNames}. */
        protected int nameIndex = -1;

        /**
         * Returns the type of the value, describing how to access the value.
         */
        public ValueType getType() {
            return type;
        }

        /**
         * Returns the kind of the value.
         */
        public JavaKind getKind() {
            return kind;
        }

        /**
         * When {@link #kind} is {@link JavaKind#Object}, indicates whether this value is a
         * compressed or uncompressed reference.
         */
        public boolean isCompressedReference() {
            return isCompressedReference;
        }

        /**
         * When true, the value is a monitor (a {@link FrameInfoQueryResult#numLocks lock slot},
         * located after the local variables and expression stack slots) that was eliminated and
         * re-locking must be performed during deoptimization.
         */
        public boolean isEliminatedMonitor() {
            return isEliminatedMonitor;
        }

        /**
         * Returns additional data for the value, according to the specification in
         * {@link ValueType}.
         */
        public long getData() {
            return data;
        }

        /**
         * Returns the constant value. Only non-null if the {@link #getType type} is
         * {@link ValueType#Constant} or {@link ValueType#DefaultConstant}.
         */
        public JavaConstant getValue() {
            return value;
        }
    }

    protected FrameInfoQueryResult caller;
    protected SharedMethod deoptMethod;
    protected int deoptMethodOffset;
    protected long encodedBci;
    protected boolean isDeoptEntry;
    protected boolean needLocalValues;
    protected int numLocals;
    protected int numStack;
    protected int numLocks;
    protected ValueInfo[] valueInfos;
    protected ValueInfo[][] virtualObjects;
    protected Class<?> sourceClass;
    protected String sourceMethodName;
    protected int sourceLineNumber;

    // Index of sourceClass in CodeInfoDecoder.frameInfoSourceClasses
    protected int sourceClassIndex;

    // Index of sourceMethodName in CodeInfoDecoder.frameInfoSourceMethodNames
    protected int sourceMethodNameIndex;

    public FrameInfoQueryResult() {
        init();
    }

    public void init() {
        caller = null;
        deoptMethod = null;
        deoptMethodOffset = 0;
        encodedBci = 0;
        isDeoptEntry = false;
        needLocalValues = false;
        numLocals = 0;
        numStack = 0;
        numLocks = 0;
        valueInfos = null;
        virtualObjects = null;
        sourceClass = null;
        sourceMethodName = "";
        sourceLineNumber = -1;
        sourceClassIndex = -1;
        sourceMethodNameIndex = -1;
    }

    /**
     * Returns the caller if this frame is an inlined method.
     */
    public FrameInfoQueryResult getCaller() {
        return caller;
    }

    /**
     * Returns the deoptimization target method, or {@code null} if not available. Only use the
     * result for debug printing, since it is not available in all cases.
     */
    public SharedMethod getDeoptMethod() {
        return deoptMethod;
    }

    /**
     * Returns the offset of the deoptimization target method. The offset is relative to the
     * {@link CodeInfoAccess#getCodeStart code start} of the {@link ImageCodeInfo image}. Together
     * with the BCI it is used to find the corresponding bytecode frame in the target method. Note
     * that there is no inlining in target methods, so the method + BCI is unique.
     */
    public int getDeoptMethodOffset() {
        return deoptMethodOffset;
    }

    /**
     * Returns the entry point address of the deoptimization target method.
     */
    public CodePointer getDeoptMethodAddress() {
        return CodeInfoAccess.absoluteIP(CodeInfoTable.getImageCodeInfo(), deoptMethodOffset);
    }

    /**
     * Returns an encoding of the bytecode index itself plus the duringCall and rethrowException
     * flags.
     */
    public long getEncodedBci() {
        return encodedBci;
    }

    /**
     * Returns the bytecode index.
     */
    public int getBci() {
        return FrameInfoDecoder.decodeBci(encodedBci);
    }

    /**
     * Returns true if this frame has been marked as a valid deoptimization entry point.
     */
    public boolean isDeoptEntry() {
        return isDeoptEntry;
    }

    /**
     * Returns the number of locals variables. It can be larger than the length of
     * {@link #getValueInfos()} because trailing illegal values are truncated there. It can be
     * smaller than the length of {@link #getValueInfos()} when expression stack values and locked
     * values are present.
     */
    public int getNumLocals() {
        return numLocals;
    }

    /**
     * Returns the number of locked values.
     */
    public int getNumLocks() {
        return numLocks;
    }

    /**
     * Returns the number of stack values.
     */
    public int getNumStack() {
        return numStack;
    }

    /**
     * Returns the local variables and expression stack values.
     */
    public ValueInfo[] getValueInfos() {
        return valueInfos;
    }

    /**
     * Returns the virtual objects. This result is the same for all frames in the
     * {@link #getCaller() inlining chain}.
     */
    public ValueInfo[][] getVirtualObjects() {
        return virtualObjects;
    }

    public Class<?> getSourceClass() {
        return sourceClass;
    }

    public String getSourceClassName() {
        return sourceClass != null ? sourceClass.getName() : "";
    }

    public String getSourceMethodName() {
        return sourceMethodName;
    }

    public String getSourceFileName() {
        return sourceClass != null ? DynamicHub.fromClass(sourceClass).getSourceFileName() : null;
    }

    public int getSourceLineNumber() {
        return sourceLineNumber;
    }

    /**
     * Returns the name and source code location of the method, for debugging purposes only.
     */
    public StackTraceElement getSourceReference() {
        /*
         * According to StackTraceElement undefined className is denoted by "", undefined fileName
         * is denoted by null
         */
        final String className = sourceClass != null ? sourceClass.getName() : "";
        String sourceFileName = sourceClass != null ? DynamicHub.fromClass(sourceClass).getSourceFileName() : null;
        return new StackTraceElement(className, sourceMethodName, sourceFileName, sourceLineNumber);
    }

    public boolean isNativeMethod() {
        return sourceLineNumber == -2;
    }

    public Log log(Log log) {
        String className = sourceClass != null ? sourceClass.getName() : "";
        String methodName = sourceMethodName != null ? sourceMethodName : "";
        log.string(className);
        if (!(className.isEmpty() || methodName.isEmpty())) {
            log.string(".");
        }
        log.string(methodName);
        if (isDeoptEntry()) {
            log.string("**");
        }

        log.string("(");
        if (isNativeMethod()) {
            log.string("Native Method");
        } else {
            String sourceFileName = sourceClass != null ? DynamicHub.fromClass(sourceClass).getSourceFileName() : null;
            if (sourceFileName != null) {
                if (sourceLineNumber >= 0) {
                    log.string(sourceFileName).string(":").signed(sourceLineNumber);
                } else {
                    log.string(sourceFileName);
                }
            } else {
                log.string("Unknown Source");
            }
        }
        log.string(")");

        return log;
    }

    /**
     * Returns the name of the local variable with the given index, for debugging purposes only.
     */
    public String getLocalVariableName(int idx) {
        return idx < valueInfos.length ? valueInfos[idx].name : null;
    }
}
