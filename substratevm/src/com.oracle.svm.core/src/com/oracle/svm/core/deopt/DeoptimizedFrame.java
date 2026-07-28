/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.shared.Uninterruptible;

import jdk.vm.ci.meta.JavaConstant;

/**
 * The handle to a deoptimized frame. It contains all stack entries which are written to the frame
 * of the deopt target method(s). For details see {@link Deoptimizer}.
 * <p>
 */
public abstract class DeoptimizedFrame {
    /**
     * The frame size of the deoptimized method. This is the size of the physical stack frame that
     * is still present on the stack until the actual stack frame rewriting happens.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract long getSourceEncodedFrameSize();

    public abstract long getSourceTotalFrameSize();

    /**
     * The code address inside the source method (= the method to deoptimize).
     */
    public abstract CodePointer getSourcePC();

    /**
     * Rewrites the first return address entry to the exception handler. This lets the
     * deoptimization stub return to the exception handler instead of the regular return address of
     * the deoptimization target.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract void takeException();

    /**
     * Returns the {@link PinnedObject} that ensures that this {@link DeoptimizedFrame} is not moved
     * by the GC. The {@link DeoptimizedFrame} is accessed during GC when walking the stack.
     */
    public abstract PinnedObject getPin();

    /**
     * The top frame, i.e., the innermost callee of the inlining hierarchy.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract VirtualFrame getTopFrame();

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public abstract DeoptTargetTier getTargetTier();

    public enum DeoptTargetTier {
        /**
         * Baseline-compiled AOT deoptimization target entry method.
         */
        BaselineCompiledCode,
        /**
         * Crema interpreter.
         */
        Interpreter;
    }

    public abstract SubstrateInstalledCode getSourceInstalledCode();

    public abstract void logTraceDeoptMessage(Log log, FrameInfoQueryResult sourceTopFrame, boolean printOnlyTopFrames);

    /**
     * Returns the diagnostic event emitted when the deopt stub finishes handing off this frame.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected abstract char[] getCompletedMessage();

    /**
     * Base class for all kind of stack entries.
     */
    abstract static class Entry {
        protected final int offset;

        protected Entry(int offset) {
            this.offset = offset;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected abstract void write(Deoptimizer.TargetContent targetContent);
    }

    /**
     * A constant on the stack. This is the only type of entry inside a deopt target frame.
     */
    abstract static class ConstantEntry extends Entry {

        protected final JavaConstant constant;

        protected static ConstantEntry factory(int offset, JavaConstant constant, FrameInfoQueryResult frameInfo) {
            switch (constant.getJavaKind()) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                    return new FourByteConstantEntry(offset, constant, constant.asInt());
                case Float:
                    return new FourByteConstantEntry(offset, constant, Float.floatToIntBits(constant.asFloat()));
                case Long:
                    return new EightByteConstantEntry(offset, constant, constant.asLong());
                case Double:
                    return new EightByteConstantEntry(offset, constant, Double.doubleToLongBits(constant.asDouble()));
                case Object:
                    return new ObjectConstantEntry(offset, constant, SubstrateObjectConstant.asObject(constant), SubstrateObjectConstant.isCompressed(constant));
                default:
                    throw Deoptimizer.fatalDeoptimizationError("Unexpected constant type: " + constant, frameInfo);
            }
        }

        /* Constructor for subclasses. Clients should use the factory. */
        protected ConstantEntry(int offset, JavaConstant constant) {
            super(offset);
            this.constant = constant;
        }
    }

    /** A constant primitive value, stored on the stack as 4 bytes. */
    static class FourByteConstantEntry extends ConstantEntry {

        protected final int value;

        protected FourByteConstantEntry(int offset, JavaConstant constant, int value) {
            super(offset, constant);
            this.value = value;
        }

        @Override
        @Uninterruptible(reason = "Writes pointers to unmanaged storage.")
        protected void write(Deoptimizer.TargetContent targetContent) {
            targetContent.writeInt(offset, value);
        }
    }

    /** A constant primitive value, stored on the stack as 8 bytes. */
    static class EightByteConstantEntry extends ConstantEntry {

        protected final long value;

        protected EightByteConstantEntry(int offset, JavaConstant constant, long value) {
            super(offset, constant);
            this.value = value;
        }

        @Override
        @Uninterruptible(reason = "Writes pointers to unmanaged storage.")
        protected void write(Deoptimizer.TargetContent targetContent) {
            targetContent.writeLong(offset, value);
        }
    }

    /** A constant Object value. */
    static class ObjectConstantEntry extends ConstantEntry {

        protected final Object value;
        protected final boolean compressed;

        protected ObjectConstantEntry(int offset, JavaConstant constant, Object value, boolean compressed) {
            super(offset, constant);
            this.value = value;
            this.compressed = compressed;
        }

        @Override
        @Uninterruptible(reason = "Writes pointers to unmanaged storage.")
        protected void write(Deoptimizer.TargetContent targetContent) {
            targetContent.writeObject(offset, value, compressed);
        }
    }

    /**
     * The return address, located between deopt target frames.
     */
    public static class ReturnAddress extends Entry {
        protected long returnAddress;

        public ReturnAddress(int offset, long returnAddress) {
            super(offset);
            this.returnAddress = returnAddress;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void write(Deoptimizer.TargetContent targetContent) {
            targetContent.writeLong(offset, returnAddress);
        }

        public long getReturnAddress() {
            return returnAddress;
        }
    }

    /**
     * The saved base pointer, located between deopt target frames.
     */
    static class SavedBasePointer {
        private final int offset;
        private final long valueRelativeToNewSp;

        protected SavedBasePointer(int offset, long valueRelativeToNewSp) {
            this.offset = offset;
            this.valueRelativeToNewSp = valueRelativeToNewSp;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected void write(Deoptimizer.TargetContent targetContent, Pointer newSp) {
            targetContent.writeWord(offset, newSp.add(Word.unsigned(valueRelativeToNewSp)));
        }
    }

    /** Data for re-locking of an object during deoptimization. */
    public static class RelockObjectData {
        /** The object that needs to be re-locked. */
        final Object object;
        /**
         * Value returned by {@link MonitorSupport#prepareRelockObject} and passed to
         * {@link MonitorSupport#doRelockObject}.
         */
        final Object lockData;

        RelockObjectData(Object object, Object lockData) {
            this.object = object;
            this.lockData = lockData;
        }

        public Object getLockData() {
            return lockData;
        }

        public Object getObject() {
            return object;
        }
    }
}
