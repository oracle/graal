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
package com.sun.max.asm.dis.x86;

import java.io.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.asm.amd64.*;
import com.sun.max.asm.dis.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.asm.x86.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.util.*;

/**
 * An x86 instruction disassembler.
 *
 * The string representation for disassembler output has the following format,
 * which borrows from both Intel and AT&T syntax and differs from either
 * regarding indirect addressing and indexing.
 *
 * Operand order follows Intel syntax:
 *
 * mnemonic argument
 * mnemonic destination, source
 * mnemonic argument1, argument2, argument3
 *
 * Some mnemonics may have operand size suffixes as in AT&T (gas) syntax.
 * Suffix    Intel size     Java size    # bits
 * ------    -----------    ---------    ------
 * b         byte           byte          8
 * w         word           short        16
 * l         long word      int          32
 * q         quad word      long         64
 *
 * Using this AT&T syntax feature, there is no need for operand size indicators
 * (e.g. DWORD PTR) for pointers as in Intel syntax.
 *
 * Registers etc. are named as in Intel syntax,
 * in lower case without AT&T's "%" prefix.
 *
 * Indexing is indicated by '[' and ']', similiar to array access in the Java(TM) Programming Language:
 *
 * base[index], e.g. eax[ebx]
 *
 * Indirect access looks like indexing without a base (or with implicit base 0):
 *
 * [indirect], e.g. [ecx]
 *
 * Displacements are added/subtracted from the index/indirect operand:
 *
 * base[index + displacement], e.g. ebp[eax - 12]
 * [indirect + displacement], e.g. [esi + 100]
 *
 * Scale is displayed as multiplication of the index:
 *
 * [base[index * scale] or base[index * scale + displacement], e.g. ecx[ebx * 4 + 10]
 *
 * A scale of 1 is left implicit, i.e. not printed.
 * Scale literals are the unsigned decimal integer numbers 2, 4, 8.
 *
 * Displacement literals are signed decimal integer numbers.
 *
 * Direct memory references (pointer literals) are unsigned hexadecimal integer numbers, e.g.:
 *
 * [0x12345678], 0x12345678[eax]
 *
 * Immediate operands are unsigned hexadecimal integer numbers, e.g.:
 *
 * 0x12, 0xffff, 0x0, 0x123456789abcdef
 *
 * Offset operands are signed decimal integer numbers, like displacements, but without space between the sign and the number, e.g.:
 *
 * jmp +12
 * call -2048
 *
 * RIP (Relative to Instruction Pointer) addressing is a combination of an offset operand and indirect addressing, e.g.:
 *
 * add [+20], eax
 * mov ebx, [-200]
 *
 * The disassembler displays synthetic labels for all target addresses
 * within the disassembled address range that hit the start address of an instruction.
 * Operands that coincide with such a label are displayed with the respective Label prepended. e.g.:
 *
 * jmp L1: +100
 * adc [L2: +128], ESI
 *
 * @see Disassembler
 * @see X86DisassembledInstruction
 */
public abstract class X86Disassembler extends Disassembler {

    private X86Assembly<? extends X86Template> assembly;

    protected X86Disassembler(ImmediateArgument startAddress, X86Assembly<? extends X86Template> assembly, InlineDataDecoder inlineDataDecoder) {
        super(startAddress, Endianness.LITTLE, inlineDataDecoder);
        this.assembly = assembly;
    }

    protected abstract boolean isRexPrefix(HexByte opcode);

    private X86InstructionHeader scanInstructionHeader(BufferedInputStream stream, boolean justSkip) throws IOException {
        int byteValue = stream.read();
        if (byteValue < 0) {
            return null;
        }
        final X86InstructionHeader header = new X86InstructionHeader();

        do {
            final HexByte hexByte = HexByte.VALUES.get(byteValue);
            if (header.opcode1 == null) {
                if (hexByte == X86Opcode.ADDRESS_SIZE) {
                    header.hasAddressSizePrefix = true;
                } else if (hexByte == X86Opcode.OPERAND_SIZE) {
                    if (header.instructionSelectionPrefix != null) {
                        return X86InstructionHeader.INVALID;
                    }
                    header.instructionSelectionPrefix = hexByte;
                } else if (hexByte == X86Opcode.REPE || hexByte == X86Opcode.REPNE) {
                    if (header.instructionSelectionPrefix != null) {
                        return X86InstructionHeader.INVALID;
                    }
                    header.instructionSelectionPrefix = hexByte;
                } else if (isRexPrefix(hexByte)) {
                    header.rexPrefix = hexByte;
                } else {
                    header.opcode1 = hexByte;
                    if (hexByte != HexByte._0F) {
                        break;
                    }
                }
            } else {
                header.opcode2 = hexByte;
                break;
            }
            byteValue = stream.read();
        } while (byteValue >= 0);

        if (TRACE && !justSkip) {
            System.out.println("Scanned header: " + header);
        }

        return justSkip ? null : header;
    }

