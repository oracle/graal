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
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayDeque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.ElementKind;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class SourceInformationTreeImplElement extends AbstractElement {

    SourceInformationTreeImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL),
                        ElementKind.CLASS, null, "SourceInformationTreeImpl");
        this.setSuperClass(types.SourceInformationTree);

        this.add(new CodeVariableElement(Set.of(FINAL, STATIC), type(int.class), "UNAVAILABLE_ROOT")).createInitBuilder().string("-1");
        this.add(new CodeVariableElement(Set.of(FINAL), parent.abstractBytecodeNode.asType(), "bytecode"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "baseIndex"));
        this.add(new CodeVariableElement(Set.of(FINAL), generic(List.class, types.SourceInformationTree), "children"));

        CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null, Set.of("children")));
        CodeTree tree = constructor.getBodyTree();
        CodeTreeBuilder b = constructor.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.tree(tree);
        // We prepend items during parsing. Use a linked list for constant time prepends.
        b.startAssign("this.children").startNew(generic(type(LinkedList.class), types.SourceInformationTree)).end(2);

        this.add(createGetStartBytecodeIndex());
        this.add(createGetEndBytecodeIndex());
        this.add(createGetSourceSection());
        this.add(createGetChildren());
        this.add(createContains());
        this.add(createParse());
    }

    private CodeExecutableElement createGetStartBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getStartBytecodeIndex");
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
        b.startReturn().string("0").end();
        b.end();
        b.startReturn().string("bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_START_BCI]").end();
        return ex;
    }

    private CodeExecutableElement createGetEndBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getEndBytecodeIndex");
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
        b.startReturn().string("bytecode.bytecodes.length").end();
        b.end();
        b.startReturn().string("bytecode.sourceInfo[baseIndex + SOURCE_INFO_OFFSET_END_BCI]").end();
        return ex;
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformation, "getSourceSection");
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
        b.startReturn().string("null").end();
        b.end();
        b.statement("return AbstractBytecodeNode.createSourceSection(bytecode.sources, bytecode.sourceInfo, baseIndex)");
        return ex;
    }

    private CodeExecutableElement createGetChildren() {
        CodeExecutableElement ex = GeneratorUtils.override(types.SourceInformationTree, "getChildren");
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return children");
        return ex;
    }

    private CodeExecutableElement createContains() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), type(boolean.class), "contains");
        ex.addParameter(new CodeVariableElement(this.asType(), "other"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("baseIndex == UNAVAILABLE_ROOT").end().startBlock();
        b.startReturn().string("true").end();
        b.end();
        b.statement("return this.getStartBytecodeIndex() <= other.getStartBytecodeIndex() && other.getEndBytecodeIndex() <= this.getEndBytecodeIndex()");
        return ex;
    }

    private CodeExecutableElement createParse() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), types.SourceInformationTree, "parse");
        ex.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "bytecode"));

        CodeTreeBuilder b = ex.createBuilder();
        /**
         * This algorithm reconstructs the source information tree in a single linear pass of the
         * source info table.
         */
        b.declaration(arrayOf(type(int.class)), "sourceInfo", "bytecode.sourceInfo");

        b.startIf().string("sourceInfo.length == 0").end().startBlock();
        b.statement("return null");
        b.end();

        b.lineComment("Create a synthetic root node that contains all other SourceInformationTrees.");
        b.startDeclaration(this.asType(), "root");
        b.startNew(this.asType()).string("bytecode").string("UNAVAILABLE_ROOT").end();
        b.end();

        b.declaration(type(int.class), "baseIndex", "sourceInfo.length");
        b.declaration(this.asType(), "current", "root");
        b.declaration(generic(ArrayDeque.class, this.asType()), "stack", "new ArrayDeque<>()");
        b.startDoBlock();
        // Create the next node.
        b.statement("baseIndex -= SOURCE_INFO_LENGTH");
        b.startDeclaration(this.asType(), "newNode");
        b.startNew(this.asType()).string("bytecode").string("baseIndex").end();
        b.end();

        // Find the node's parent.
        b.startWhile().string("!current.contains(newNode)").end().startBlock();
        // If newNode is not contained in current, then no more entries belong to current (we
        // are done parsing it). newNode must be a child of some other node on the stack.
        b.statement("current = stack.pop()");
        b.end();

        // Link up the child and continue parsing.
        b.statement("current.children.addFirst(newNode)");
        b.statement("stack.push(current)");
        b.statement("current = newNode");

        b.end().startDoWhile().string("baseIndex > 0").end();

        b.startIf().string("root.getChildren().size() == 1").end().startBlock();
        b.lineComment("If there is an actual root source section, ignore the synthetic root we created.");
        b.statement("return root.getChildren().getFirst()");
        b.end().startElseBlock();
        b.statement("return root");
        b.end();
        return parent.withTruffleBoundary(ex);
    }

}
