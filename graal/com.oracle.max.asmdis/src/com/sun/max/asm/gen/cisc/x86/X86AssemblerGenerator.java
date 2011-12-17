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
package com.sun.max.asm.gen.cisc.x86;

import java.io.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;

/**
 */
public abstract class X86AssemblerGenerator<Template_Type extends X86Template> extends AssemblerGenerator<Template_Type> {

    private final Option<Boolean> support16BitAddressesOption = options.newBooleanOption("a16", false, "Enables 16 bit addressing.");
    private final Option<Boolean> support16BitOffsetOption = options.newBooleanOption("d16", false, "Enables 16 bit offsets.");

    private final WordWidth addressWidth;

    protected X86AssemblerGenerator(Assembly<Template_Type> assembly, WordWidth addressWidth) {
        super(assembly, true);
        this.addressWidth = addressWidth;
    }

    @Override
    public X86Assembly<Template_Type> assembly() {
        final Class<X86Assembly<Template_Type>> type = null;
        return Utils.cast(type, super.assembly());
    }

    public WordWidth addressWidth() {
        return addressWidth;
    }

    @Override
    protected void generate() {
        if (support16BitAddressesOption.getValue() != null && support16BitAddressesOption.getValue()) {
            X86Assembly.support16BitAddresses();
        }
        if (support16BitOffsetOption.getValue() != null && support16BitOffsetOption.getValue()) {
            X86Assembly.support16BitOffsets();
        }
        super.generate();
    }

    protected X86Parameter getParameter(Template_Type template, Class parameterType) {
        for (X86Parameter parameter : template.parameters()) {
            if (parameter.type() == parameterType) {
                return parameter;
            }
        }
        throw ProgramError.unexpected("found no parameter of type: " + parameterType);
    }

    private void printCallWithByteDisplacement(IndentWriter writer, Template_Type template, Class argumentType) {
        final Template_Type modVariantTemplate = X86Assembly.getModVariantTemplate(assembly().templates(), template, argumentType);
        final String subroutineName = makeSubroutine(modVariantTemplate);
        writer.print(subroutineName + "(");
        if (template.opcode2() != null) {
            writer.print(OPCODE2_VARIABLE_NAME);
        } else {
            writer.print(OPCODE1_VARIABLE_NAME);
        }
        if (template.modRMGroupOpcode() != null) {
            writer.print(", " + MODRM_GROUP_OPCODE_VARIABLE_NAME);
        }
        for (X86Parameter parameter : template.parameters()) {
            if (parameter.type() == argumentType) {
                writer.print(", (byte) 0");
            }
            writer.print(", " + parameter.variableName());
        }
        writer.println(");");
    }

    protected String asIdentifier(EnumerableArgument argument) {
        return argument.getClass().getSimpleName() + "." + argument.name();
    }

    protected <Argument_Type extends Enum<Argument_Type> & EnumerableArgument<Argument_Type>> void printModVariant(IndentWriter writer, final Template_Type template, Argument_Type... arguments) {
        final Class argumentType = arguments[0].getClass();
        final X86Parameter parameter = getParameter(template, argumentType);
        writer.print("if (");
        String separator = "";
        for (EnumerableArgument argument : arguments) {
            writer.print(separator + parameter.variableName() + " == " + asIdentifier(argument));
            separator = " || ";
        }
        writer.println(") {");
        writer.indent();
        printCallWithByteDisplacement(writer, template, argumentType);
        writer.println("return;");
        writer.outdent();
        writer.println("}");
    }

    protected abstract void printModVariants(IndentWriter writer, Template_Type template);

    protected void printPrefixes(IndentWriter writer, Template_Type template) {
        if (template.addressSizeAttribute() != addressWidth()) {
            emitByte(writer, X86Opcode.ADDRESS_SIZE.byteValue());
            writer.println(" // address size prefix");
        }
        if (template.operandSizeAttribute() == WordWidth.BITS_16) {
            emitByte(writer, X86Opcode.OPERAND_SIZE.byteValue());
            writer.println(" // operand size prefix");
        }
        if (template.instructionSelectionPrefix() != null) {
            emitByte(writer, template.instructionSelectionPrefix().byteValue());
            writer.println(" // instruction selection prefix");
        }
    }

