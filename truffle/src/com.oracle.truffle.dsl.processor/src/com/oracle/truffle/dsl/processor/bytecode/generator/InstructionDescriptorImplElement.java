/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Set;

import javax.lang.model.element.ElementKind;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class InstructionDescriptorImplElement extends AbstractElement {

    InstructionDescriptorImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "InstructionDescriptorImpl");
        this.setSuperClass(types.InstructionDescriptor);
    }

    void lazyInit() {
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "opcode"));

        CodeExecutableElement c = this.add(createConstructorUsingFields(Set.of(), this, null));
        CodeTree tree = c.getBodyTree();
        CodeTreeBuilder b = c.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.tree(tree);

        this.add(createGetLength());
        this.add(createGetName());
        this.add(createIsInstrumentation());
        this.add(createGetOperationCode());
        this.add(createGetArgumentDescriptors());

        CodeVariableElement descriptors = this.add(new CodeVariableElement(Set.of(STATIC, FINAL), arrayOf(asType()), "DESCRIPTORS"));
        descriptors.createInitBuilder().string("createDescriptors()");
        GeneratorUtils.addCompilationFinal(descriptors, 1);
        this.add(createCreateDescriptors());
    }

    private CodeExecutableElement createCreateDescriptors() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), arrayOf(asType()), "createDescriptors");
        CodeTreeBuilder b = ex.createBuilder();
        int endIndex = parent.model.getInstructionStartIndex() + parent.model.getInstructions().size();
        b.startDeclaration(arrayOf(asType()), "array").startNewArray(arrayOf(asType()), CodeTreeBuilder.singleString(String.valueOf(endIndex))).end().end();
        b.startFor().string("int opcode = " + parent.model.getInstructionStartIndex() + "; opcode < array.length; opcode++").end().startBlock();
        b.startStatement();
        b.string("array[opcode] = ").startNew(asType()).string("opcode").end();
        b.end();
        b.end();

        b.statement("return array");
        return ex;
    }

    private CodeExecutableElement createGetName() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstructionDescriptor, "getName");
        ex.getModifiers().add(FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().tree(parent.instructionsElement.call("getName", "opcode")).end();
        return ex;
    }

    private CodeExecutableElement createIsInstrumentation() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstructionDescriptor, "isInstrumentation");
        ex.getModifiers().add(FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().tree(parent.instructionsElement.call("isInstrumentation", "opcode")).end();
        return ex;
    }

    private CodeExecutableElement createGetLength() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstructionDescriptor, "getLength");
        ex.getModifiers().add(FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().tree(parent.instructionsElement.call("getLength", "opcode")).end();
        return ex;
    }

    private CodeExecutableElement createGetOperationCode() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstructionDescriptor, "getOperationCode");
        ex.getModifiers().add(FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("opcode").end();
        return ex;
    }

    private CodeExecutableElement createGetArgumentDescriptors() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstructionDescriptor, "getArgumentDescriptors");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startStaticCall(parent.instructionsElement.asType(), "getArgumentDescriptors").string("opcode").end();
        b.end();
        return ex;
    }
}
