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

final class ExceptionHandlerImplElement extends AbstractElement {

    ExceptionHandlerImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL),
                        ElementKind.CLASS, null, "ExceptionHandlerImpl");
        this.setSuperClass(types.ExceptionHandler);

        this.add(new CodeVariableElement(Set.of(FINAL), parent.abstractBytecodeNode.asType(), "bytecode"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));

        CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
        CodeTree tree = constructor.getBodyTree();
        CodeTreeBuilder b = constructor.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.tree(tree);
        this.add(createGetKind());
        this.add(createGetStartBytecodeIndex());
        this.add(createGetEndBytecodeIndex());
        this.add(createGetHandlerBytecodeIndex());
        this.add(createGetTagTree());
    }

    private CodeExecutableElement createGetKind() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getKind");
        CodeTreeBuilder b = ex.createBuilder();
        if (hasSpecialHandlers()) {
            b.startSwitch();
            b.string("bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_KIND]");
            b.end().startBlock();
            if (parent.model.enableTagInstrumentation) {
                b.startCase().string("HANDLER_TAG_EXCEPTIONAL").end().startCaseBlock();
                b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "TAG").end();
                b.end();
            }
            if (parent.model.epilogExceptional != null) {
                b.startCase().string("HANDLER_EPILOG_EXCEPTIONAL").end().startCaseBlock();
                b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "EPILOG").end();
                b.end();
            }
            b.caseDefault().startCaseBlock();
            b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "CUSTOM").end();
            b.end();
            b.end(); // switch block
        } else {
            b.startReturn().staticReference(types.ExceptionHandler_HandlerKind, "CUSTOM").end();
        }
        return ex;
    }

    private boolean hasSpecialHandlers() {
        return parent.model.enableTagInstrumentation || parent.model.epilogExceptional != null;
    }

    private CodeExecutableElement createGetStartBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getStartBytecodeIndex");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_START_BCI]");
        return ex;
    }

    private CodeExecutableElement createGetEndBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getEndBytecodeIndex");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_END_BCI]");
        return ex;
    }

    private CodeExecutableElement createGetHandlerBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getHandlerBytecodeIndex");
        CodeTreeBuilder b = ex.createBuilder();

        if (hasSpecialHandlers()) {
            b.startSwitch();
            b.string("getKind()");
            b.end().startBlock();
            if (parent.model.enableTagInstrumentation) {
                b.startCase().string("TAG").end();
            }
            if (parent.model.epilogExceptional != null) {
                b.startCase().string("EPILOG").end();
            }
            b.startCaseBlock();
            b.statement("return super.getHandlerBytecodeIndex()");
            b.end();
            b.caseDefault().startCaseBlock();
            b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            b.end();
            b.end(); // switch block
        } else {
            b.statement("return bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
        }

        return ex;
    }

    private CodeExecutableElement createGetTagTree() {
        CodeExecutableElement ex = GeneratorUtils.override(types.ExceptionHandler, "getTagTree");
        CodeTreeBuilder b = ex.createBuilder();
        if (parent.model.enableTagInstrumentation) {
            b.startIf().string("getKind() == ").staticReference(types.ExceptionHandler_HandlerKind, "TAG").end().startBlock();
            b.declaration(type(int.class), "nodeId", "bytecode.handlers[baseIndex + EXCEPTION_HANDLER_OFFSET_HANDLER_BCI]");
            b.statement("return bytecode.tagRoot.tagNodes[nodeId]");
            b.end().startElseBlock();
            b.statement("return super.getTagTree()");
            b.end();
        } else {
            b.statement("return super.getTagTree()");
        }

        return ex;
    }

}
