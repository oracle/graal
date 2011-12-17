/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm;

import java.io.*;
import java.util.*;

import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * Assembler base class.
 */
public abstract class Assembler {

    private final Directives directives;

    protected Assembler(byte byteData, boolean isValidCode) {
        this.directives = new Directives(byteData, isValidCode);
    }

    public abstract ISA isa();

    public final Directives directives() {
        return directives;
    }

    /**
     * Gets the width of a word on machine for which the code is being assembled.
     */
    public abstract WordWidth wordWidth();

    /**
     * Resets any internal state in this assembler to the equivalent state it had when first constructed.
     * <p>
     * This method is overridden as needed to ensure the state in subclasses is reset as well. Any
     * overriding implementation must call this method in its superclass.
     */
    public Assembler reset() {
        boundLabels.clear();
        assembledObjects.clear();
        mutableAssembledObjects.clear();
        padOutput = false;
        potentialExpansionSize = 0;
        selectingLabelInstructions = true;
        stream.reset();
        if (this instanceof Assembler32) {
            final Assembler32 assembler32 = (Assembler32) this;
            assembler32.setStartAddress(0);
        } else if (this instanceof Assembler64) {
            final Assembler64 assembler64 = (Assembler64) this;
            assembler64.setStartAddress(0);
        }
        return this;
    }

    /**
     * A facility for including output during assembly that may not necessarily be decoded interpreted as code.
     *
     */
    public final class Directives {

        private final byte padByte;
        private final boolean isValidCode;

        public Directives(byte padByte, boolean isValidCode) {
            this.padByte = padByte;
            this.isValidCode = isValidCode;
        }
        /**
         * Inserts as many {@linkplain #padByte pad bytes} as necessary to ensure that the next assembled object starts
         * at an address aligned by a given number.
         *
         * @param alignment
         *                the next assembled object is guaranteed to start at the next highest address starting at the
         *                current address that is divisible by this value. Note that this computed address will be the
         *                current address if the current address is already aligned by {@code alignment}
         */
        private void alignIfSpaceLeftSmallerThan(int alignment, int requiredSpace, int alignmentStart) {
            final int startPosition = currentPosition();

            // We avoid sign problems with '%' below by masking off the sign bit:
            final long unsignedAddend = (baseAddress() + startPosition + alignmentStart) & Long.MAX_VALUE;

            final int misalignmentSize = (int) (unsignedAddend % alignment);
            final int padSize = misalignmentSize > 0 ? (alignment - misalignmentSize) : 0;
            for (int i = 0; i < padSize; i++) {
                emitByte(padByte);
            }
            new AlignmentPadding(Assembler.this, startPosition, currentPosition(), alignment, alignmentStart, requiredSpace, padByte) {
                public boolean isCode() {
                    return isValidCode;
                }
            };
        }

        public void align(int alignment) {
            alignIfSpaceLeftSmallerThan(alignment, alignment, 0);
        }

        /**
         * Enforce that the next assembled object fits within an aligned chunk.
         * The specified space required by the next assembled object must be smaller or equals to the specified alignment. If the object cannot fit in the current chunk,
         * Inserts as many {@linkplain #padByte pad bytes} as necessary to ensure that the next assembled object starts
         * at the next alignment.
         *
         * @param alignment
         *                the next assembled object is guaranteed to fit in within a block of memory whose starting address is the next
         *                 address starting at the
         *                current address that is divisible by this value.
         *  @param requiredSpace size of the next assembled object; it must be smaller or equals to alignment
         */
        public boolean align(int alignment, int requiredSpace) {
            if (alignment < requiredSpace) {
                return false;
            }
            alignIfSpaceLeftSmallerThan(alignment, requiredSpace, 0);
            return true;
        }

        /**
         *  Inserts as many {@linkplain #padByte pad bytes} as necessary to ensure that the nth byte within the next assembled object starts
         *   at an address aligned by a given number.
         * @param alignment
         * @param alignmentStart where the alignment should start within the next assembled object.
         */
        public void alignAfter(int alignment, int alignmentStart) {
            alignIfSpaceLeftSmallerThan(alignment, alignment, alignmentStart);
        }

