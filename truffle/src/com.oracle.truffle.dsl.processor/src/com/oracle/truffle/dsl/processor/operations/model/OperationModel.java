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
package com.oracle.truffle.dsl.processor.operations.model;

import java.util.Arrays;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.model.MessageContainer;

public class OperationModel extends MessageContainer implements InfoDumpable {
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
        INSTRUMENT_TAG,

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

        CUSTOM_SIMPLE,
        CUSTOM_SHORT_CIRCUIT
    }

    private static final TypeMirror[] EMPTY_ARGUMENTS = new TypeMirror[0];

    public final OperationsModel parent;
    public final int id;
    public final OperationKind kind;
    public final String name;

    public final TypeElement templateType;
    public AnnotationMirror annotationMirror;

    public boolean isTransparent;
    public boolean isVoid;
    public boolean isVariadic;

    public boolean[] childrenMustBeValues;
    public int numChildren;

    public InstructionModel instruction;
    public TypeMirror[] operationArguments = EMPTY_ARGUMENTS;

    public OperationModel(OperationsModel parent, TypeElement templateType, int id, OperationKind kind, String name) {
        this.parent = parent;
        this.templateType = templateType;
        this.id = id;
        this.kind = kind;
        this.name = name;
    }

    public boolean hasChildren() {
        return isVariadic || numChildren > 0;
    }

    public OperationModel setTransparent(boolean isTransparent) {
        this.isTransparent = isTransparent;
        return this;
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
        return this;
    }

    public OperationModel setOperationArguments(TypeMirror... operationArguments) {
        this.operationArguments = operationArguments;
        return this;
    }

    @Override
    public Element getMessageElement() {
        return templateType;
    }

    @Override
    public AnnotationMirror getMessageAnnotation() {
        return annotationMirror;
    }

    @Override
    public MessageContainer getBaseContainer() {
        return parent;
    }

    public void dump(Dumper dumper) {
        dumper.print("Operation %s", name);
        dumper.field("kind", kind);
    }

    public boolean isSourceOnly() {
        return kind == OperationKind.SOURCE || kind == OperationKind.SOURCE_SECTION;
    }

    public String getConstantName() {
        return name.toUpperCase();
    }

}