    private void printOpcode(IndentWriter writer, Template_Type template, String opcodeVarName, final ParameterPlace parameterPlace32, ParameterPlace parameterPlace64) {
        String comment = "";
        String opcodeVariableName = opcodeVarName;
        for (X86Parameter parameter : template.parameters()) {
            if (parameter.place() == parameterPlace32) {
                opcodeVariableName += " + " + parameter.valueString();
                comment = " // " + parameterPlace32.name().toLowerCase();
            } else if (parameter.place() == parameterPlace64) {
                opcodeVariableName += " + (" + parameter.valueString() + "& 7)";
                comment = " // " + parameterPlace64.name().toLowerCase();
            }
        }
        if (comment.length() == 0) {
            emitByte(writer, opcodeVariableName);
            writer.println();
        } else {
            emitByte(writer, "(byte) (" + opcodeVariableName + ")");
            writer.println(comment);
        }
    }

    private static final String MODRM_BYTE_VARIABLE_NAME = "modRMByte";

    private void printModRMByte(IndentWriter writer, Template_Type template) {
        writer.print("byte " + MODRM_BYTE_VARIABLE_NAME + " = (byte) ((" + template.modCase().ordinal() + " << " + X86Field.MOD.shift() + ")");
        if (template.modRMGroupOpcode() != null) {
            writer.print(" | (" + MODRM_GROUP_OPCODE_VARIABLE_NAME + " << " + X86Field.REG.shift() + ")");
        }
        writer.print("); // mod field");
        if (template.modRMGroupOpcode() != null) {
            writer.print(", group opcode in reg field");
        }
        writer.println();
        switch (template.rmCase()) {
            case SIB:
            case SWORD:
            case SDWORD: {
                writer.println(MODRM_BYTE_VARIABLE_NAME + " |= " + template.rmCase().value() + " << " + X86Field.RM.shift() + "; // rm field");
                break;
            }
            default:
                break;
        }
        for (X86Parameter parameter : template.parameters()) {
            switch (parameter.place()) {
                case MOD_REG:
                case MOD_REG_REXR: {
                    writer.println(MODRM_BYTE_VARIABLE_NAME + " |= (" + parameter.valueString() + " & 7) << " + X86Field.REG.shift() + "; // reg field");
                    break;
                }
                case MOD_RM:
                case MOD_RM_REXB: {
                    writer.println(MODRM_BYTE_VARIABLE_NAME + " |= (" + parameter.valueString() + " & 7) << " + X86Field.RM.shift() + "; // rm field");
                    break;
                }
                default:
                    break;
            }
        }
        emitByte(writer, MODRM_BYTE_VARIABLE_NAME);
        writer.println();
    }

    private static final String SIB_BYTE_NAME = "sibByte";

    private void printSibByte(IndentWriter writer, Template_Type template) {
        writer.print("byte " + SIB_BYTE_NAME + " = ");
        if (template.sibBaseCase() == X86TemplateContext.SibBaseCase.SPECIAL) {
            writer.println("(byte) (5 << " + X86Field.BASE.shift() + "); // base field");
        } else {
            writer.println("(byte) 0;");
        }
        for (X86Parameter parameter : template.parameters()) {
            switch (parameter.place()) {
                case SIB_BASE:
                case SIB_BASE_REXB:
                    writer.println(SIB_BYTE_NAME + " |= (" + parameter.valueString() + " & 7) << " + X86Field.BASE.shift() + "; // base field");
                    break;
                case SIB_INDEX:
                case SIB_INDEX_REXX:
                    writer.println(SIB_BYTE_NAME + " |= (" + parameter.valueString() + " & 7) << " + X86Field.INDEX.shift() + "; // index field");
                    break;
                case SIB_SCALE:
                    writer.println(SIB_BYTE_NAME + " |= " + parameter.valueString() + " << " + X86Field.SCALE.shift() + "; // scale field");
                    break;
                default:
                    break;
            }
        }
        emitByte(writer, SIB_BYTE_NAME);
        writer.println();
    }

