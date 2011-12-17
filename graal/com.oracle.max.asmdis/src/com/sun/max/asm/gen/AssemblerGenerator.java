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
package com.sun.max.asm.gen;

import static com.sun.max.lang.Classes.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.max.*;
import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.ide.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;

/**
 * Source code generator for raw and label assembler methods derived from an ISA specification.
 */
public abstract class AssemblerGenerator<Template_Type extends Template> {

    protected OptionSet options = new OptionSet();

    public final Option<File> outputDirectoryOption = options.newFileOption("d", JavaProject.findSourceDirectory(AssemblerGenerator.class),
            "Source directory of the class(es) containing the for generated assembler methods.");
    public final Option<String> assemblerInterfaceNameOption = options.newStringOption("i", null,
            "Interface used to constrain which assembler methods will be generated. " +
            "If absent, an assembler method is generated for each template in the specification.");
    public final Option<String> rawAssemblerClassNameOption = options.newStringOption("r", null,
            "Class containing the generated raw assembler methods.");
    public final Option<String> labelAssemblerClassNameOption = options.newStringOption("l", null,
            "Class containing the generated label assembler methods.");
    public final Option<Boolean> generateRedundantInstructionsOption = options.newBooleanOption("redundant", true,
            "Generate assembler methods for redundant templates. Two templates are redundant if they " +
            "both have the same name and operands. Redundant pairs of instructions are assumed to " +
            "implement the same machine instruction semantics but may have different encodings.");

    private final Assembly<Template_Type> assembly;
    private final boolean sortAssemblerMethods;
    private List<Template_Type> templates;
    private List<Template_Type> labelTemplates;

    protected AssemblerGenerator(Assembly<Template_Type> assembly, boolean sortAssemblerMethods) {
        Trace.addTo(options);
        this.assembly = assembly;
        final String isa = assembly.isa().name();
        final String defaultOutputPackage = getPackageName(Assembler.class) + "." + isa.toLowerCase() + ".complete";
        this.rawAssemblerClassNameOption.setDefaultValue(defaultOutputPackage + "." + isa + "RawAssembler");
        this.labelAssemblerClassNameOption.setDefaultValue(defaultOutputPackage + "." + isa + "LabelAssembler");
        this.sortAssemblerMethods = sortAssemblerMethods;
    }

    public Assembly<Template_Type> assembly() {
        return assembly;
    }

    static class MethodKey {
        final String name;
        final Class[] parameterTypes;

        MethodKey(Method method) {
            name = method.getName();
            parameterTypes = method.getParameterTypes();
        }

        MethodKey(Template template, boolean asLabelTemplate) {
            name = template.assemblerMethodName();
            parameterTypes = template.parameterTypes();
            if (asLabelTemplate) {
                final int labelParameterIndex = template.labelParameterIndex();
                assert labelParameterIndex != -1;
                parameterTypes[labelParameterIndex] = Label.class;
            }
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof MethodKey) {
                final MethodKey other = (MethodKey) object;
                return other.name.equals(name) && Arrays.equals(parameterTypes, other.parameterTypes);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ parameterTypes.length;
        }

        @Override
        public String toString() {
            final String paramTypes = Arrays.toString(this.parameterTypes);
            return name + "(" + paramTypes.substring(1, paramTypes.length() - 1) + ")";
        }

    }

    private List<Template_Type> filterTemplates(List<Template_Type> templates) {
        if (!generateRedundantInstructionsOption.getValue()) {
            final List<Template_Type> result = new LinkedList<Template_Type>();
            for (Template_Type template : templates) {
                if (!template.isRedundant()) {
                    result.add(template);
                }
            }
            return result;
        }
        return templates;
    }

