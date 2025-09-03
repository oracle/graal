/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.List;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.bytecode.parser.CustomOperationParser;
import com.oracle.truffle.dsl.processor.bytecode.parser.SpecializationSignatureParser.SpecializationSignature;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

public class OperationModel implements PrettyPrintable {
    public enum OperationKind {
        ROOT,
        BLOCK,
        IF_THEN,
        IF_THEN_ELSE,
        CONDITIONAL,
        WHILE,
        TRY_CATCH,
        TRY_FINALLY,
        TRY_CATCH_OTHERWISE,
        FINALLY_HANDLER,
        SOURCE,
        SOURCE_SECTION,
        TAG,

        LABEL,
        BRANCH,
        RETURN,
        YIELD,

        LOAD_CONSTANT,
        LOAD_NULL,
        LOAD_ARGUMENT,
        LOAD_EXCEPTION,
        LOAD_LOCAL,
        LOAD_LOCAL_MATERIALIZED,
        STORE_LOCAL,
        STORE_LOCAL_MATERIALIZED,

        CUSTOM,
        CUSTOM_SHORT_CIRCUIT,
        CUSTOM_INSTRUMENTATION,
    }

    /**
     * Models an argument to a begin/emit/end method.
     */
    public record OperationArgument(TypeMirror builderType, TypeMirror constantType, Encoding kind, String name, String doc) {

        OperationArgument(TypeMirror builderType, Encoding kind, String name, String doc) {
            this(builderType, builderType, kind, name, doc);
        }

        public CodeVariableElement toVariableElement() {
            return new CodeVariableElement(builderType, name);
        }

        public String toJavadocParam() {
            String docPart = doc.isEmpty() ? "" : String.format(" %s.", doc);
            return String.format("@param %s%s", name, docPart);
        }

        /*
         * Encoding used for serialization.
         */
        public enum Encoding {
            LANGUAGE,
            SHORT,
            INTEGER,
            OBJECT,
            LOCAL,
            LOCAL_ARRAY,
            TAGS,
            LABEL,
            FINALLY_GENERATOR,
        }

    }

    private static final OperationArgument[] EMPTY_ARGUMENTS = new OperationArgument[0];

    /**
     * Models the constant operand data statically declared on the operation using ConstantOperand
     * annotations.
     */
    public record ConstantOperandsModel(List<ConstantOperandModel> before, List<ConstantOperandModel> after) {
        public static final ConstantOperandsModel NONE = new ConstantOperandsModel(List.of(), List.of());

        public boolean hasConstantOperands() {
            return this != NONE;
        }
    }

    public final BytecodeDSLModel parent;
    public final int id;
    public final OperationKind kind;
    public final String name;
    /*
     * Name used to generate builder methods.
     */
    public final String builderName;
    public final String javadoc;

    /**
     * Transparent operations do not have their own logic; any value produced by their children is
     * simply forwarded to the parent operation.
     *
     * e.g., blocks do not have their own logic, but are useful to support operation sequencing.
     * Source position-related operations are also transparent.
     */
    public boolean isTransparent;
    public boolean isVoid;
    public boolean isVariadic;
    public int variadicOffset = 0;
    public boolean variadicReturn;

    /**
     * Internal operations are generated and used internally by the DSL. They should not be exposed
     * through the builder and should not be serialized.
     */
    public boolean isInternal;

    public InstructionModel instruction;
    public CustomOperationModel customModel;

    // The constant operands parsed from {@code @ConstantOperand} annotations.
    public ConstantOperandsModel constantOperands = null;

    // Dynamic operand data supplied by builtin specs / parsed from operation specializations.
    public DynamicOperandModel[] dynamicOperands = new DynamicOperandModel[0];

    // Operand names parsed from operation specializations.
    public List<String> constantOperandBeforeNames;
    public List<String> constantOperandAfterNames;

    public OperationArgument[] operationBeginArguments = EMPTY_ARGUMENTS;
    public OperationArgument[] operationEndArguments = EMPTY_ARGUMENTS;
    public boolean operationBeginArgumentVarArgs = false;

    // A unique identifier for instrumentation instructions.
    public int instrumentationIndex;

    public OperationModel(BytecodeDSLModel parent, int id, OperationKind kind, String name, String builderName, String javadoc) {
        this.parent = parent;
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.builderName = builderName;
        this.javadoc = javadoc;
    }