    protected <Argument_Type extends Enum<Argument_Type> & EnumerableArgument<Argument_Type>> void printSibVariant(IndentWriter writer, Template_Type template, Argument_Type... arguments) {
        final Class argumentType = arguments[0].getClass();
        final X86Parameter parameter = getParameter(template, argumentType);
        writer.print("if (");
        String separator = "";
        for (EnumerableArgument argument : arguments) {
            writer.print(separator + parameter.variableName() + " == " + asIdentifier(argument));
            separator = " || ";
        }
        writer.println(") {");
        writer.indent();
        emitByte(writer, (byte) 0x24);
        writer.println(" // SIB byte");
        writer.outdent();
        writer.println("}");
    }

    protected abstract void printSibVariants(IndentWriter writer, Template_Type template);

    private void printImmediateParameter(IndentWriter writer, X86NumericalParameter parameter) {
        if (parameter.width() == WordWidth.BITS_8) {
            emitByte(writer, parameter.variableName());
            writer.println(" // appended");
        } else {
            writer.println("// appended:");
            for (int i = 0; i < parameter.width().numberOfBytes; i++) {
                if (i > 0) {
                    writer.println(parameter.variableName() + " >>= 8;");
                }
                emitByte(writer, "(byte) (" + parameter.variableName() + " & 0xff)");
                writer.println();
            }
        }
    }

    private void printAppendedEnumerableParameter(IndentWriter writer, X86EnumerableParameter parameter) {
        emitByte(writer, "(byte) " + parameter.variableName() + ".value()");
        writer.println(" // appended");
    }

    private void printAppendedParameter(IndentWriter writer, Template_Type template) {
        for (X86Parameter parameter : template.parameters()) {
            if (parameter.place() == ParameterPlace.APPEND) {
                if (parameter instanceof X86NumericalParameter) {
                    printImmediateParameter(writer, (X86NumericalParameter) parameter);
                } else if (parameter instanceof X86EnumerableParameter) {
                    printAppendedEnumerableParameter(writer, (X86EnumerableParameter) parameter);
                } else {
                    throw ProgramError.unexpected("appended parameter of unexpected type: " + parameter);
                }
            }
        }
    }

    private int subroutineSerial;

    private String createSubroutineName() {
        ++subroutineSerial;
        String number = Integer.toString(subroutineSerial);
        while (number.length() < 4) {
            number = "0" + number;
        }
        return "assemble" + number;
    }

    private Map<String, String> subroutineToName = new HashMap<String, String>();

    private static final String OPCODE1_VARIABLE_NAME = "opcode1";
    private static final String OPCODE2_VARIABLE_NAME = "opcode2";
    private static final String MODRM_GROUP_OPCODE_VARIABLE_NAME = "modRmOpcode";

    private void printSubroutine(IndentWriter writer, Template_Type template) {
        writer.print("(byte ");
        if (template.opcode2() != null) {
            writer.print(OPCODE2_VARIABLE_NAME);
        } else {
            writer.print(OPCODE1_VARIABLE_NAME);
        }
        if (template.modRMGroupOpcode() != null) {
            writer.print(", byte " + MODRM_GROUP_OPCODE_VARIABLE_NAME);
        }
        writer.print(formatParameterList(", ", template.parameters(), false));
        writer.println(") {");
        writer.indent();
        writer.indent();
        printModVariants(writer, template);
        printPrefixes(writer, template);
        if (template.opcode2() != null) {
            emitByte(writer, "(byte) (" + Bytes.toHexLiteral(template.opcode1().byteValue()) + ")");
            writer.println(" // " + OPCODE1_VARIABLE_NAME);
            printOpcode(writer, template, OPCODE2_VARIABLE_NAME, ParameterPlace.OPCODE2, ParameterPlace.OPCODE2_REXB);
        } else {
            printOpcode(writer, template, OPCODE1_VARIABLE_NAME, ParameterPlace.OPCODE1, ParameterPlace.OPCODE1_REXB);
        }
        if (template.hasModRMByte()) {
            printModRMByte(writer, template);
            if (template.hasSibByte()) {
                printSibByte(writer, template);
            } else {
                printSibVariants(writer, template);
            }
        }
        printAppendedParameter(writer, template);
        writer.outdent();
        writer.println("}");
        writer.outdent();
    }