    /**
     * Initializes the set of label and raw templates that will be generated as assembler methods.
     * This includes doing any filtering out of templates based on an {@linkplain #assemblerInterfaceNameOption assembler interface}.
     */
    private void initTemplates() {
        assert (labelTemplates == null) == (templates == null);
        if (templates == null) {
            final String assemblerInterfaceName = assemblerInterfaceNameOption.getValue();
            if (assemblerInterfaceName == null) {
                templates = filterTemplates(assembly().templates());
                labelTemplates = filterTemplates(assembly().labelTemplates());
            } else {
                final List<Template_Type> newTemplates = new ArrayList<Template_Type>();
                final List<Template_Type> newLabelTemplates = new ArrayList<Template_Type>();

                Class assemberInterface = null;
                try {
                    assemberInterface = Class.forName(assemblerInterfaceName);
                    ProgramError.check(assemberInterface.isInterface(), "The class " + assemblerInterfaceName + " is not an interface");
                } catch (ClassNotFoundException e) {
                    throw ProgramError.unexpected("The assembler interface class " + assemblerInterfaceName + " must be on the class path");
                }
                final Set<MethodKey> assemblerInterfaceMethods = new HashSet<MethodKey>();
                for (Method assemblerInterfaceMethod : assemberInterface.getDeclaredMethods()) {
                    assemblerInterfaceMethods.add(new MethodKey(assemblerInterfaceMethod));
                }

                for (Template_Type labelTemplate : assembly().labelTemplates()) {
                    if (assemblerInterfaceMethods.contains(new MethodKey(labelTemplate, true))) {
                        assert labelTemplate.labelParameterIndex() != -1;
                        newLabelTemplates.add(labelTemplate);
                    }
                }

                for (Template_Type template : assembly().templates()) {
                    if (template.labelParameterIndex() != -1 && assemblerInterfaceMethods.contains(new MethodKey(template, true))) {
                        newTemplates.add(template);
                    } else if (assemblerInterfaceMethods.contains(new MethodKey(template, false))) {
                        newTemplates.add(template);
                    }
                }

                this.templates = newTemplates;
                this.labelTemplates = newLabelTemplates;

                Trace.line(1, "Based on " + assemberInterface + ", " + (assembly().templates().size() - newTemplates.size()) + " (of " + assembly().templates().size() + ") raw templates and " +
                              (assembly().labelTemplates().size() - newLabelTemplates.size()) + " (of " + assembly().labelTemplates().size() + ") label templates will be omitted from generated assembler methods");
            }

            if (sortAssemblerMethods) {
                Class<Template_Type[]> type = null;
                Template_Type[] sortedTemplates = Utils.cast(type, Array.newInstance(assembly().templateType(), templates.size()));
                Template_Type[] sortedLabelTemplates = Utils.cast(type, Array.newInstance(assembly().templateType(), labelTemplates.size()));
                templates.toArray(sortedTemplates);
                Arrays.sort(sortedTemplates);
                templates = Arrays.asList(sortedTemplates);
                labelTemplates.toArray(sortedLabelTemplates);
                Arrays.sort(sortedLabelTemplates);
                labelTemplates = Arrays.asList(sortedLabelTemplates);
            }
        }
    }

    protected final List<Template_Type> templates() {
        initTemplates();
        return templates;
    }

    protected final List<Template_Type> labelTemplates() {
        initTemplates();
        return labelTemplates;
    }

    /**
     * Gets the absolute path to the source file that will updated to include the generated assembler methods.
     *
     * @param className the name of the Java class that contains the generated assembler methods
     */
    private File getSourceFileFor(String className) {
        return new File(outputDirectoryOption.getValue(), className.replace('.', File.separatorChar) + ".java").getAbsoluteFile();
    }

    protected final String formatParameterList(String separator, List<? extends Parameter> parameters, boolean typesOnly) {
        String sep = separator;
        final StringBuilder sb = new StringBuilder();
        for (Parameter parameter : parameters) {
            sb.append(sep);
            sb.append(Classes.getSimpleName(parameter.type(), true));
            if (!typesOnly) {
                sb.append(" ");
                sb.append(parameter.variableName());
            }
            if (!sep.startsWith(", ")) {
                sep = ", " + sep;
            }
        }
        return sb.toString();
    }

    /**
     * Prints the source code for the raw assembler method for to a given template.
     *
     * @return the number of source code lines printed
     */
    protected abstract int printMethod(IndentWriter writer, Template_Type template);

    /**
     * Prints the source code for support methods that are used by the methods printed by {@link #printMethod(IndentWriter, Template)}.
     *
     * @return the number of subroutines printed
     */
    protected int printSubroutines(IndentWriter writer) {
        return 0;
    }