        public void inlineByte(byte byteValue) {
            addInlineData(currentPosition(), Bytes.SIZE);
            emitByte(byteValue);
        }

        public void inlineByteArray(byte[] byteArrayValue) {
            addInlineData(currentPosition(), byteArrayValue.length);
            emitByteArray(byteArrayValue, 0, byteArrayValue.length);
        }

        public void inlineByteArray(byte[] byteArrayValue, int offset, int length) {
            addInlineData(currentPosition(), length);
            emitByteArray(byteArrayValue, offset, length);
        }

        public void inlineShort(short shortValue) {
            addInlineData(currentPosition(), Shorts.SIZE);
            emitShort(shortValue);
        }

        public void inlineInt(int intValue) {
            addInlineData(currentPosition(), Ints.SIZE);
            emitInt(intValue);
        }

        public void inlineLong(long longValue) {
            addInlineData(currentPosition(), Longs.SIZE);
            emitLong(longValue);
        }

        /**
         * Inlines the absolute address of a position (represented by a given label) in the assembled code.
         * The absolute address is calculated as {@code baseAddress() + label.position()}. The size
         * of the inlined address is determined by {@link Assembler#wordWidth()}.
         *
         * @param label the label whose absolute address is to be inlined
         */
        public AddressLiteral inlineAddress(Label label) {
            final int startPosition = currentPosition();
            // Emit placeholder bytes
            final WordWidth width = wordWidth();
            for (int i = 0; i < width.numberOfBytes; i++) {
                emitByte((byte) 0);
            }
            final AddressLiteral addressLiteral = new AddressLiteral(Assembler.this, startPosition, currentPosition(), label);
            assert addressLiteral.size() == width.numberOfBytes;
            return addressLiteral;
        }

        /**
         * Inlines the offset between two positions (represented by given labels) in the assembled code.
         *
         * @param base the label whose position marks the base of the offset
         * @param target the label whose position marks the target of the offset
         * @param width the fixed size to be used for the offset
         */
        public OffsetLiteral inlineOffset(Label target, Label base, WordWidth width) {
            final int startPosition = currentPosition();
            for (int i = 0; i < width.numberOfBytes; i++) {
                emitByte((byte) 0);
            }
            final OffsetLiteral offsetLiteral = new OffsetLiteral(Assembler.this, startPosition, currentPosition(), target, base);
            assert offsetLiteral.size() == width.numberOfBytes;
            return offsetLiteral;
        }
    }

    /**
     * Gets the number of bytes that have been written to the underlying output stream.
     */
    public int currentPosition() {
        return stream.size();
    }

    /**
     * Gets the start address of the code assembled by this assembler.
     */
    public abstract long baseAddress();

    private ByteArrayOutputStream stream = new ByteArrayOutputStream();

    protected void emitByte(int byteValue) {
        stream.write(byteValue);
    }

    protected void emitZeroes(int count) {
        for (int i = 0; i < count; ++i) {
            stream.write(0);
        }
    }

    protected abstract void emitShort(short shortValue);

    protected abstract void emitInt(int intValue);

    protected abstract void emitLong(long longValue);

    public void emitByteArray(byte[] byteArrayValue, int off, int len) {
        stream.write(byteArrayValue, off, len);
    }

    private boolean selectingLabelInstructions = true;

    boolean selectingLabelInstructions() {
        return selectingLabelInstructions;
    }

    private final Set<Label> boundLabels = Collections.newSetFromMap(new IdentityHashMap<Label, Boolean>());

    public Set<Label> boundLabels() {
        return boundLabels;
    }

    /**
     * Binds a given label to the current position in the assembler's instruction stream. The assembler may update the
     * label's position if any emitted instructions change lengths, so that this label keeps addressing the same logical
     * position.
     *
     * @param label
     *                the label that is to be bound to the current position
     *
     * @see Label#fix32
     */
    public final void bindLabel(Label label) {
        label.bind(currentPosition());
        boundLabels.add(label);
    }