    private List<Argument> scanArguments(BufferedInputStream stream, X86Template template, X86InstructionHeader header, byte modRMByte, byte sibByte) throws IOException {
        final List<Argument> arguments = new ArrayList<Argument>();
        final byte rexByte = (header.rexPrefix != null) ? header.rexPrefix.byteValue() : 0;
        for (X86Parameter parameter : template.parameters()) {
            int value = 0;
            switch (parameter.place()) {
                case MOD_REG_REXR:
                    value = X86Field.extractRexValue(X86Field.REX_R_BIT_INDEX, rexByte);
                    // fall through...
                case MOD_REG:
                    value += X86Field.REG.extract(modRMByte);
                    break;
                case MOD_RM_REXB:
                    value = X86Field.extractRexValue(X86Field.REX_B_BIT_INDEX, rexByte);
                    // fall through...
                case MOD_RM:
                    value += X86Field.RM.extract(modRMByte);
                    break;
                case SIB_BASE_REXB:
                    value = X86Field.extractRexValue(X86Field.REX_B_BIT_INDEX, rexByte);
                    // fall through...
                case SIB_BASE:
                    value += X86Field.BASE.extract(sibByte);
                    break;
                case SIB_INDEX_REXX:
                    value = X86Field.extractRexValue(X86Field.REX_X_BIT_INDEX, rexByte);
                    // fall through...
                case SIB_INDEX:
                    value += X86Field.INDEX.extract(sibByte);
                    break;
                case SIB_SCALE:
                    value = X86Field.SCALE.extract(sibByte);
                    break;
                case APPEND:
                    if (parameter instanceof X86EnumerableParameter) {
                        final X86EnumerableParameter enumerableParameter = (X86EnumerableParameter) parameter;
                        final Enumerator enumerator = enumerableParameter.enumerator();
                        arguments.add((Argument) enumerator.fromValue(endianness().readByte(stream)));
                        continue;
                    }
                    final X86NumericalParameter numericalParameter = (X86NumericalParameter) parameter;
                    switch (numericalParameter.width()) {
                        case BITS_8:
                            arguments.add(new Immediate8Argument(endianness().readByte(stream)));
                            break;
                        case BITS_16:
                            arguments.add(new Immediate16Argument(endianness().readShort(stream)));
                            break;
                        case BITS_32:
                            arguments.add(new Immediate32Argument(endianness().readInt(stream)));
                            break;
                        case BITS_64:
                            arguments.add(new Immediate64Argument(endianness().readLong(stream)));
                            break;
                    }
                    continue;
                case OPCODE1_REXB:
                    value = X86Field.extractRexValue(X86Field.REX_B_BIT_INDEX, rexByte);
                    // fall through...
                case OPCODE1:
                    value += header.opcode1.ordinal() & 7;
                    break;
                case OPCODE2_REXB:
                    value = X86Field.extractRexValue(X86Field.REX_B_BIT_INDEX, rexByte);
                    // fall through...
                case OPCODE2:
                    value += header.opcode2.ordinal() & 7;
                    break;
            }
            final X86EnumerableParameter enumerableParameter = (X86EnumerableParameter) parameter;
            final Enumerator enumerator = enumerableParameter.enumerator();
            if (enumerator == AMD64GeneralRegister8.ENUMERATOR) {
                arguments.add(AMD64GeneralRegister8.fromValue(value, header.rexPrefix != null));
            } else {
                arguments.add((Argument) enumerator.fromValue(value));
            }
        }
        return arguments;
    }

