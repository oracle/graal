/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.deopt.Deoptimizer.TargetContent;
import com.oracle.svm.core.log.StringBuilderLog;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaConstant;

/**
 * The handle to a deoptimized frame. It contains all stack entries which are written to the frame
 * of the deopt target method(s). For details see {@link Deoptimizer}.
 * <p>
 * In addition to the instance fields, the {@link DeoptimizedFrame} also has same reserved space for
 * the return value registers. This is used by {@link Deoptimizer#deoptStub} for restoring the
 * original return values.
 */
@DeoptimizedFrame.ReserveDeoptScratchSpace
public final class DeoptimizedFrame {

    /**
     * Used to let the universe builder reserve some space in the instance layout for storing return
     * value registers.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ReserveDeoptScratchSpace {
    }

    /**
     * Returns the offset of the {@linkplain ReserveDeoptScratchSpace scratch space} in the object.
     */
    public static int getScratchSpaceOffset() {
        return NumUtil.roundUp(ConfigurationValues.getObjectLayout().getFirstFieldOffset(), FrameAccess.wordSize());
    }

    /**
     * Heap-based representation of a future baseline-compiled stack frame, i.e., the intermediate
     * representation between deoptimization of an optimized frame and the stack-frame rewriting.
     */
    public static class VirtualFrame {
        protected final FrameInfoQueryResult frameInfo;
        protected VirtualFrame caller;
        /** The program counter where execution continuous. */
        protected ReturnAddress returnAddress;

        /**
         * The saved base pointer for the target frame, or null if the architecture does not use
         * base pointers.
         */
        protected SavedBasePointer savedBasePointer;

        /**
         * The local variables and expression stack value of this frame. Local variables that are
         * unused at the deoptimization point are {@code null}.
         */
        protected final ConstantEntry[] values;

        protected VirtualFrame(FrameInfoQueryResult frameInfo) {
            this.frameInfo = frameInfo;
            this.values = new ConstantEntry[frameInfo.getValueInfos().length];
        }

        /**
         * The caller frame of this frame, or null if this is the outermost frame.
         */
        public VirtualFrame getCaller() {
            return caller;
        }

        /**
         * The deoptimization metadata for this frame, i.e., the metadata of the baseline-compiled
         * deoptimization target method.
         */
        public FrameInfoQueryResult getFrameInfo() {
            return frameInfo;
        }

        /**
         * Returns the value of the local variable or expression stack value with the given index.
         * Expression stack values are after all local variables.
         */
        public JavaConstant getConstant(int index) {
            if (index >= values.length || values[index] == null) {
                return JavaConstant.forIllegal();
            } else {
                return values[index].constant;
            }
        }
    }

    /**
     * Base class for all kind of stack entries.
     */
    abstract static class Entry {
        protected final int offset;

        protected Entry(int offset) {
            this.offset = offset;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected abstract void write(Deoptimizer.TargetContent targetContent);
    }

    /**
     * A constant on the stack. This is the only type of entry inside a deopt target frame.
     */
    abstract static class ConstantEntry extends Entry {

        protected final JavaConstant constant;