    /**
     * Gets the set of packages that must be imported for the generated code to compile successfully.
     *
     * @param className the name of the Java class that contains the assembler methods generated from {@code templates}
     * @param templateList the list of templates for which code is being generated
     * @return a set of packages sorted by name
     */
    public Set<String> getImportPackages(String className, Iterable<Template_Type> templateList) {
        final String outputPackage = getPackageName(className);
        final Set<String> packages = new TreeSet<String>();
        packages.add(getPackageName(AssemblyException.class));
        packages.add(getPackageName(Label.class));
        for (Template_Type template : templateList) {
            for (Parameter parameter : template.parameters()) {
                final Class type = parameter.type();
                if (!type.isPrimitive()) {
                    final String p = getPackageName(type);
                    if (!p.equals(outputPackage)) {
                        packages.add(p);
                    }
                }
            }
        }
        return packages;
    }

    /**
     * Prints the Javadoc comment for a template followed by a C++ style comment stating the template's number (it's
     * position in the order of emitted assembler methods) and its serial (a unique identifier given to every template).
     */
    protected void printMethodComment(IndentWriter writer, Template_Type template, int number, boolean forLabelAssemblerMethod) {
        printMethodJavadoc(writer, template, forLabelAssemblerMethod);
        writer.println("// Template#: " + number + ", Serial#: " + template.serial());
    }

    /**
     * Determines if a given label template should be omitted from assembler method generation.
     * This method is overridden by subclasses that may generate the code for 2 related label templates
     * in a single assembler method. For example, on X86 most branch instructions can take offsets of variable bit widths
     * and the logic for decoding the bit width of a {@link Label} value may be generated in a single assembler method.
     * <p>
     * The default implementation of this method returns {@code false}.
     */
    protected boolean omitLabelTemplate(Template_Type labelTemplate) {
        return false;
    }

    /**
     * Gets a reference to the architecture manual section describing the given template. The
     * returned string should conform to the format of the {@code @see} Javadoc tag.
     */
    protected String getJavadocManualReference(Template_Type template) {
        return null;
    }

    /**
     * Allows subclasses to print ISA specific details for a template. For example, RISC synthetic instructions
     * print what raw instruction they are derived from.
     *
     * @param extraLinks
     *                a sequence to which extra javadoc links should be appended
     */
    protected void printExtraMethodJavadoc(IndentWriter writer, Template_Type template, List<String> extraLinks, boolean forLabelAssemblerMethod) {
    }

    private boolean seenNoSuchAssemblerMethodError;

    /**
     * Writes the Javadoc comment for an assembler method.
     *
     * @param template the template from which the assembler method is generated
     */
    protected void printMethodJavadoc(IndentWriter writer, Template_Type template, boolean forLabelAssemblerMethod) {
        final List<String> extraLinks = new LinkedList<String>();
        final List<? extends Parameter> parameters = getParameters(template, forLabelAssemblerMethod);
        writer.println("/**");
        writer.println(" * Pseudo-external assembler syntax: {@code " + template.externalName() + externalMnemonicSuffixes(parameters) + "  }" + externalParameters(parameters));

        final boolean printExampleInstruction = true;
        if (printExampleInstruction) {

            final List<Argument> arguments = new ArrayList<Argument>();
            final AddressMapper addressMapper = new AddressMapper();
            for (Parameter p : template.parameters()) {
                final Argument exampleArg = p.getExampleArgument();
                if (exampleArg != null) {
                    arguments.add(exampleArg);
                } else {
                    break;
                }
            }
            if (arguments.size() == template.parameters().size()) {
                try {
                    final DisassembledInstruction instruction = generateExampleInstruction(template, arguments);
                    final ImmediateArgument targetAddress = instruction.targetAddress();

                    if (targetAddress != null) {
                        addressMapper.add(targetAddress, "L1");
                    }
                    final String exampleInstruction = instruction.toString(addressMapper);
                    writer.println(" * Example disassembly syntax: {@code " + exampleInstruction + "}");
                } catch (NoSuchAssemblerMethodError e) {
                    if (!seenNoSuchAssemblerMethodError) {
                        seenNoSuchAssemblerMethodError = true;
                        ProgramWarning.message("Once generated assembler has been compiled, re-generate it you want a usage example " +
                            "in the Javadoc for every generated assembler method");
                    }
                } catch (AssemblyException e) {
                    ProgramWarning.message("Error generating example instruction: " + e);
                }
            }
        }

        printExtraMethodJavadoc(writer, template, extraLinks, forLabelAssemblerMethod);
        final List<InstructionConstraint> constraints = new ArrayList<InstructionConstraint>(template.instructionDescription().specifications().size());
        for (Object s : template.instructionDescription().specifications()) {
            if (s instanceof InstructionConstraint) {
                constraints.add((InstructionConstraint) s);
            }
        }
        if (!constraints.isEmpty()) {
            writer.println(" * <p>");
            for (InstructionConstraint constraint : constraints) {
                final Method predicateMethod = constraint.predicateMethod();
                if (predicateMethod != null) {
                    extraLinks.add(predicateMethod.getDeclaringClass().getName() + "#" + predicateMethod.getName());
                }
                writer.println(" * Constraint: {@code " + constraint.asJavaExpression() + "}<br />");
            }
        }

        if (!extraLinks.isEmpty()) {
            writer.println(" *");
            for (String link : extraLinks) {
                writer.println(" * @see " + link);
            }
        }

        final String ref = getJavadocManualReference(template);
        if (ref != null) {
            writer.println(" *");
            writer.println(" * @see " + ref);
        }
        writer.println(" */");
    }

