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

final class LocalVariableImplElement extends AbstractElement {

    LocalVariableImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "LocalVariableImpl");
        this.setSuperClass(types.LocalVariable);

        this.add(new CodeVariableElement(Set.of(FINAL), parent.abstractBytecodeNode.asType(), "bytecode"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

        CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
        CodeTree tree = constructor.getBodyTree();
        CodeTreeBuilder b = constructor.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.tree(tree);

        if (parent.model.enableBlockScoping) {
            this.add(createGetStartIndex());
            this.add(createGetEndIndex());
        }
        this.add(createGetInfo());
        this.add(createGetName());
        this.add(createGetLocalIndex());
        this.add(createGetLocalOffset());
        this.add(createGetTypeProfile());
    }

    private CodeExecutableElement createGetStartIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getStartIndex");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_START_BCI]");
        return ex;
    }

    private CodeExecutableElement createGetEndIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getEndIndex");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_END_BCI]");
        return ex;
    }

    private CodeExecutableElement createGetInfo() {
        CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getInfo");
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(type(int.class), "infoId", "bytecode.locals[baseIndex + LOCALS_OFFSET_INFO]");
        b.startIf().string("infoId == -1").end().startBlock();
        b.returnNull();
        b.end().startElseBlock();
        b.startReturn().tree(BytecodeRootNodeElement.readConst("infoId", "bytecode.constants")).end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetName() {
        CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getName");
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(type(int.class), "nameId", "bytecode.locals[baseIndex + LOCALS_OFFSET_NAME]");
        b.startIf().string("nameId == -1").end().startBlock();
        b.returnNull();
        b.end().startElseBlock();
        b.startReturn().tree(BytecodeRootNodeElement.readConst("nameId", "bytecode.constants")).end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetLocalIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getLocalIndex");
        CodeTreeBuilder b = ex.createBuilder();
        if (parent.model.enableBlockScoping) {
            b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_LOCAL_INDEX]");
        } else {
            b.statement("return baseIndex / LOCALS_LENGTH");
        }
        return ex;
    }

    private CodeExecutableElement createGetLocalOffset() {
        CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getLocalOffset");
        CodeTreeBuilder b = ex.createBuilder();
        if (parent.model.enableBlockScoping) {
            b.statement("return bytecode.locals[baseIndex + LOCALS_OFFSET_FRAME_INDEX] - USER_LOCALS_START_INDEX");
        } else {
            b.statement("return baseIndex / LOCALS_LENGTH");
        }
        return ex;
    }

    private CodeExecutableElement createGetTypeProfile() {
        CodeExecutableElement ex = GeneratorUtils.override(types.LocalVariable, "getTypeProfile");
        CodeTreeBuilder b = ex.createBuilder();

        if (parent.model.usesBoxingElimination()) {
            b.declaration(type(byte[].class), "localTags", "bytecode.getLocalTags()");
            b.startIf().string("localTags == null").end().startBlock();
            b.returnNull();
            b.end();
            b.statement("return FrameSlotKind.fromTag(localTags[getLocalIndex()])");
        } else {
            b.returnNull();
        }
        return ex;
    }

}