        protected static ConstantEntry factory(int offset, JavaConstant constant) {
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
                    throw VMError.shouldNotReachHere(constant.getJavaKind().toString());
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
    static class ReturnAddress extends Entry {
        protected long returnAddress;

        protected ReturnAddress(int offset, long returnAddress) {
            super(offset);
            this.returnAddress = returnAddress;
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void write(Deoptimizer.TargetContent targetContent) {
            targetContent.writeLong(offset, returnAddress);
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

        @Uninterruptible(reason = "Called from uninterruptible code.")
        protected void write(Deoptimizer.TargetContent targetContent, Pointer newSp) {
            targetContent.writeWord(offset, newSp.add(WordFactory.unsigned(valueRelativeToNewSp)));
        }
    }

    /** Data for re-locking of an object during deoptimization. */
    static class RelockObjectData {
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
    }

    protected static DeoptimizedFrame factory(int targetContentSize, long sourceEncodedFrameSize, SubstrateInstalledCode sourceInstalledCode, VirtualFrame topFrame,
                    RelockObjectData[] relockedObjects, CodePointer sourcePC) {
        final TargetContent targetContentBuffer = new TargetContent(targetContentSize, ConfigurationValues.getTarget().arch.getByteOrder());
        return new DeoptimizedFrame(sourceEncodedFrameSize, sourceInstalledCode, topFrame, targetContentBuffer, relockedObjects, sourcePC);
    }

    private final long sourceEncodedFrameSize;
    private final WeakReference<SubstrateInstalledCode> sourceInstalledCode;
    private final VirtualFrame topFrame;
    private final Deoptimizer.TargetContent targetContent;
    private final RelockObjectData[] relockedObjects;
    private final PinnedObject pin;
    private final CodePointer sourcePC;
    private final char[] completedMessage;

    private DeoptimizedFrame(long sourceEncodedFrameSize, SubstrateInstalledCode sourceInstalledCode, VirtualFrame topFrame, Deoptimizer.TargetContent targetContent,
                    RelockObjectData[] relockedObjects, CodePointer sourcePC) {
        this.sourceEncodedFrameSize = sourceEncodedFrameSize;
        this.topFrame = topFrame;
        this.targetContent = targetContent;
        this.relockedObjects = relockedObjects;
        this.sourceInstalledCode = sourceInstalledCode == null ? null : new WeakReference<>(sourceInstalledCode);
        this.sourcePC = sourcePC;
        this.pin = PinnedObject.create(this);
        StringBuilderLog sbl = new StringBuilderLog();
        sbl.string("deoptStub: completed for DeoptimizedFrame at ").hex(pin.addressOfObject()).newline();
        this.completedMessage = sbl.getResult().toCharArray();
    }

    /**
     * The frame size of the deoptimized method. This is the size of the physical stack frame that
     * is still present on the stack until the actual stack frame rewriting happens.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public long getSourceEncodedFrameSize() {
        return sourceEncodedFrameSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public long getSourceTotalFrameSize() {
        return CodeInfoQueryResult.getTotalFrameSize(sourceEncodedFrameSize);
    }

    /**
     * Returns the {@link InstalledCode} of the deoptimized method, or {@code null}. If a runtime
     * compiled method has been invalidated, the {@link InstalledCode} is no longer available. No
     * {@link InstalledCode} is available for native image methods (which are only deoptimized
     * during deoptimization testing).
     */
    public SubstrateInstalledCode getSourceInstalledCode() {
        return sourceInstalledCode == null ? null : sourceInstalledCode.get();
    }

    /**
     * The top frame, i.e., the innermost callee of the inlining hierarchy.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public VirtualFrame getTopFrame() {
        return topFrame;
    }

    /**
     * The new stack content for the target methods. In the second step of deoptimization this
     * content is built from the entries of {@link VirtualFrame}s.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    protected Deoptimizer.TargetContent getTargetContent() {
        return targetContent;
    }

    /**
     * Returns the {@link PinnedObject} that ensures that this {@link DeoptimizedFrame} is not moved
     * by the GC. The {@link DeoptimizedFrame} is accessed during GC when walking the stack.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public PinnedObject getPin() {
        return pin;
    }

    /**
     * The code address inside the source method (= the method to deoptimize).
     */
    public CodePointer getSourcePC() {
        return sourcePC;
    }

    @Uninterruptible(reason = "Called from Deoptimizer.deoptStub.")
    char[] getCompletedMessage() {
        return completedMessage;
    }

    /**
     * Fills the target content from the {@link VirtualFrame virtual frame} information. This method
     * must be uninterruptible.
     *
     * @param newSp the new stack pointer where execution will eventually continue
     */
    @Uninterruptible(reason = "Reads pointer values from the stack frame to unmanaged storage.")
    protected void buildContent(Pointer newSp) {

        VirtualFrame cur = topFrame;
        do {
            cur.returnAddress.write(targetContent);
            if (cur.savedBasePointer != null) {
                cur.savedBasePointer.write(targetContent, newSp);
            }
            for (int i = 0; i < cur.values.length; i++) {
                if (cur.values[i] != null) {
                    cur.values[i].write(targetContent);
                }
            }
            cur = cur.caller;
        } while (cur != null);

        if (relockedObjects != null) {
            for (RelockObjectData relockedObject : relockedObjects) {
                MonitorSupport.singleton().doRelockObject(relockedObject.object, relockedObject.lockData);
            }
        }
    }

    /**
     * Rewrites the first return address entry to the exception handler. This lets the
     * deoptimization stub return to the exception handler instead of the regular return address of
     * the deoptimization target.
     */
    public void takeException() {
        ReturnAddress firstAddressEntry = topFrame.returnAddress;
        CodeInfo info = CodeInfoTable.getImageCodeInfo();
        SimpleCodeInfoQueryResult codeInfoQueryResult = StackValue.get(SimpleCodeInfoQueryResult.class);
        CodeInfoAccess.lookupCodeInfo(info, CodeInfoAccess.relativeIP(info, WordFactory.pointer(firstAddressEntry.returnAddress)), codeInfoQueryResult);
        long handler = codeInfoQueryResult.getExceptionOffset();
        assert handler != 0 : "no exception handler registered for deopt target";
        firstAddressEntry.returnAddress += handler;
    }
}