    private final List<AssembledObject> assembledObjects = new LinkedList<AssembledObject>();
    private final List<MutableAssembledObject> mutableAssembledObjects = new LinkedList<MutableAssembledObject>();

    private int potentialExpansionSize;

    /**
     * Adds the description of an instruction that is fixed in size.
     *
     * @param fixedSizeAssembledObject
     */
    void addFixedSizeAssembledObject(AssembledObject fixedSizeAssembledObject) {
        assembledObjects.add(fixedSizeAssembledObject);
        if (fixedSizeAssembledObject instanceof MutableAssembledObject) {
            mutableAssembledObjects.add((MutableAssembledObject) fixedSizeAssembledObject);
        }
    }

    /**
     * Adds the description of an instruction that can change in size, depending on where it is located in the
     * instruction and/or where another object it addresses is located in the instruction stream.
     *
     * @param spanDependentInstruction
     */
    void addSpanDependentInstruction(InstructionWithOffset spanDependentInstruction) {
        assembledObjects.add(spanDependentInstruction);
        mutableAssembledObjects.add(spanDependentInstruction);
        // A span-dependent instruction's offset operand can potentially grow from 8 bits to 32 bits.
        // Also, some instructions need an extra byte for encoding when not using an 8-bit operand.
        // Together, this might enlarge every span-dependent label instruction by maximally 4 bytes.
        potentialExpansionSize += 4;
    }

    void addAlignmentPadding(AlignmentPadding alignmentPadding) {
        assembledObjects.add(alignmentPadding);
        mutableAssembledObjects.add(alignmentPadding);
        potentialExpansionSize += alignmentPadding.alignment() - alignmentPadding.size();
    }

    void addInlineData(int startPosition, int size) {
        assembledObjects.add(new AssembledObject(startPosition, startPosition + size) {
            public boolean isCode() {
                return false;
            }
        });
    }

