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
import static javax.lang.model.element.Modifier.VOLATILE;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class InstructionTracerAccessImplElement extends AbstractElement {

    InstructionTracerAccessImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "InstructionTracerAccessImpl");
        this.setSuperClass(types.InstructionTracer_InstructionAccess);
    }

    void lazyInit() {
        // private static final InstructionTracer[] EMPTY = new InstructionTracer[0];
        CodeVariableElement emptyArr = new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), arrayOf(types.InstructionTracer), "EMPTY");
        emptyArr.createInitBuilder().startNewArray(arrayOf(types.InstructionTracer), CodeTreeBuilder.singleString("0")).end();
        this.add(emptyArr);

        CodeExecutableElement c = this.add(createConstructorUsingFields(Set.of(), this, null));
        c.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        CodeTree tree = c.getBodyTree();
        CodeTreeBuilder b = c.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.tree(tree);

        CodeVariableElement localTracers = this.add(new CodeVariableElement(Set.of(VOLATILE), arrayOf(types.InstructionTracer), "localTracers"));
        CodeVariableElement globalTracers = this.add(new CodeVariableElement(Set.of(VOLATILE), arrayOf(types.InstructionTracer), "globalTracers"));

        GeneratorUtils.addCompilationFinal(localTracers, 1);
        GeneratorUtils.addCompilationFinal(globalTracers, 1);

        localTracers.createInitBuilder().string("EMPTY");
        globalTracers.createInitBuilder().string("EMPTY");

        this.add(createOnInstructionEnter());

        this.add(createAddLocalTracer());
        this.add(createRemoveLocalTracer());

        this.add(createUpdateGlobalTracers());

        this.add(createGetTracedInstruction());
        this.add(createGetTracedOperationCode());
    }

    private CodeExecutableElement createOnInstructionEnter() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(), type(void.class), "onInstructionEnter",
                        new CodeVariableElement(types.BytecodeNode, "bytecode"),
                        new CodeVariableElement(type(int.class), "bytecodeIndex"),
                        new CodeVariableElement(types.Frame, "frame"));
        ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
        ex.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));

        CodeTreeBuilder b = ex.createBuilder();

        b.startFor().type(types.InstructionTracer).string(" t : localTracers").end().startBlock();
        b.statement("t.onInstructionEnter(this, bytecode, bytecodeIndex, frame)");
        b.end();

        b.startFor().type(types.InstructionTracer).string(" t : globalTracers").end().startBlock();
        b.statement("t.onInstructionEnter(this, bytecode, bytecodeIndex, frame)");
        b.end();
        return ex;
    }

    private CodeExecutableElement createUpdateGlobalTracers() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(), type(void.class), "updateGlobalTracers",
                        new CodeVariableElement(arrayOf(types.InstructionTracer), "tracers"));

        CodeTreeBuilder b = ex.createBuilder();
        b.statement("this.globalTracers = tracers");
        return ex;
    }

    private CodeExecutableElement createAddLocalTracer() {
        CodeExecutableElement ex = new CodeExecutableElement(
                        Set.of(Modifier.SYNCHRONIZED),
                        type(void.class),
                        "addLocalTracer",
                        new CodeVariableElement(types.InstructionTracer, "tracer"));
        CodeTreeBuilder b = ex.createBuilder();

        b.startStatement().startStaticCall(type(Objects.class), "requireNonNull").string("tracer").end().end();

        b.startDeclaration(arrayOf(types.InstructionTracer), "newTracers");
        b.startStaticCall(type(Arrays.class), "copyOf");
        b.string("localTracers").string("localTracers.length + 1");
        b.end();
        b.end(); // declaration

        b.startFor().type(types.InstructionTracer).string(" search : newTracers").end().startBlock();
        b.startIf().string("tracer == search").end().startBlock();
        b.statement("return");
        b.end(); // block
        b.end();

        b.statement("newTracers[newTracers.length - 1] = tracer");
        b.statement("this.localTracers = newTracers");
        return ex;
    }

    private CodeExecutableElement createRemoveLocalTracer() {
        CodeExecutableElement ex = new CodeExecutableElement(
                        Set.of(Modifier.SYNCHRONIZED),
                        type(void.class),
                        "removeLocalTracer",
                        new CodeVariableElement(types.InstructionTracer, "tracer"));
        CodeTreeBuilder b = ex.createBuilder();

        b.startStatement().startStaticCall(type(Objects.class), "requireNonNull").string("tracer").end().end();
        b.declaration(arrayOf(types.InstructionTracer), "tracers", "this.localTracers");
        b.declaration(type(boolean.class), "found", "false");

        b.startFor().type(types.InstructionTracer).string(" t : tracers").end().startBlock();
        b.startIf().string("t == tracer").end().startBlock();
        b.statement("found = true");
        b.statement("break");
        b.end(); // if
        b.end(); // for

        b.startIf().string("found").end().startBlock();

        b.startDeclaration(arrayOf(types.InstructionTracer), "newTracers");
        b.startNewArray(arrayOf(types.InstructionTracer), CodeTreeBuilder.singleString("tracers.length - 1"));
        b.end();
        b.end(); // declaration

        b.statement("int index = 0");
        b.startFor().string("int i = 0; i < tracers.length; i++").end().startBlock();
        b.declaration(types.InstructionTracer, "t", "tracers[i]");
        b.startIf().string("t != tracer").end().startBlock();
        b.statement("newTracers[index++] = t");
        b.end(); // if
        b.end(); // for

        b.statement("this.localTracers = newTracers");

        b.end(); // if (found)

        return ex;
    }

    private CodeExecutableElement createGetTracedInstruction() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstructionTracer_InstructionAccess, "getTracedInstruction", new String[]{"bytecode", "bytecodeIndex"});
        CodeTreeBuilder b = ex.createBuilder();
        b.startDeclaration(parent.abstractBytecodeNode.asType(), "castBytecode").cast(parent.abstractBytecodeNode.asType()).string("bytecode").end();
        int length = parent.model.traceInstruction.getInstructionEncoding().length();
        b.declaration("int", "nextBytecodeIndex", "bytecodeIndex + " + length);
        b.startReturn();
        parent.emitParseInstruction(b, "castBytecode", "nextBytecodeIndex", CodeTreeBuilder.singleString("castBytecode.readValidBytecode(castBytecode.bytecodes, nextBytecodeIndex)"));
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetTracedOperationCode() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstructionTracer_InstructionAccess, "getTracedOperationCode", new String[]{"bytecode", "bytecodeIndex"});
        CodeTreeBuilder b = ex.createBuilder();
        b.startDeclaration(parent.abstractBytecodeNode.asType(), "castBytecode").cast(parent.abstractBytecodeNode.asType()).string("bytecode").end();
        int length = parent.model.traceInstruction.getInstructionEncoding().length();
        b.declaration("int", "nextBytecodeIndex", "bytecodeIndex + " + length);
        b.startReturn();
        b.string("castBytecode.readValidBytecode(castBytecode.bytecodes, ", "bytecodeIndex + " + length, ")");
        b.end();
        return ex;
    }

}