    public int numConstantOperandsBefore() {
        if (constantOperands == null) {
            return 0;
        }
        return constantOperands.before.size();
    }

    public int numDynamicOperands() {
        return dynamicOperands.length;
    }

    public int numConstantOperandsAfter() {
        if (constantOperands == null) {
            return 0;
        }
        return constantOperands.after.size();
    }

    public boolean hasChildren() {
        return isVariadic || numDynamicOperands() > 0;
    }

    public void setInstrumentationIndex(int instrumentationIndex) {
        this.instrumentationIndex = instrumentationIndex;
    }

    public SpecializationSignature getSpecializationSignature(SpecializationData specialization) {
        return getSpecializationSignature(List.of(specialization));
    }

    public SpecializationSignature getSpecializationSignature(List<SpecializationData> specializations) {
        List<ExecutableElement> methods = specializations.stream().map(s -> s.getMethod()).toList();
        SpecializationSignature includedSpecializationSignatures = CustomOperationParser.parseSignatures(methods,
                        specializations.get(0).getNode(),
                        constantOperands).get(0);
        return includedSpecializationSignatures;
    }

    public OperationModel setTransparent(boolean isTransparent) {
        this.isTransparent = isTransparent;
        return this;
    }

    public OperationModel setVariadic(boolean isVariadic, int variadicOffset) {
        this.isVariadic = isVariadic;
        this.variadicOffset = variadicOffset;
        return this;
    }

    public boolean isTransparent() {
        return isTransparent;
    }

    public OperationModel setVoid(boolean isVoid) {
        this.isVoid = isVoid;
        return this;
    }

    public String getConstantOperandBeforeName(int i) {
        return constantOperandBeforeNames.get(i);
    }

    public String getConstantOperandAfterName(int i) {
        return constantOperandAfterNames.get(i);
    }

    public OperationModel setDynamicOperands(DynamicOperandModel... dynamicOperands) {
        this.dynamicOperands = dynamicOperands;
        return this;
    }

    public OperationModel setInstruction(InstructionModel instruction) {
        this.instruction = instruction;
        if (instruction.operation != null) {
            throw new AssertionError("operation already set");
        }
        instruction.operation = this;
        return this;
    }

    public OperationModel setOperationBeginArgumentVarArgs(boolean varArgs) {
        this.operationBeginArgumentVarArgs = varArgs;
        return this;
    }

    public OperationModel setOperationBeginArguments(OperationArgument... operationBeginArguments) {
        if (this.operationBeginArguments != null) {
            assert this.operationBeginArguments.length == operationBeginArguments.length;
        }
        this.operationBeginArguments = operationBeginArguments;
        return this;
    }

    public OperationModel setOperationEndArguments(OperationArgument... operationEndArguments) {
        if (this.operationEndArguments != null) {
            assert this.operationEndArguments.length == operationEndArguments.length;
        }
        this.operationEndArguments = operationEndArguments;
        return this;
    }

    public String getOperationBeginArgumentName(int i) {
        return operationBeginArguments[i].name;
    }

    public String getOperationEndArgumentName(int i) {
        return operationEndArguments[i].name;
    }

    public OperationModel setInternal() {
        this.isInternal = true;
        return this;
    }

    @Override
    public void pp(PrettyPrinter printer) {
        printer.print("Operation %s", name);
        printer.field("kind", kind);
    }

    public boolean isSourceOnly() {
        return kind == OperationKind.SOURCE || kind == OperationKind.SOURCE_SECTION;
    }

    public boolean isCustom() {
        return kind == OperationKind.CUSTOM || kind == OperationKind.CUSTOM_SHORT_CIRCUIT || kind == OperationKind.CUSTOM_INSTRUMENTATION;
    }

    public boolean requiresRootOperation() {
        return kind != OperationKind.SOURCE && kind != OperationKind.SOURCE_SECTION;
    }

    public boolean requiresStackBalancing() {
        return kind != OperationKind.TAG;
    }

    public String getConstantName() {
        return name.toUpperCase();
    }

    @Override
    public String toString() {
        return "OperationModel [id=" + id + ", kind=" + kind + ", name=" + name + "]";
    }

}
