/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class OperationModel implements PrettyPrintable {
    public enum OperationKind {
        ROOT,
        BLOCK,
        IF_THEN,
        IF_THEN_ELSE,
        CONDITIONAL,
        WHILE,
        TRY_CATCH,
        FINALLY_TRY,
        FINALLY_TRY_NO_EXCEPT,
        SOURCE,
        SOURCE_SECTION,
        TAG,

        LABEL,
        BRANCH,
        RETURN,
        YIELD,

        LOAD_CONSTANT,
        LOAD_ARGUMENT,
        LOAD_LOCAL,
        LOAD_LOCAL_MATERIALIZED,
        STORE_LOCAL,
        STORE_LOCAL_MATERIALIZED,

        CUSTOM,
        CUSTOM_SHORT_CIRCUIT,
        CUSTOM_INSTRUMENTATION,
    }

    public record OperationArgument(TypeMirror type, String name, String doc) {
        public CodeVariableElement toVariableElement() {
            return new CodeVariableElement(type, name);
        }

        public String toJavadocParam() {
            return String.format("@param %s %s.", name, doc);
        }
    }

    private static final OperationArgument[] EMPTY_ARGUMENTS = new OperationArgument[0];

    public final BytecodeDSLModel parent;
    public final int id;
    public final OperationKind kind;
    public final String name;

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
    /**
     * Internal operations are generated and used internally by the DSL. They should not be exposed
     * through the builder and should not be serialized.
     */
    public boolean isInternal;

    public boolean[] childrenMustBeValues;
    public int numChildren;

    public InstructionModel instruction;
    public OperationArgument[] operationArguments = EMPTY_ARGUMENTS;
    public boolean operationArgumentVarArgs = false;

    public CustomOperationModel customModel;

    public int instrumentationIndex;

    public OperationModel(BytecodeDSLModel parent, int id, OperationKind kind, String name) {
        this.parent = parent;
        this.id = id;
        this.kind = kind;
        this.name = name;
    }

    public boolean hasChildren() {
        return isVariadic || numChildren > 0;
    }

    public void setInstrumentationIndex(int instrumentationIndex) {
        this.instrumentationIndex = instrumentationIndex;
    }

    public OperationModel setTransparent(boolean isTransparent) {
        this.isTransparent = isTransparent;
        return this;
    }

    public boolean isTransparent() {
        return isTransparent;
    }

    public OperationModel setVoid(boolean isVoid) {
        this.isVoid = isVoid;
        return this;
    }

    public OperationModel setChildrenMustBeValues(boolean... childrenMustBeValues) {
        this.childrenMustBeValues = childrenMustBeValues;
        return this;
    }

    public OperationModel setAllChildrenMustBeValues() {
        childrenMustBeValues = new boolean[numChildren];
        Arrays.fill(childrenMustBeValues, true);
        return this;
    }

    public OperationModel setVariadic(int minChildren) {
        this.isVariadic = true;
        this.numChildren = minChildren;
        return this;
    }

    public OperationModel setNumChildren(int numChildren) {
        this.numChildren = numChildren;
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

    public OperationModel setOperationArgumentVarArgs(boolean varArgs) {
        this.operationArgumentVarArgs = varArgs;
        return this;
    }

    public OperationModel setOperationArguments(OperationArgument... operationArguments) {
        if (this.operationArguments != null) {
            assert this.operationArguments.length == operationArguments.length;
        }
        this.operationArguments = operationArguments;
        return this;
    }

    public OperationModel setInternal() {
        this.isInternal = true;
        return this;
    }

    public String getOperationArgumentName(int i) {
        return operationArguments[i].name;
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

    public String getConstantName() {
        return name.toUpperCase();
    }

}