    private int getModVariantParameterIndex(X86Template template, byte modRMByte, byte sibByte) {
        if (template.modCase() == X86TemplateContext.ModCase.MOD_0 && X86Field.MOD.extract(modRMByte) != X86TemplateContext.ModCase.MOD_0.value()) {
            switch (template.rmCase()) {
                case NORMAL: {
                    if (template.addressSizeAttribute() == WordWidth.BITS_16) {
                        if (X86Field.RM.extract(modRMByte) != X86TemplateContext.RMCase.SWORD.value()) {
                            return -1;
                        }
                    } else if (X86Field.RM.extract(modRMByte) != X86TemplateContext.RMCase.SDWORD.value()) {
                        return -1;
                    }
                    for (int i = 0; i < template.parameters().size(); i++) {
                        switch (template.parameters().get(i).place()) {
                            case MOD_RM_REXB:
                            case MOD_RM:
                                return i;
                            default:
                                break;
                        }
                    }
                    break;
                }
                case SIB: {
                    if (template.sibBaseCase() == X86TemplateContext.SibBaseCase.GENERAL_REGISTER && X86Field.BASE.extract(sibByte) == 5) {
                        for (int i = 0; i < template.parameters().size(); i++) {
                            switch (template.parameters().get(i).place()) {
                                case SIB_BASE_REXB:
                                case SIB_BASE:
                                    return i;
                                default:
                                    break;
                            }
                        }
                    }
                    break;
                }
                default: {
                    break;
                }
            }
        }
        return -1;
    }

    private byte getSibByte(BufferedInputStream stream, X86Template template, byte modRMByte) throws IOException {
        if (template.addressSizeAttribute() == WordWidth.BITS_16) {
            return 0;
        }
        if (template.hasSibByte()) {
            return endianness().readByte(stream);
        }
        if (template.hasModRMByte() && X86Field.RM.extract(modRMByte) == X86TemplateContext.RMCase.SIB.value() &&
                   X86Field.MOD.extract(modRMByte) != X86TemplateContext.ModCase.MOD_3.value()) {
            return endianness().readByte(stream);
        }
        return 0;
    }

    protected abstract Map<X86InstructionHeader, List<X86Template>> headerToTemplates();

    private static int serial;

