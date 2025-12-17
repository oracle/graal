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

final class InstructionImplElement extends AbstractElement {

    InstructionImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "InstructionImpl");
    }

    void lazyInit() {
        this.setSuperClass(types.Instruction);

        this.add(new CodeVariableElement(Set.of(FINAL), parent.abstractBytecodeNode.asType(), "bytecode"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "bci"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "opcode"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(byte[].class), "bytecodes"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(Object[].class), "constants"));

        CodeExecutableElement constructor1 = this.add(createConstructorUsingFields(Set.of(), this, null));
        CodeTree tree = constructor1.getBodyTree();
        CodeTreeBuilder b = constructor1.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.tree(tree);

        CodeExecutableElement constructor2 = this.add(createConstructorUsingFields(Set.of(), this, null, Set.of("bytecodes", "constants")));
        tree = constructor2.getBodyTree();
        b = constructor2.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.tree(tree);
        b.statement("this.bytecodes = bytecode.bytecodes");
        b.statement("this.constants = bytecode.constants");

        this.add(createGetBytecodeIndex());
        this.add(createGetBytecodeNode());
        this.add(createGetArguments());
        this.add(createGetDescriptor());
        this.add(createNext());
    }

    private CodeExecutableElement createGetDescriptor() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getDescriptor");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.staticReference(parent.instructionDescriptorImpl.asType(), "DESCRIPTORS[opcode]");
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getBytecodeIndex");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return bci");
        return ex;
    }

    private CodeExecutableElement createNext() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "next");
        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(type(int.class), "nextBci", "getNextBytecodeIndex()");
        b.startIf().string("nextBci >= bytecode.bytecodes.length").end().startBlock();
        b.returnNull();
        b.end();
        b.startReturn().startNew(this.asType()).string("bytecode").string("nextBci").string("bytecode.readValidBytecode(bytecode.bytecodes, nextBci)").end().end();
        return ex;
    }

    private CodeExecutableElement createGetBytecodeNode() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getBytecodeNode");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return bytecode");
        return ex;
    }

    private CodeExecutableElement createGetArguments() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Instruction, "getArguments");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startStaticCall(parent.instructionsElement.asType(), "getArguments").string("opcode").string("bci").string("bytecode").string("this.bytecodes").string("this.constants").end();
        b.end();
        return ex;
    }

}