    private String makeSubroutine(Template_Type template) {
        final StringWriter stringWriter = new StringWriter();
        printSubroutine(new IndentWriter(new PrintWriter(stringWriter)), template);
        final String subroutine = stringWriter.toString();
        String name = subroutineToName.get(subroutine);
        if (name == null) {
            name = createSubroutineName();
            subroutineToName.put(subroutine, name);
        }
        return name;
    }

    @Override
    protected int printMethod(IndentWriter writer, Template_Type template) {
        final int startLineCount = writer.lineCount();
        writer.print("public void ");
        writer.print(template.assemblerMethodName() + "(");
        writer.print(formatParameterList("", template.parameters(), false));
        writer.println(") {");
        writer.indent();
        final String subroutineName = makeSubroutine(template);
        writer.print(subroutineName + "(");
        if (template.opcode2() != null) {
            writer.print("(byte) " + Bytes.toHexLiteral(template.opcode2().byteValue()));
        } else {
            writer.print("(byte) " + Bytes.toHexLiteral(template.opcode1().byteValue()));
        }
        if (template.modRMGroupOpcode() != null) {
            writer.print(", (byte) " + Bytes.toHexLiteral(template.modRMGroupOpcode().byteValue()));
        }
        for (X86Parameter parameter : template.parameters()) {
            writer.print(", " + parameter.variableName());
        }
        writer.println(");");
        writer.outdent();
        writer.println("}");
        return writer.lineCount() - startLineCount;
    }

    @Override
    protected int printSubroutines(IndentWriter writer) {
        final Set<String> subroutineSet = subroutineToName.keySet();
        final String[] subroutines = subroutineSet.toArray(new String[subroutineSet.size()]);
        for (int i = 0; i < subroutines.length; i++) {
            subroutines[i] = subroutineToName.get(subroutines[i]) + subroutines[i];
        }
        java.util.Arrays.sort(subroutines);
        for (String subroutine : subroutines) {
            writer.print("private void " + subroutine);
            writer.println();
        }
        return subroutines.length;
    }

    private boolean parametersMatching(Template_Type candidate, Template_Type original) {
        if (candidate.parameters().size() != original.parameters().size()) {
            return false;
        }
        for (int i = 0; i < candidate.parameters().size(); i++) {
            if (i == original.labelParameterIndex()) {
                assert candidate.parameters().get(i).getClass() == X86OffsetParameter.class || candidate.parameters().get(i).getClass() == X86AddressParameter.class;
                assert candidate.parameters().get(i).getClass() == original.parameters().get(i).getClass();
            } else if (candidate.parameters().get(i).type() != original.parameters().get(i).type()) {
                return false;
            }
        }
        return true;
    }

    private final class LabelWidthCase {
        final WordWidth width;
        final Template_Type template;

        private LabelWidthCase(WordWidth width, Template_Type template) {
            this.width = width;
            this.template = template;
        }
    }

    private String getValidSizesMaskExpression(List<LabelWidthCase> labelWidthCases) {
        final Iterator<LabelWidthCase> iterator = labelWidthCases.iterator();
        String mask = String.valueOf(iterator.next().width.numberOfBytes);
        while (iterator.hasNext()) {
            mask += " | " + iterator.next().width.numberOfBytes;
        }
        return mask;
    }