    protected abstract DisassembledInstruction generateExampleInstruction(Template_Type template, List<Argument> arguments) throws AssemblyException;

    private String externalParameters(List< ? extends Parameter> parameters) {
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Parameter parameter : parameters) {
            if (!ExternalMnemonicSuffixArgument.class.isAssignableFrom(parameter.type())) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append("<i>").append(parameter.variableName()).append("</i>");
                first = false;
            }
        }
        return sb.toString();
    }

    private String externalMnemonicSuffixes(List< ? extends Parameter> parameters) {
        final StringBuilder sb = new StringBuilder();
        for (Parameter parameter : parameters) {
            if (ExternalMnemonicSuffixArgument.class.isAssignableFrom(parameter.type())) {
                boolean first = true;
                String close = "]";
                for (Argument argument : parameter.getLegalTestArguments()) {
                    final String externalValue = argument.externalValue();
                    if (externalValue.length() != 0) {
                        if (!first) {
                            sb.append("|");
                        } else {
                            if (((ExternalMnemonicSuffixArgument) argument).isOptional()) {
                                sb.append("{");
                                close = "}";
                            } else {
                                sb.append("[");
                            }
                        }
                        sb.append(externalValue);
                        first = false;
                    }
                }
                sb.append(close);
            }
        }
        return sb.toString();
    }

    private boolean generateRawAssemblerMethods(String rawAssemblerClassName) throws IOException {
        Trace.line(1, "Generating raw assembler methods");
        final List<Template_Type> templateList = templates();
        final File sourceFile = getSourceFileFor(rawAssemblerClassName);
        ProgramError.check(sourceFile.exists(), "Source file for class containing raw assembler methods does not exist: " + sourceFile);
        final CharArraySource charArrayWriter = new CharArraySource((int) sourceFile.length());
        final IndentWriter writer = new IndentWriter(new PrintWriter(charArrayWriter));
        writer.indent();

        int codeLineCount = 0;
        final Map<InstructionDescription, Integer> instructionDescriptions = new HashMap<InstructionDescription, Integer>();
        int maxTemplatesPerDescription = 0;
        int i = 0;
        for (Template_Type template : templateList) {
            printMethodComment(writer, template, i + 1, false);
            codeLineCount += printMethod(writer, template);
            writer.println();

            Integer count = instructionDescriptions.get(template.instructionDescription());
            if (count == null) {
                count = 1;
            } else {
                count = count + 1;
            }
            if (count > maxTemplatesPerDescription) {
                maxTemplatesPerDescription = count;
            }
            instructionDescriptions.put(template.instructionDescription(), count);
            i++;
        }
        final int subroutineCount = printSubroutines(writer);

        writer.outdent();
        writer.close();

        Trace.line(1, "Generated raw assembler methods" +
                        " [code line count=" + codeLineCount + ", total line count=" + writer.lineCount() +
                        ", method count=" + (templateList.size() + subroutineCount) +
                        ", instruction templates=" + templateList.size() + ", max templates per description=" + maxTemplatesPerDescription +
                        "]");

        return Files.updateGeneratedContent(sourceFile, charArrayWriter, "// START GENERATED RAW ASSEMBLER METHODS", "// END GENERATED RAW ASSEMBLER METHODS", false);
    }

    /**
     * Gets the parameters for a template.
     *
     * @param forLabelAssemblerMethod
     *                if true and template contains a label parameter, then this parameter is represented as a
     *                {@link LabelParameter} object in the returned sequence
     */
    protected static List<Parameter> getParameters(Template template, boolean forLabelAssemblerMethod) {
        if (!forLabelAssemblerMethod || template.labelParameterIndex() == -1) {
            final Class<List<Parameter>> type = null;
            return Utils.cast(type, template.parameters());
        }
        final List<Parameter> parameters = new ArrayList<Parameter>(template.parameters());
        parameters.set(template.labelParameterIndex(), LabelParameter.LABEL);
        return parameters;
    }

    protected void printLabelMethodHead(IndentWriter writer, Template_Type template, List<Parameter> parameters) {
        writer.print("public void " + template.assemblerMethodName() + "(");
        writer.print(formatParameterList("final ", parameters, false));
        writer.println(") {");
        writer.indent();
    }

    /**
     * Prints an assembler method for a template that refers to an address via a {@linkplain Label label}.
     *
     * @param writer the writer to which code will be printed
     * @param labelTemplate a template that has a label parameter (i.e. its {@linkplain Template#labelParameterIndex()
     *            label parameter index} is not -1)
     * @param assemblerClassName the name of the class enclosing the assembler method declaration
     */
    protected abstract void printLabelMethod(IndentWriter writer, Template_Type labelTemplate, String assemblerClassName);

    /**
     * Mechanism that writes the body of the {@link MutableAssembledObject#assemble} method in a generated label method helper class.
     */
    public class InstructionWithLabelSubclass {

        final Class<? extends InstructionWithLabel> superClass;
        final String name;
        final String extraConstructorArguments;
        final String labelArgumentPrefix;

        public InstructionWithLabelSubclass(Template template, Class<? extends InstructionWithLabel> superClass, String extraConstructorArguments) {
            this.superClass = superClass;
            this.name = template.assemblerMethodName() + "_" + template.serial();
            this.extraConstructorArguments = extraConstructorArguments;
            final String labelType;
            if (superClass == InstructionWithAddress.class) {
                labelType = "address";
            } else if (superClass == InstructionWithOffset.class) {
                labelType = "offset";
            } else {
                throw ProgramError.unexpected("Unknown instruction with label type: " + superClass);
            }
            this.labelArgumentPrefix = labelType + "As";
        }

        /**
         * Prints the body of the {@link MutableAssembledObject#assemble} method in a label method helper class being
         * generated by a call to {@link AssemblerGenerator#printLabelMethodHelper}.
         * <p>
         * The default implementation generates a call to the raw assembler method generated for {@code template}
         *
         * @param writer
         * @param template
         */
        protected void printAssembleMethodBody(IndentWriter writer, Template template) {
            writer.print(template.assemblerMethodName() + "(");
            final List<? extends Parameter> parameters = template.parameters();
            String separator = "";
            int index = 0;
            final int labelParameterIndex = template.labelParameterIndex();
            final String labelArgument = labelArgumentPrefix + Strings.firstCharToUpperCase(parameters.get(labelParameterIndex).type().getName()) + "()";
            for (Parameter parameter : parameters) {
                writer.print(separator);
                if (index == labelParameterIndex) {
                    writer.print(labelArgument);
                } else {
                    writer.print(parameter.variableName());
                }
                separator = ", ";
                index++;
            }
            writer.println(");");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Prints the code that emits the place holder bytes for a label instruction before a value has been bound to the
     * label.
     *
     * @param writer
     * @param template
     * @param placeholderInstructionSize the number of place holder bytes written to the instruction stream before the
     *            label's value has been determined. If this value is -1, then the size depends on the arguments to the
     *            method and so a call to the raw assembler method is made to determine the size.
     * @return an expression denoting the number of place holder bytes emitted
     */
    private String printPlaceholderBytes(IndentWriter writer, Template_Type template, int placeholderInstructionSize) {
        if (placeholderInstructionSize == -1) {
            writer.println("final " + template.parameters().get(template.labelParameterIndex()).type() + " placeHolder = 0;");
            writer.print(template.assemblerMethodName() + "(");
            String separator = "";
            for (int i = 0; i < template.parameters().size(); i++) {
                writer.print(separator);
                if (i == template.labelParameterIndex()) {
                    writer.print("placeHolder");
                } else {
                    writer.print(template.parameters().get(i).variableName());
                }
                separator = ", ";
            }
            writer.println(");");
            return "currentPosition() - startPosition";
        }

        if (placeholderInstructionSize == 2) {
            writer.println("emitShort(0);");
        } else if (placeholderInstructionSize == 4) {
            writer.println("emitInt(0);");
        } else if (placeholderInstructionSize == 8) {
            writer.println("emitLong(0);");
        } else {
            writer.println("emitZeroes(" + placeholderInstructionSize + ");");
        }
        return String.valueOf(placeholderInstructionSize);
    }

    /**
     * Handles most of the work of {@link #printLabelMethod(IndentWriter, Template, String)}.
     *
     * @param writer the writer to which code will be printed
     * @param template a template that has a label parameter (i.e. its {@linkplain Template#labelParameterIndex() label
     *            parameter index} is not -1)
     * @param parameters the parameters of the template with the label parameter represented as a {@link LabelParameter}
     *            object
     * @param placeholderInstructionSize the number of place holder bytes written to the instruction stream before the
     *            label's value has been determined. If this value is -1, then the size depends on the arguments to the
     *            method and so a call to the raw assembler method is made to determine the size.
     * @param assemblerClassName the name of the class in which the assembler methods will be declared
     * @param labelInstructionSubclassGenerator the object that writes the body of the
     *            {@link MutableAssembledObject#assemble} method in a generated label method helper class
     */
    protected final void printLabelMethodHelper(IndentWriter writer,
                    Template_Type template,
                    List<Parameter> parameters,
                    int placeholderInstructionSize,
                    String assemblerClassName,
                    InstructionWithLabelSubclass labelInstructionSubclassGenerator) {
        assert template.labelParameterIndex() != -1;
        printLabelMethodHead(writer, template, parameters);
        writer.println("final int startPosition = currentPosition();");
        final String size = printPlaceholderBytes(writer, template, placeholderInstructionSize);
        writer.print("new " + labelInstructionSubclassGenerator.name + "(startPosition, " + size + ", ");
        for (Parameter parameter : parameters) {
            if (!(parameter instanceof LabelParameter)) {
                writer.print(parameter.variableName() + ", ");
            }
        }
        writer.println("label);");
        writer.outdent();
        writer.println("}");
        writer.println();

        final StringWriter stringWriter = new StringWriter();
        final IndentWriter indentWriter = new IndentWriter(new PrintWriter(stringWriter));
        indentWriter.indent();
        printLabelMethodHelperClass(
                        indentWriter,
                        template,
                        parameters,
                        assemblerClassName,
                        labelInstructionSubclassGenerator);
        labelMethodHelperClasses.add(stringWriter.toString());
    }

    private final List<String> labelMethodHelperClasses = new ArrayList<String>();

    private void printLabelMethodHelperClass(
                    IndentWriter writer,
                    Template_Type template,
                    List<Parameter> parameters,
                    String assemblerClassName,
                    InstructionWithLabelSubclass labelInstructionSubclass) {
        final String simpleAssemblerClassName = assemblerClassName.substring(assemblerClassName.lastIndexOf('.') + 1);
        writer.println("class " + labelInstructionSubclass + " extends " + labelInstructionSubclass.superClass.getSimpleName() + " {");
        writer.indent();
        String parametersDecl = "";
        for (Parameter parameter : parameters) {
            if (!(parameter instanceof LabelParameter)) {
                final Class parameterType = parameter.type();
                final String typeName = Classes.getSimpleName(parameterType, true);
                final String variableName = parameter.variableName();
                writer.println("private final " + typeName + " " + variableName + ";");
                parametersDecl = parametersDecl + typeName + " " + variableName + ", ";
            }
        }

        writer.println(labelInstructionSubclass + "(int startPosition, int endPosition, " + parametersDecl + "Label label) {");
        writer.indent();
        writer.println("super(" + simpleAssemblerClassName + ".this, startPosition, currentPosition(), label" + labelInstructionSubclass.extraConstructorArguments + ");");
        for (Parameter parameter : parameters) {
            if (!(parameter instanceof LabelParameter)) {
                final String variableName = parameter.variableName();
                writer.println("this." + variableName + " = " + variableName + ";");
            }
        }
        writer.outdent();
        writer.println("}");
        writer.println("@Override");
        writer.println("protected void assemble() throws AssemblyException {");
        writer.indent();
        labelInstructionSubclass.printAssembleMethodBody(writer, template);
        writer.outdent();
        writer.println("}");
        writer.outdent();
        writer.println("}");
        writer.println();
    }

    private boolean generateLabelAssemblerMethods(String labelAssemblerClassName) throws IOException {
        Trace.line(1, "Generating label assembler methods");
        final List<Template_Type> labelTemplateList = labelTemplates();
        final File sourceFile = getSourceFileFor(labelAssemblerClassName);
        ProgramError.check(sourceFile.exists(), "Source file for class containing label assembler methods does not exist: " + sourceFile);
        final CharArraySource charArrayWriter = new CharArraySource((int) sourceFile.length());
        final IndentWriter writer = new IndentWriter(new PrintWriter(charArrayWriter));
        writer.indent();

        int codeLineCount = 0;
        int i = 0;
        for (Template_Type labelTemplate : labelTemplateList) {
            if (!omitLabelTemplate(labelTemplate)) {
                printMethodComment(writer, labelTemplate, i + 1, true);
                final int startLineCount = writer.lineCount();
                printLabelMethod(writer, labelTemplate, labelAssemblerClassName);
                codeLineCount += writer.lineCount() - startLineCount;
                i++;
            }
        }
        writer.outdent();

        for (String labelMethodHelperClass : labelMethodHelperClasses) {
            writer.print(labelMethodHelperClass);
        }

        writer.close();

        Trace.line(1, "Generated label assembler methods" +
                      " [code line count=" + codeLineCount +
                      ", total line count=" + writer.lineCount() +
                      ", method count=" + templates().size() + ")");

        return Files.updateGeneratedContent(sourceFile, charArrayWriter, "// START GENERATED LABEL ASSEMBLER METHODS", "// END GENERATED LABEL ASSEMBLER METHODS", false);
    }

    protected void emitByte(IndentWriter writer, String byteValue) {
        writer.print("emitByte(" + byteValue + ");");
    }

    protected void emitByte(IndentWriter writer, byte value) {
        emitByte(writer, "((byte) " + Bytes.toHexLiteral(value) + ")");
    }

    protected void generate() {
        try {
            final String rawAssemblerClassName = rawAssemblerClassNameOption.getValue();
            final String labelAssemblerClassName = labelAssemblerClassNameOption.getValue();

            final boolean rawAssemblerMethodsUpdated = generateRawAssemblerMethods(rawAssemblerClassName);
            final boolean labelAssemblerMethodsUpdated = generateLabelAssemblerMethods(labelAssemblerClassName);

            if (rawAssemblerClassName.equals(labelAssemblerClassName)) {
                if (rawAssemblerMethodsUpdated || labelAssemblerMethodsUpdated) {
                    System.out.println("modified: " + getSourceFileFor(rawAssemblerClassName));
                    if (!ToolChain.compile(AssemblerGenerator.class, rawAssemblerClassName)) {
                        List<Template_Type> allTemplates = new ArrayList<Template_Type>(templates());
                        allTemplates.addAll(labelTemplates());
                        throw ProgramError.unexpected("compilation failed for: " + rawAssemblerClassName +
                                        "[Maybe missing an import statement for one of the following packages: " +
                                        getImportPackages(rawAssemblerClassName, allTemplates));
                    }
                } else {
                    System.out.println("unmodified: " + getSourceFileFor(rawAssemblerClassName));
                }
            } else {
                if (rawAssemblerMethodsUpdated) {
                    System.out.println("modified: " + getSourceFileFor(rawAssemblerClassName));
                    if (!ToolChain.compile(AssemblerGenerator.class, rawAssemblerClassName)) {
                        throw ProgramError.unexpected("compilation failed for: " + rawAssemblerClassName +
                                        "[Maybe missing an import statement for one of the following packages: " +
                                        getImportPackages(rawAssemblerClassName, templates()));
                    }
                } else {
                    System.out.println("unmodified: " + getSourceFileFor(rawAssemblerClassName));
                }

                if (labelAssemblerMethodsUpdated) {
                    System.out.println("modified: " + getSourceFileFor(labelAssemblerClassName));
                    if (!ToolChain.compile(AssemblerGenerator.class, labelAssemblerClassName)) {
                        throw ProgramError.unexpected("compilation failed for: " + labelAssemblerClassName +
                                        "[Maybe missing an import statement for one of the following packages: " +
                                        getImportPackages(labelAssemblerClassName, labelTemplates()));
                    }
                } else {
                    System.out.println("unmodified: " + getSourceFileFor(labelAssemblerClassName));
                }

            }

            Trace.line(1, "done");
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.err.println("something went wrong: " + throwable + ": " + throwable.getMessage());
        }
    }

}
