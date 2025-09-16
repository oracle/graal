/*
 * Copyright (c) 2009, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.asm;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.function.Consumer;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;

/**
 * The platform-independent base class for the assembler.
 */
public abstract class Assembler<T extends Enum<T>> {

    public abstract static class CodeAnnotation {
    }

    private final TargetDescription target;

    /**
     * Labels with instructions to be patched when it is {@linkplain Label#bind bound}.
     */
    Label labelsWithPatches;

    /**
     * Backing code buffer.
     */
    private final Buffer codeBuffer;

    protected Consumer<CodeAnnotation> codePatchingAnnotationConsumer;

    /**
     * CPU features that are statically available.
     */
    private final EnumSet<T> features;
    /**
     * Stack of features that are temporarily available in a certain region of the code. Each
     * element only contains the features which were added.
     *
     * @see #addFeatures
     * @see #removeFeatures
     */
    private final ArrayDeque<EnumSet<T>> featuresStack;

    public Assembler(TargetDescription target, EnumSet<T> features) {
        this.target = target;
        this.codeBuffer = new Buffer(target.arch.getByteOrder());
        this.features = features;
        featuresStack = new ArrayDeque<>(1);
    }

    public final EnumSet<T> getFeatures() {
        return features;
    }

    /**
     * Add a new item at the top of the feature stack. The new item will contain all those
     * {@code newFeatures} that aren't already contained in {@link #getFeatures()}. A feature stack
     * item will always be added, even if none of the features are actually new.
     */
    public void addFeatures(EnumSet<T> newFeatures) {
        EnumSet<T> added = EnumSet.copyOf(newFeatures);
        added.removeIf(feature -> !getFeatures().add(feature));
        featuresStack.push(added);
    }

    /**
     * Removes the topmost item from the feature stack and removes all of this item's features from
     * {@link #getFeatures()}.
     */
    public void removeFeatures() {
        GraalError.guarantee(!featuresStack.isEmpty(), "cannot remove features since no features have been added");
        getFeatures().removeAll(featuresStack.pop());
    }

    /**
     * Returns {@code true} if the feature is included in the current topmost item of the feature
     * stack.
     */
    public boolean isCurrentRegionFeature(T feature) {
        if (featuresStack.isEmpty()) {
            return false;
        }
        return featuresStack.peek().contains(feature);
    }

    public void setCodePatchingAnnotationConsumer(Consumer<CodeAnnotation> codeAnnotationConsumer) {
        assert this.codePatchingAnnotationConsumer == null : "overwriting existing value";
        this.codePatchingAnnotationConsumer = codeAnnotationConsumer;
    }

    /**
     * Returns the current position of the underlying code buffer.
     *
     * @return current position in code buffer
     */
    public int position() {
        return codeBuffer.position();
    }

    public final void emitByte(int x) {
        codeBuffer.emitByte(x);
    }

    public final void emitShort(int x) {
        codeBuffer.emitShort(x);
    }

    public final void emitInt(int x) {
        codeBuffer.emitInt(x);
    }

    public final void emitLong(long x) {
        codeBuffer.emitLong(x);
    }

    public final void emitByte(int b, int pos) {
        codeBuffer.emitByte(b, pos);
    }

    public final void emitShort(int b, int pos) {
        codeBuffer.emitShort(b, pos);
    }

    public final void emitInt(int b, int pos) {
        codeBuffer.emitInt(b, pos);
    }

    public final void emitLong(long b, int pos) {
        codeBuffer.emitLong(b, pos);
    }

    public final int getByte(int pos) {
        return codeBuffer.getByte(pos);
    }

    public final int getShort(int pos) {
        return codeBuffer.getShort(pos);
    }

    public final int getInt(int pos) {
        return codeBuffer.getInt(pos);
    }

    private static final String NEWLINE = System.lineSeparator();

    /**
     * Some GPU architectures have a text based encoding.
     */
    public final void emitString(String x) {
        emitString0("\t");  // XXX REMOVE ME pretty-printing
        emitString0(x);
        emitString0(NEWLINE);
    }

    // XXX for pretty-printing
    public final void emitString0(String x) {
        codeBuffer.emitBytes(x.getBytes(), 0, x.length());
    }

    public void emitString(String s, int pos) {
        codeBuffer.emitBytes(s.getBytes(), pos);
    }