    public DisassembledObject scanInstruction(BufferedInputStream stream, X86InstructionHeader header) throws IOException, AssemblyException {
        if (header != X86InstructionHeader.INVALID) {
            serial++;
            Trace.line(4, "instruction: " + serial);
            if (header.opcode1 != null) {
                boolean isFloatingPointEscape = false;
                if (X86Opcode.isFloatingPointEscape(header.opcode1)) {
                    final int byte2 = stream.read();
                    if (byte2 >= 0xC0) {
                        isFloatingPointEscape = true;
                        header.opcode2 = HexByte.VALUES.get(byte2);
                    }
                }
                final List<X86Template> templates = headerToTemplates().get(header);
                if (templates != null) {
                    for (X86Template template : templates) {
                        stream.reset();
                        scanInstructionHeader(stream, true);
                        if (isFloatingPointEscape) {
                            stream.read();
                        }
                        try {
                            byte modRMByte = 0;
                            byte sibByte = 0;
                            int modVariantParameterIndex = -1;
                            List<Argument> arguments = null;
                            if (template.hasModRMByte()) {
                                modRMByte = endianness().readByte(stream);
                                sibByte = getSibByte(stream, template, modRMByte);
                                modVariantParameterIndex = getModVariantParameterIndex(template, modRMByte, sibByte);
                                if (modVariantParameterIndex >= 0) {
                                    final X86Template modVariantTemplate = X86Assembly.getModVariantTemplate(templates, template, template.parameters().get(modVariantParameterIndex).type());
                                    arguments = scanArguments(stream, modVariantTemplate, header, modRMByte, sibByte);
                                }
                            }
                            if (arguments == null) {
                                arguments = scanArguments(stream, template, header, modRMByte, sibByte);
                            }
                            if (modVariantParameterIndex >= 0) {
                                final Immediate8Argument immediateArgument = (Immediate8Argument) arguments.get(modVariantParameterIndex);
                                if (immediateArgument.value() != 0) {
                                    continue;
                                }

                                // Remove the mod variant argument
                                final Argument modVariantArgument = arguments.get(modVariantParameterIndex);
                                final List<Argument> result = new ArrayList<Argument>();
                                for (Argument argument : arguments) {
                                    if (modVariantArgument != argument) {
                                        result.add(argument);
                                    }
                                }
                                arguments = result;
                            }
                            if (!(Utils.indexOfIdentical(arguments, null) != -1)) {
                                byte[] bytes;
                                if (true) {
                                    final Assembler assembler = createAssembler(currentPosition);
                                    try {
                                        assembly.assemble(assembler, template, arguments);
                                    } catch (AssemblyException e) {
                                        // try the next template
                                        continue;
                                    }
                                    bytes = assembler.toByteArray();
                                } else { // TODO: does not work yet
                                    final X86TemplateAssembler templateAssembler = new X86TemplateAssembler(template, addressWidth());
                                    bytes = templateAssembler.assemble(arguments);
                                }
                                if (bytes != null) {
                                    stream.reset();
                                    if (Streams.startsWith(stream, bytes)) {
                                        final DisassembledInstruction disassembledInstruction = createDisassembledInstruction(currentPosition, bytes, template, arguments);
                                        currentPosition += bytes.length;
                                        return disassembledInstruction;
                                    }
                                }
                            }
                        } catch (NoSuchAssemblerMethodError e) {
                            // Until the X86TemplateAssembler is complete, only templates for which a generated assembler
                            // method exists can be disassembled
                        } catch (IOException ioException) {
                            // this one did not work, so loop back up and try another template
                        }
                    }
                }
            }
            if (header.instructionSelectionPrefix == X86Opcode.REPE || header.instructionSelectionPrefix == X86Opcode.REPNE) {

                stream.reset();
                final int size = 1;
                final byte[] data = new byte[size];
                Streams.readFully(stream, data);

                final X86InstructionHeader prefixHeader = new X86InstructionHeader();
                prefixHeader.opcode1 = header.instructionSelectionPrefix;
                final List<X86Template> prefixTemplates = headerToTemplates().get(prefixHeader);
                final X86Template template = Utils.first(prefixTemplates);
                final byte[] bytes = new byte[]{header.instructionSelectionPrefix.byteValue()};
                List<Argument> empty = Collections.emptyList();
                final DisassembledInstruction disassembledInstruction = createDisassembledInstruction(currentPosition, bytes, template, empty);
                currentPosition++;
                return disassembledInstruction;
            }
        }
        if (INLINE_INVALID_INSTRUCTIONS_AS_BYTES) {
            stream.reset();
            final int size = 1;
            final byte[] data = new byte[size];
            Streams.readFully(stream, data);
            final InlineData inlineData = new InlineData(currentPosition, data);
            final DisassembledData disassembledData = createDisassembledDataObjects(inlineData).iterator().next();
            currentPosition += size;
            return disassembledData;
        }
        throw new AssemblyException("unknown instruction");
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
    protected X86DisassembledInstruction createDisassembledInstruction(int position, byte[] bytes, X86Template template, List<Argument> arguments) {
        return new X86DisassembledInstruction(this, position, bytes, template, arguments);
    }

    private static final int MORE_THAN_ANY_INSTRUCTION_LENGTH = 100;
    private static final boolean INLINE_INVALID_INSTRUCTIONS_AS_BYTES = true;

    @Override
    public List<DisassembledObject> scanOne0(BufferedInputStream stream) throws IOException, AssemblyException {
        final List<DisassembledObject> disassembledObjects = new ArrayList<DisassembledObject>();
        stream.mark(MORE_THAN_ANY_INSTRUCTION_LENGTH);
        final X86InstructionHeader header = scanInstructionHeader(stream, false);
        if (header == null) {
            throw new AssemblyException("unknown instruction");
        }
        disassembledObjects.add(scanInstruction(stream, header));
        return disassembledObjects;
    }

    @Override
    public List<DisassembledObject> scan0(BufferedInputStream stream) throws IOException, AssemblyException {
        final SortedSet<Integer> knownGoodCodePositions = new TreeSet<Integer>();
        final List<DisassembledObject> result = new ArrayList<DisassembledObject>();
        boolean processingKnownValidCode = true;

        while (true) {
            while (knownGoodCodePositions.size() > 0 && knownGoodCodePositions.first().intValue() < currentPosition) {
                knownGoodCodePositions.remove(knownGoodCodePositions.first());
            }

            scanInlineData(stream, result);

            stream.mark(MORE_THAN_ANY_INSTRUCTION_LENGTH);

            final X86InstructionHeader header = scanInstructionHeader(stream, false);
            if (header == null) {
                return result;
            }
            final DisassembledObject disassembledObject = scanInstruction(stream, header);

            if (knownGoodCodePositions.size() > 0) {
                final int firstKnownGoodCodePosition = knownGoodCodePositions.first().intValue();
                final int startPosition = disassembledObject.startPosition();
                if (firstKnownGoodCodePosition > startPosition && firstKnownGoodCodePosition < disassembledObject.endPosition()) {
                    // there is a known valid code location in the middle of this instruction - assume that it is an invalid instruction
                    stream.reset();
                    final int size = firstKnownGoodCodePosition - startPosition;
                    final byte[] data = new byte[size];
                    Streams.readFully(stream, data);
                    final InlineData inlineData = new InlineData(startPosition, data);
                    currentPosition += addDisassembledDataObjects(result, inlineData);
                    processingKnownValidCode = true;
                } else {
                    result.add(disassembledObject);
                    if (firstKnownGoodCodePosition == startPosition) {
                        processingKnownValidCode = true;
                    }
                }
            } else {
                if (processingKnownValidCode && disassembledObject instanceof DisassembledInstruction) {
                    final DisassembledInstruction disassembledInstruction = (DisassembledInstruction) disassembledObject;
                    if (isRelativeJumpForward(disassembledInstruction)) {
                        int jumpOffset;
                        if (Utils.first(disassembledInstruction.arguments()) instanceof Immediate32Argument) {
                            jumpOffset = ((Immediate32Argument) Utils.first(disassembledInstruction.arguments())).value();
                        } else {
                            assert Utils.first(disassembledInstruction.arguments()) instanceof Immediate8Argument;
                            jumpOffset = ((Immediate8Argument) Utils.first(disassembledInstruction.arguments())).value();
                        }
                        final int targetPosition = disassembledInstruction.endPosition() + jumpOffset;
                        knownGoodCodePositions.add(targetPosition);
                        processingKnownValidCode = false;
                    }
                }
                result.add(disassembledObject);
            }
        }
    }

    private boolean isRelativeJumpForward(DisassembledInstruction instruction) {
        return instruction.template().internalName().equals("jmp") && // check if this is a jump instruction...
            instruction.arguments().size() == 1 && // that accepts one operand...
            ((Utils.first(instruction.arguments()) instanceof Immediate32Argument && // which is a relative offset...
            ((Immediate32Argument) Utils.first(instruction.arguments())).value() >= 0) || // forward in the code stream
            (Utils.first(instruction.arguments()) instanceof Immediate8Argument && // which is a relative offset...
            ((Immediate8Argument) Utils.first(instruction.arguments())).value() >= 0)); // forward in the code stream
    }

    @Override
    public ImmediateArgument addressForRelativeAddressing(DisassembledInstruction di) {
        return startAddress().plus(di.endPosition());
    }

    @Override
    public String mnemonic(DisassembledInstruction di) {
        return di.template().externalName();
    }

    @Override
    public String operandsToString(DisassembledInstruction di, AddressMapper addressMapper) {
        final LinkedList<X86Operand> operandQueue = new LinkedList<X86Operand>();
        for (Operand operand : di.template().operands()) {
            operandQueue.add((X86Operand) operand);
        }
        final LinkedList<Argument> argumentQueue = new LinkedList<Argument>(di.arguments());
        String result = "";
        String separator = "";
        while (!operandQueue.isEmpty()) {
            result += separator + getOperand(di, operandQueue, argumentQueue, addressMapper);
            separator = ", ";
        }
        return result;
    }

    @Override
    public String toString(DisassembledInstruction di, AddressMapper addressMapper) {
        String s = operandsToString(di, addressMapper);
        if (s.length() > 0) {
            s = "  " + s;
        }
        return Strings.padLengthWithSpaces(mnemonic(di), 8) + s;
    }

    private String getSibIndexAndScale(Queue<X86Operand> operands, Queue<Argument> arguments) {
        X86Parameter parameter = (X86Parameter) operands.remove();
        assert parameter.place() == ParameterPlace.SIB_INDEX || parameter.place() == ParameterPlace.SIB_INDEX_REXX;
        final String result = arguments.remove().disassembledValue();
        parameter = (X86Parameter) operands.remove();
        assert parameter.place() == ParameterPlace.SIB_SCALE;
        final Scale scale = (Scale) arguments.remove();
        if (scale == Scale.SCALE_1) {
            return result;
        }
        return result + " * " + scale.disassembledValue();
    }

    private String addition(Argument argument, String space) {
        assert argument instanceof ImmediateArgument;
        final long value = argument.asLong();
        final String s = Long.toString(value);
        if (value >= 0) {
            return "+" + space + s;
        }
        return "-" + space + s.substring(1);
    }

    private String getOperand(DisassembledInstruction di, Queue<X86Operand> operands, Queue<Argument> arguments, AddressMapper addressMapper) {
        final X86Operand operand = operands.remove();
        if (operand instanceof ImplicitOperand) {
            final ImplicitOperand implicitOperand = (ImplicitOperand) operand;
            return implicitOperand.argument().disassembledValue();
        }
        final X86Parameter parameter = (X86Parameter) operand;
        final Argument argument = arguments.remove();
        if (parameter instanceof X86DisplacementParameter) {
            assert parameter.place() == ParameterPlace.APPEND;
            final X86Parameter nextParameter = (X86Parameter) operands.element();
            String prefix = "";
            if (IndirectRegister.class.isAssignableFrom(nextParameter.type())) {
                operands.remove();
                prefix += "[" + arguments.remove().disassembledValue();
            } else {
                if (nextParameter.place() == ParameterPlace.SIB_BASE || nextParameter.place() == ParameterPlace.SIB_BASE_REXB) {
                    operands.remove();
                    prefix += arguments.remove().disassembledValue();
                }
                prefix += "[" + getSibIndexAndScale(operands, arguments);
            }
            return prefix + " " + addition(argument, " ") + "]";
        }
        if (parameter.place() == ParameterPlace.SIB_BASE || parameter.place() == ParameterPlace.SIB_BASE_REXB) {
            return argument.disassembledValue() + "[" + getSibIndexAndScale(operands, arguments) + "]";
        }
        if (IndirectRegister.class.isAssignableFrom(parameter.type())) {
            return "[" + argument.disassembledValue() + "]";
        }
        if (parameter instanceof X86AddressParameter) {
            String address = argument.disassembledValue();
            final DisassembledLabel label = addressMapper.labelAt((ImmediateArgument) argument);
            if (label != null) {
                address = label.name() + ": " + address;
            }
            final X86Operand nextOperand = operands.peek();
            if (nextOperand instanceof X86Parameter) {
                final X86Parameter nextParameter = (X86Parameter) nextOperand;
                if (nextParameter.place() == ParameterPlace.SIB_INDEX || nextParameter.place() == ParameterPlace.SIB_INDEX_REXX) {
                    return address + "[" + getSibIndexAndScale(operands, arguments) + "]";
                }
            }
            return "[" + address + "]";
        }
        if (parameter instanceof X86OffsetParameter) {
            String offset = addition(argument, "");
            final ImmediateArgument targetAddress = di.addressForRelativeAddressing().plus((ImmediateArgument) argument);
            final DisassembledLabel label =  addressMapper.labelAt(targetAddress);
            if (label != null) {
                offset = label.name() + ": " + offset;
            }
            if (((X86Template) di.template()).addressSizeAttribute() == WordWidth.BITS_64 && ((X86Template) di.template()).rmCase() == X86TemplateContext.RMCase.SDWORD) {
                return "[" + offset + "]"; // RIP
            }
            return offset;
        }
        if (parameter.getClass() == X86ImmediateParameter.class) {
            return argument.disassembledValue();
        }
        return argument.disassembledValue();
    }
}
