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
package com.sun.max.asm.dis.risc;

import java.io.*;
import java.util.*;
import java.util.Arrays;

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 */
public abstract class RiscDisassembler extends Disassembler {

    private final RiscAssembly assembly;

    protected RiscDisassembler(ImmediateArgument startAddress, RiscAssembly assembly, Endianness endianness, InlineDataDecoder inlineDataDecoder) {
        super(startAddress, endianness, inlineDataDecoder);
        assert assembly != null;
        this.assembly = assembly;
        this.byteFields = new ImmediateOperandField[]{createByteField(0), createByteField(1), createByteField(2), createByteField(3)};
    }

    public RiscAssembly assembly() {
        return assembly;
    }

    private static final boolean INLINE_INVALID_INSTRUCTIONS_AS_BYTES = true;

    /**
     * Extract the value for each operand of a template from an encoded instruction whose opcode
     * matches that of the template.
     *
     * @param instruction  the encoded instruction
     * @return the decoded arguments for each operand or null if at least one operand has
     *         an invalid value in the encoded instruction
     */
    private List<Argument> disassemble(int instruction, RiscTemplate template) {
        final List<Argument> arguments = new ArrayList<Argument>();
        for (OperandField operandField : template.parameters()) {
            final Argument argument = operandField.disassemble(instruction);
            if (argument == null) {
                return null;
            }
            arguments.add(argument);
        }
        return arguments;
    }

    private boolean isLegalArgumentList(RiscTemplate template, List<Argument> arguments) {
        final List<InstructionConstraint> constraints = template.instructionDescription().constraints();
        for (InstructionConstraint constraint : constraints) {
            if (!(constraint.check(template, arguments))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a disassembled instruction based on a given sequence of bytes, a template and a set of arguments. The
     * caller has performed the necessary decoding of the bytes to derive the template and arguments.
     *
     * @param position the position an instruction stream from which the bytes were read
     * @param bytes the bytes of an instruction
     * @param template the template that corresponds to the instruction encoded in {@code bytes}
     * @param arguments the arguments of the instruction encoded in {@code bytes}
     * @return a disassembled instruction representing the result of decoding {@code bytes} into an instruction
     */
    protected abstract DisassembledInstruction createDisassembledInstruction(int position, byte[] bytes, RiscTemplate template, List<Argument> arguments);

    @Override
    public List<DisassembledObject> scanOne0(BufferedInputStream stream) throws IOException, AssemblyException {
        final int instruction = endianness().readInt(stream);
        final List<DisassembledObject> result = new LinkedList<DisassembledObject>();
        final byte[] instructionBytes = endianness().toBytes(instruction);
        for (SpecificityGroup specificityGroup : assembly().specificityGroups()) {
            for (OpcodeMaskGroup opcodeMaskGroup : specificityGroup.opcodeMaskGroups()) {
                final int opcode = instruction & opcodeMaskGroup.mask();
                for (RiscTemplate template : opcodeMaskGroup.templatesFor(opcode)) {
                    // Skip synthetic instructions when preference is for raw instructions,
                    // and skip instructions with a different number of arguments than requested if so (i.e. when running the AssemblyTester):
                    if (template != null && template.isDisassemblable() && ((abstractionPreference() == AbstractionPreference.SYNTHETIC) || !template.instructionDescription().isSynthetic())) {
                        final List<Argument> arguments = disassemble(instruction, template);
                        if (arguments != null && (expectedNumberOfArguments() < 0 || arguments.size() == expectedNumberOfArguments())) {
                            if (isLegalArgumentList(template, arguments)) {
                                final Assembler assembler = createAssembler(currentPosition);
                                try {
                                    assembly().assemble(assembler, template, arguments);
                                    final byte[] bytes = assembler.toByteArray();
                                    if (Arrays.equals(bytes, instructionBytes)) {
                                        final DisassembledInstruction disassembledInstruction = createDisassembledInstruction(currentPosition, bytes, template, arguments);
                                        result.add(disassembledInstruction);
                                    }
                                } catch (AssemblyException assemblyException) {
                                    ProgramWarning.message("could not assemble matching instruction: " + template);
                                }
                            }
                        }
                    }
                }
            }
        }
        if (result.isEmpty()) {
            if (INLINE_INVALID_INSTRUCTIONS_AS_BYTES) {
                stream.reset();
                final InlineData inlineData = new InlineData(currentPosition, instructionBytes);
                final DisassembledData disassembledData = createDisassembledDataObjects(inlineData).iterator().next();
                result.add(disassembledData);
            } else {
                throw new AssemblyException("instruction could not be disassembled: " + Bytes.toHexLiteral(endianness().toBytes(instruction)));
            }
        }
        currentPosition += 4;
        return result;
    }

    @Override
    public List<DisassembledObject> scan0(BufferedInputStream stream) throws IOException, AssemblyException {
        final List<DisassembledObject> result = new ArrayList<DisassembledObject>();
        try {
            while (true) {

                scanInlineData(stream, result);

                final List<DisassembledObject> disassembledObjects = scanOne(stream);
                boolean foundSyntheticDisassembledInstruction = false;
                if (abstractionPreference() == AbstractionPreference.SYNTHETIC) {
                    for (DisassembledObject disassembledObject : disassembledObjects) {
                        if (disassembledObject instanceof DisassembledInstruction) {
                            final DisassembledInstruction disassembledInstruction = (DisassembledInstruction) disassembledObject;
                            if (disassembledInstruction.template().instructionDescription().isSynthetic()) {
                                result.add(disassembledInstruction);
                                foundSyntheticDisassembledInstruction = true;
                                break;
                            }
                        }
                    }
                }
                if (!foundSyntheticDisassembledInstruction) {
                    result.add(Utils.first(disassembledObjects));
                }
            }
        } catch (IOException ioException) {
            return result;
        }
    }

    protected RiscTemplate createInlineDataTemplate(InstructionDescription instructionDescription) {
        return new RiscTemplate(instructionDescription);
    }

    private final ImmediateOperandField[] byteFields;

    private ImmediateOperandField createByteField(int index) {
        if (assembly().bitRangeEndianness() == BitRangeOrder.ASCENDING) {
            final int firstBit = index * Bytes.WIDTH;
            final int lastBit = firstBit + 7;
            return ImmediateOperandField.createAscending(firstBit, lastBit);
        }
        final int lastBit = index * Bytes.WIDTH;
        final int firstBit = lastBit + 7;
        return ImmediateOperandField.createDescending(firstBit, lastBit);
    }

    @Override
    public ImmediateArgument addressForRelativeAddressing(DisassembledInstruction di) {
        return di.startAddress();
    }

    @Override
    public String mnemonic(DisassembledInstruction di) {
        final RiscExternalInstruction instruction = new RiscExternalInstruction((RiscTemplate) di.template(), di.arguments(), di.startAddress(), null);
        return instruction.name();
    }

    @Override
    public String operandsToString(DisassembledInstruction di, AddressMapper addressMapper) {
        final RiscExternalInstruction instruction = new RiscExternalInstruction((RiscTemplate) di.template(), di.arguments(), di.startAddress(), addressMapper);
        return instruction.operands();
    }

    @Override
    public String toString(DisassembledInstruction di, AddressMapper addressMapper) {
        final RiscExternalInstruction instruction = new RiscExternalInstruction((RiscTemplate) di.template(), di.arguments(), di.startAddress(), addressMapper);
        return instruction.toString();
    }
}