    private List<LabelWidthCase> getRelatedLabelTemplatesByWidth(Template_Type template) {
        final List<LabelWidthCase> array = Utils.newArrayAsList(WordWidth.VALUES.size());
        for (Template_Type t : labelTemplates()) {
            if (t.assemblerMethodName().equals(template.assemblerMethodName()) && t.labelParameterIndex() == template.labelParameterIndex() && parametersMatching(t, template)) {
                final X86NumericalParameter numericalParameter = (X86NumericalParameter) t.parameters().get(template.labelParameterIndex());
                final WordWidth width = numericalParameter.width();
                array.set(width.ordinal(), new LabelWidthCase(width, t));
                t.isLabelMethodWritten = true;
            }
        }

        // Report the found cases in the order of ascending width:
        final List<LabelWidthCase> result = new LinkedList<LabelWidthCase>();
        for (int i = 0; i < array.size(); i++) {
            final LabelWidthCase labelWidthCase = array.get(i);
            if (labelWidthCase != null) {
                assert result.isEmpty() || labelWidthCase.width.greaterThan(Utils.last(result).width);
                result.add(labelWidthCase);
            }
        }
        assert result.size() > 0;
        return result;
    }

    private void printOffsetLabelMethod(final IndentWriter indentWriter,
                    Template_Type template,
                    final List<Parameter> parameters,
                    String assemblerClassName) {
        final List<LabelWidthCase> labelWidthCases = getRelatedLabelTemplatesByWidth(template);
        final InstructionWithLabelSubclass labelInstructionSubclass = new InstructionWithLabelSubclass(template, InstructionWithOffset.class, ", " + getValidSizesMaskExpression(labelWidthCases)) {
            @Override
            public void printAssembleMethodBody(IndentWriter writer, Template t) {
                if (labelWidthCases.size() == 1) {
                    final LabelWidthCase labelWidthCase = Utils.first(labelWidthCases);
                    super.printAssembleMethodBody(writer, labelWidthCase.template);
                } else {
                    writer.println("final int labelSize = labelSize();");
                    String prefix = "";
                    for (LabelWidthCase labelWidthCase : labelWidthCases) {
                        writer.println(prefix + "if (labelSize == " + labelWidthCase.width.numberOfBytes + ") {");
                        writer.indent();
                        super.printAssembleMethodBody(writer, labelWidthCase.template);
                        writer.outdent();
                        prefix = "} else ";
                    }
                    writer.println(prefix + "{");
                    writer.println("    throw new " + AssemblyException.class.getSimpleName() + "(\"Unexpected label width: \" + labelSize);");
                    writer.println("}");
                }
            }
        };

        printLabelMethodHelper(indentWriter,
                        template,
                        parameters,
                        -1,
                        assemblerClassName,
                        labelInstructionSubclass);
    }

    private void printAddressLabelMethod(
                    final IndentWriter indentWriter,
                    final Template_Type template,
                    final List<Parameter> parameters,
                    String assemblerClassName) {
        final InstructionWithLabelSubclass labelInstructionSubclass = new InstructionWithLabelSubclass(template, InstructionWithAddress.class, "");
        printLabelMethodHelper(indentWriter,
                        template,
                        parameters,
                        -1,
                        assemblerClassName,
                        labelInstructionSubclass);
    }

    @Override
    protected boolean omitLabelTemplate(Template_Type labelTemplate) {
        return labelTemplate.isLabelMethodWritten;
    }

    @Override
    protected void printLabelMethod(IndentWriter writer, Template_Type labelTemplate, String assemblerClassName) {
        if (labelTemplate.addressSizeAttribute() == addressWidth()) {
            if (!labelTemplate.isLabelMethodWritten) {
                labelTemplate.isLabelMethodWritten = true;
                final List<Parameter> parameters = getParameters(labelTemplate, true);
                final X86Parameter parameter = labelTemplate.parameters().get(labelTemplate.labelParameterIndex());
                if (parameter instanceof X86OffsetParameter) {
                    printOffsetLabelMethod(writer, labelTemplate, parameters, assemblerClassName);
                } else {
                    printAddressLabelMethod(writer, labelTemplate, parameters, assemblerClassName);
                }
            }
        }
    }

    public static byte basicRexValue(X86Template template) {
        if (template.operandSizeAttribute() == WordWidth.BITS_64 && template.instructionDescription().defaultOperandSize() != WordWidth.BITS_64) {
            return (byte) (X86Opcode.REX_MIN.ordinal() + (1 << X86Field.REX_W_BIT_INDEX));
        }
        return (byte) X86Opcode.REX_MIN.ordinal();
    }
}