    private void gatherLabels() throws AssemblyException {
        for (AssembledObject assembledObject : assembledObjects) {
            if (assembledObject instanceof InstructionWithLabel) {
                final InstructionWithLabel labelInstruction = (InstructionWithLabel) assembledObject;
                switch (labelInstruction.label().state()) {
                    case UNASSIGNED:
                        throw new AssemblyException("unassigned label");
                    case BOUND:
                        boundLabels.add(labelInstruction.label());
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private boolean updateSpanDependentInstruction(InstructionWithOffset instruction) throws AssemblyException {
        if (!instruction.updateLabelSize()) {
            return false;
        }
        final int oldSize = instruction.size();
        final int oldEndPosition = instruction.endPosition();
        stream.reset();
        instruction.assemble();
        final int newSize = stream.toByteArray().length;
        instruction.setSize(newSize);
        final int delta = newSize - oldSize;
        adjustMutableAssembledObjects(delta, oldEndPosition, null);
        return true;
    }

    private boolean updateAlignmentPadding(AlignmentPadding alignmentPadding) throws AssemblyException {
        final int oldSize = alignmentPadding.size();
        final int oldEndPosition = alignmentPadding.endPosition();
        alignmentPadding.updatePadding();
        final int newSize = alignmentPadding.size();
        if (oldSize != newSize) {
            // Only if the padding expanded will subsequent objects in the stream need to be adjusted
            final int delta = newSize - oldSize;
            adjustMutableAssembledObjects(delta, oldEndPosition, alignmentPadding);
            return true;
        }
        return false;
    }

    /**
     * Adjusts the position of all the mutable objects that are currently at or after a given position.
     *
     * @param delta the amount by which the position of each mutable object is to be adjusted
     * @param startPosition only mutable objects whose current {@linkplain AssembledObject#startPosition() start
     *            position} is equal to or greater than this value are adjusted
     * @param adjustedPadding the padding object whose adjustment caused the need for this call. If this call was made
     *            for some other reason than having adjusted a padding object's size, then this value will be null. In this
     *            value is not null, then its position will not be adjusted by this call.
     */
    private void adjustMutableAssembledObjects(int delta, int startPosition, AlignmentPadding adjustedPadding) throws AssemblyException {
        for (Label label : boundLabels) {
            if (label.position() >= startPosition) {
                label.adjust(delta);
            }
        }

        for (MutableAssembledObject mutableAssembledObject : mutableAssembledObjects) {
            if (mutableAssembledObject != adjustedPadding && mutableAssembledObject.startPosition() >= startPosition) {
                mutableAssembledObject.adjust(delta);
            }
        }
    }

    private void updateSpanDependentVariableInstructions() throws AssemblyException {
        boolean changed;
        do {
            changed = false;
            for (MutableAssembledObject mutableAssembledObject : mutableAssembledObjects) {
                if (mutableAssembledObject instanceof InstructionWithOffset) {
                    changed |= updateSpanDependentInstruction((InstructionWithOffset) mutableAssembledObject);
                } else if (mutableAssembledObject instanceof AlignmentPadding) {
                    changed |= updateAlignmentPadding((AlignmentPadding) mutableAssembledObject);
                }
            }
        } while (changed);
    }

    private int writeOutput(OutputStream outputStream, byte[] initialBytes, InlineDataRecorder inlineDataRecorder) throws IOException, AssemblyException {
        selectingLabelInstructions = false;
        int bytesWritten = 0;
        try {
            int initialOffset = 0;
            for (AssembledObject assembledObject : assembledObjects) {
                if (inlineDataRecorder != null && !assembledObject.isCode()) {
                    inlineDataRecorder.add(new InlineDataDescriptor.ByteData(assembledObject.startPosition(), assembledObject.size()));
                }

                if (assembledObject instanceof MutableAssembledObject) {
                    final MutableAssembledObject mutableAssembledObject = (MutableAssembledObject) assembledObject;

                    // Copy the original assembler output between the end of the last mutable assembled object and the start of the current one
                    final int length = mutableAssembledObject.initialStartPosition() - initialOffset;
                    outputStream.write(initialBytes, initialOffset, length);
                    bytesWritten += length;

                    // Now (re)assemble the mutable assembled object
                    stream.reset();
                    mutableAssembledObject.assemble();
                    stream.writeTo(outputStream);
                    bytesWritten += stream.size();
                    initialOffset = mutableAssembledObject.initialEndPosition();
                } else {
                    // Copy the original assembler output between the end of the last assembled object and the end of current one
                    final int length = assembledObject.endPosition() - initialOffset;
                    outputStream.write(initialBytes, initialOffset, length);
                    bytesWritten += length;
                    initialOffset = assembledObject.endPosition();
                }
            }

            // Copy the original assembler output (if any) after the last mutable assembled object
            outputStream.write(initialBytes, initialOffset, initialBytes.length - initialOffset);
            bytesWritten += initialBytes.length - initialOffset;

            if (padOutput) {
                final int padding = (initialBytes.length + potentialExpansionSize) - bytesWritten;
                assert padding >= 0;
                if (padding > 0) {
                    stream.reset();
                    emitPadding(padding);
                    stream.writeTo(outputStream);
                    bytesWritten += padding;
                }
            }
            return bytesWritten;
        } finally {
            selectingLabelInstructions = true;
        }
    }

    /**
     * Emits padding to the instruction stream in the form of NOP instructions.
     *
     * @param numberOfBytes
     * @throws AssemblyException if exactly {@code numberOfBytes} cannot be emitted as a sequence of one or more valid NOP instructions
     */
    protected abstract void emitPadding(int numberOfBytes) throws AssemblyException;

    /**
     * Writes the object code assembled so far to a given output stream.
     *
     * @return the number of bytes written {@code outputStream}
     * @throws AssemblyException
     *             if there any problem with binding labels to addresses
     */
    public int output(OutputStream outputStream, InlineDataRecorder inlineDataRecorder) throws IOException, AssemblyException {
        final int upperLimitForCurrentOutputSize = upperLimitForCurrentOutputSize();
        final byte[] initialBytes = stream.toByteArray();
        gatherLabels();
        updateSpanDependentVariableInstructions();
        final int bytesWritten = writeOutput(outputStream, initialBytes, inlineDataRecorder);
        assert !padOutput || upperLimitForCurrentOutputSize == bytesWritten;
        return bytesWritten;
    }

    /**
     * Gets the maximum size of the code array that would be assembled by a call to {@link #output(OutputStream)} or
     * {@link #toByteArray()}. For a variable sized instruction set (e.g. x86), the exact size may be known until the
     * code is assembled as the size of certain instructions depends on their position in the instruction and/or the
     * {@linkplain #baseAddress() base address} at which the code is being assembled.
     * <p>
     * <b>Note that any subsequent call that adds a new instruction to the instruction stream invalidates the value
     * returned by this method.</b>
     */
    public int upperLimitForCurrentOutputSize() {
        return currentPosition() + potentialExpansionSize;
    }

    private boolean padOutput;

    /**
     * Sets or unsets the flag determining if the code assembled by a call to {@link #output(OutputStream)} or
     * {@link #toByteArray()} should be padded with NOPs at the end to ensure that the code size equals the value
     * returned by {@link #upperLimitForCurrentOutputSize()}. This default value of the flag is {@code false}.
     */
    public void setPadOutput(boolean flag) {
        padOutput = flag;
    }

    /**
     * Returns the object code assembled so far in a byte array.
     *
     * @throws AssemblyException
     *             if there any problem with binding labels to addresses
     */
    public byte[] toByteArray(InlineDataRecorder inlineDataRecorder) throws AssemblyException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(upperLimitForCurrentOutputSize());
        try {
            output(baos, inlineDataRecorder);
            baos.close();
            final byte[] result = baos.toByteArray();
            return result;
        } catch (IOException ioException) {
            throw ProgramError.unexpected("IOException during output to byte array", ioException);
        }
    }

    public byte[] toByteArray() throws AssemblyException {
        return toByteArray(null);
    }

    /**
     * @see Label#fix32(int)
     */
    protected void fixLabel32(Label label, int address32) {
        label.fix32(address32);
    }

    /**
     * @see Label#fix64(long)
     */
    protected void fixLabel64(Label label, long address64) {
        label.fix64(address64);
    }

    protected int address32(Label label) throws AssemblyException {
        return label.address32();
    }

    protected long address64(Label label) throws AssemblyException {
        return label.address64();
    }

    protected void checkConstraint(boolean passed, String expression) {
        if (!passed) {
            throw new IllegalArgumentException(expression);
        }
    }

    /**
     * Calculate the difference between two Labels. This works whether the labels
     * are fixed or bound.
     * @throws AssemblyException
     */
    public int labelOffsetRelative(Label label, Label relativeTo) throws AssemblyException {
        return labelOffsetRelative(label, 0) - labelOffsetRelative(relativeTo, 0);
    }

    /**
     * Calculate the difference between a Label and a position within the assembled code.
     * @throws AssemblyException
     */
    public int labelOffsetRelative(Label label, int position) throws AssemblyException {
        switch (label.state()) {
            case BOUND: {
                return label.position() - position;
            }
            case FIXED_32: {
                final Assembler32 assembler32 = (Assembler32) this;
                return assembler32.address(label) - (assembler32.startAddress() + position);
            }
            case FIXED_64: {
                final Assembler64 assembler64 = (Assembler64) this;
                final long offset64 = assembler64.address(label) - (assembler64.startAddress() + position);
                if (Longs.numberOfEffectiveSignedBits(offset64) > 32) {
                    throw new AssemblyException("fixed 64-bit label out of 32-bit range");
                }
                return (int) offset64;
            }
            default: {
                throw new AssemblyException("unassigned label");
            }
        }
    }

    /**
     * Calculate the difference between a Label and an assembled object.
     * Different CPUs have different conventions for which end of an
     * instruction to measure from.
     * @throws AssemblyException
     */
    public final int offsetInstructionRelative(Label label, AssemblyObject assembledObject) throws AssemblyException {
        final int position = (isa().relativeBranchFromStart) ? assembledObject.startPosition() : assembledObject.endPosition();
        return labelOffsetRelative(label, position);
    }
}