    /**
     * Closes this assembler. No extra data can be written to this assembler after this call.
     *
     * @param trimmedCopy if {@code true}, then a copy of the underlying byte array up to (but not
     *            including) {@code position()} is returned
     * @return the data in this buffer or a trimmed copy if {@code trimmedCopy} is {@code true}
     */
    public final byte[] close(boolean trimmedCopy) {
        return closeAligned(trimmedCopy, 0);
    }

    /**
     * Closes this assembler. No extra data can be written to this assembler after this call.
     *
     * @param trimmedCopy if {@code true}, then a copy of the underlying byte array up to (but not
     *            including) {@code position()} is returned
     * @param alignment if {@code > 0}, then align the end of the code buffer with NOPs to the
     *            specified alignment
     * @return the data in this buffer or a trimmed copy if {@code trimmedCopy} is {@code true}
     */
    public byte[] closeAligned(boolean trimmedCopy, int alignment) {
        checkAndClearLabelsWithPatches();
        if (alignment > 0 && position() % alignment != 0) {
            this.align(alignment);
        }
        finalCodeSize = position();
        return codeBuffer.close(trimmedCopy);
    }

    private int finalCodeSize = -1;

    /**
     * Returns the final code size after code emission has been completed.
     */
    public int finalCodeSize() {
        assert codeBuffer.data == null : "Buffer is expected to be closed";
        return finalCodeSize;
    }

    public byte[] copy(int start, int end) {
        return codeBuffer.copyData(start, end);
    }

    private void checkAndClearLabelsWithPatches() throws InternalError {
        Label label = labelsWithPatches;
        while (label != null) {
            if (label.patchPositions != null) {
                throw new GraalError("Label used by instructions at following offsets has not been bound: %s", label.patchPositions);
            }
            Label next = label.nextWithPatches;
            label.nextWithPatches = null;
            label = next;
        }
        labelsWithPatches = null;
    }

    public void bind(Label l) {
        assert !l.isBound() : "can bind label only once";
        l.bind(position(), this);
    }

    public abstract void align(int modulus);

    /**
     * Emit an instruction that will fail in some way if it is reached.
     */
    public abstract void halt();

    public abstract void jmp(Label l);

    protected abstract void patchJumpTarget(int branch, int jumpTarget);

    /**
     * This is used by the CompilationResultBuilder to convert a {@link StackSlot} to an
     * {@link AbstractAddress}.
     *
     * @param transferSize bit size of memory operation this address will be used in.
     */
    public abstract AbstractAddress makeAddress(int transferSize, Register base, int displacement);

    /**
     * Returns a target specific placeholder address that can be used for code patching.
     *
     * @param instructionStartPosition The start of the instruction, i.e., the value that is used as
     *            the key for looking up placeholder patching information.
     */
    public abstract AbstractAddress getPlaceholder(int instructionStartPosition);

    /**
     * Emits a NOP instruction to advance the current PC.
     */
    public abstract void ensureUniquePC();

    public void maybeEmitIndirectTargetMarker() {
        // intentionally empty
    }

    /**
     * Passes CompilationResultBuilder and Label so the underlying block's indirect branch target
     * information can be queried.
     */
    @SuppressWarnings("unused")
    public void maybeEmitIndirectTargetMarker(CompilationResultBuilder crb, Label label) {
        // intentionally empty
    }

    /**
     * Some platforms might require special post call code emission.
     */
    public void postCallNop(Call call) {
        if (call.debugInfo != null) {
            // The nop inserted after a call is only required to distinguish
            // debug info associated with the call from debug info associated
            // with an instruction after the call. If the call has no debug
            // info, the extra nop is not required.
            ensureUniquePC();
        }
    }

    public void reset() {
        labelsWithPatches = null;
        codeBuffer.reset();
    }

    public boolean isTargetMP() {
        return target.isMP;
    }

    public int getReturnAddressSize() {
        return target.arch.getReturnAddressSize();
    }

    public int getMachineCodeCallDisplacementOffset() {
        return target.arch.getMachineCodeCallDisplacementOffset();
    }

    public boolean inlineObjects() {
        return target.inlineObjects;
    }

    public static void guaranteeDifferentRegisters(Register... registers) {
        for (int i = 0; i < registers.length - 1; ++i) {
            for (int j = i + 1; j < registers.length; ++j) {
                if (registers[i].equals(registers[j])) {
                    throw new GraalError("Multiple uses of register: %s %s", registers[i], Arrays.toString(registers));
                }
            }
        }
    }
}
