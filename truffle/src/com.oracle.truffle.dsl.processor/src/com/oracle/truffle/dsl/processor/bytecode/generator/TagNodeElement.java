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
import static javax.lang.model.element.Modifier.VOLATILE;

import java.util.List;
import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class TagNodeElement extends AbstractElement {

    TagNodeElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL),
                        ElementKind.CLASS, null, "TagNode");
        this.setSuperClass(types.TagTreeNode);
        this.getImplements().add(types.InstrumentableNode);
        this.getImplements().add(types.TagTree);

        this.add(new CodeVariableElement(Set.of(FINAL, STATIC), arrayOf(this.asType()), "EMPTY_ARRAY")).createInitBuilder().string("new TagNode[0]");

        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "tags"));
        this.add(new CodeVariableElement(Set.of(FINAL), type(int.class), "enterBci"));

        CodeExecutableElement constructor = this.add(createConstructorUsingFields(Set.of(), this, null));
        CodeTreeBuilder b = constructor.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();
        b.statement("this.tags = tags");
        b.statement("this.enterBci = enterBci");

        parent.compFinal(this.add(new CodeVariableElement(Set.of(), type(int.class), "returnBci")));
        parent.child(this.add(new CodeVariableElement(Set.of(), arrayOf(this.asType()), "children")));

        parent.child(this.add(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.ProbeNode, "probe")));
        parent.compFinal(this.add(new CodeVariableElement(Set.of(PRIVATE, VOLATILE), types.SourceSection, "sourceSection")));

    }

    void lazyInit() {
        this.add(createCreateWrapper());
        this.add(createFindProbe());
        this.add(createIsInstrumentable());
        this.add(createHasTag());
        this.add(createCopy());
        this.add(createGetSourceSection());
        this.add(createGetSourceSections());
        this.add(createCreateSourceSection());
        this.add(createFindBytecodeNode());
        this.addOptional(createDispatch());
        this.add(createGetLanguage());
        this.add(createGetLanguageId());

        // TagTree
        this.add(createGetTreeChildren());
        this.add(createGetTags());
        this.add(createGetEnterBytecodeIndex());
        this.add(createGetReturnBytecodeIndex());
    }

    private CodeExecutableElement createGetTreeChildren() {
        CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getTreeChildren");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().startStaticCall(type(List.class), "of").string("this.children").end().end();
        return ex;
    }

    private CodeExecutableElement createGetTags() {
        CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getTags");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().startStaticCall(type(List.class), "of").string("mapTagMaskToTagsArray(this.tags)").end().end();
        return ex;
    }

    private CodeExecutableElement createGetEnterBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getEnterBytecodeIndex");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return this.enterBci");
        return ex;
    }

    private CodeExecutableElement createGetReturnBytecodeIndex() {
        CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getReturnBytecodeIndex");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return this.returnBci");
        return ex;
    }

    private CodeExecutableElement createCreateWrapper() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "createWrapper", new String[]{"p"});
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.statement("return null");
        return ex;
    }

    private CodeExecutableElement createFindProbe() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "findProbe");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(types.ProbeNode, "p", "this.probe");
        b.startIf().string("p == null").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.statement("this.probe = p = insert(createProbe(getSourceSection()))");
        b.end();
        b.startStatement().startStaticCall(types.CompilerAsserts, "partialEvaluationConstant").string("p").end().end();
        b.statement("return p");
        return ex;
    }

    private CodeExecutableElement createFindBytecodeNode() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), parent.abstractBytecodeNode.asType(), "findBytecodeNode");
        CodeTreeBuilder b = ex.createBuilder();
        b.declaration(types.Node, "current", "this");
        b.startWhile().string("!(").instanceOf("current", parent.abstractBytecodeNode.asType()).string(" bytecodeNode)").end().startBlock();
        b.statement("current = current.getParent()");
        b.end();

        b.startIf().string("bytecodeNode == null").end().startBlock();
        b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected disconnected node."));
        b.end();
        b.statement("return bytecodeNode");
        return parent.withTruffleBoundary(ex);
    }

    private CodeExecutableElement createGetLanguage() {
        CodeExecutableElement ex = GeneratorUtils.override(types.TagTreeNode, "getLanguage");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        ex.setReturnType(generic(type(Class.class), parent.model.languageClass));
        ex.getAnnotationMirrors().clear();
        GeneratorUtils.mergeSuppressWarnings(ex, "deprecation");
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().typeLiteral(parent.model.languageClass).end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createGetLanguageId() {
        CodeExecutableElement ex = GeneratorUtils.override(types.TagTreeNode, "getLanguageId");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        ex.getAnnotationMirrors().clear();
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().doubleQuote(parent.model.languageId).end();
        b.end();
        return ex;
    }

    private CodeExecutableElement createDispatch() {
        if (ElementUtils.typeEquals(parent.model.tagTreeNodeLibrary.getTemplateType().asType(),
                        types.TagTreeNodeExports)) {
            // use default implementation
            return null;
        }

        CodeExecutableElement ex = GeneratorUtils.override(types.TagTreeNode, "dispatch");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        ex.getAnnotationMirrors().clear();

        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().typeLiteral(parent.model.tagTreeNodeLibrary.getTemplateType().asType()).end();
        return ex;
    }

    private CodeExecutableElement createGetSourceSection() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "getSourceSection");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();

        b.declaration(types.SourceSection, "section", "this.sourceSection");
        b.startIf().string("section == null").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.statement("this.sourceSection = section = createSourceSection()");
        b.end();
        b.statement("return section");
        return ex;
    }

    private CodeExecutableElement createGetSourceSections() {
        CodeExecutableElement ex = GeneratorUtils.override(types.TagTree, "getSourceSections");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn().string("findBytecodeNode().getSourceLocations(enterBci)").end();
        return ex;
    }

    private CodeExecutableElement createCreateSourceSection() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE), types.SourceSection, "createSourceSection");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("enterBci == -1").end().startBlock();
        b.lineComment("only happens for synthetic instrumentable root nodes.");
        b.statement("return null");
        b.end();

        // Because of operation nesting, any source section that applies to the tag.enter should
        // apply to the whole tag operation.
        b.startReturn().string("findBytecodeNode().getSourceLocation(enterBci)").end();
        return ex;
    }

    private CodeExecutableElement createIsInstrumentable() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "isInstrumentable");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        ex.createBuilder().returnTrue();
        return ex;
    }

    private CodeExecutableElement createCopy() {
        CodeExecutableElement ex = GeneratorUtils.override(types.Node, "copy");
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();
        b.startDeclaration(asType(), "copy").cast(asType()).string("super.copy()").end();
        b.statement("copy.probe = null");
        b.statement("return copy");
        return ex;
    }

    private CodeExecutableElement createHasTag() {
        CodeExecutableElement ex = GeneratorUtils.override(types.InstrumentableNode, "hasTag", new String[]{"tag"});
        ex.getModifiers().remove(Modifier.ABSTRACT);
        ex.getModifiers().add(Modifier.FINAL);
        CodeTreeBuilder b = ex.createBuilder();

        boolean elseIf = false;
        int index = 0;
        for (TypeMirror tag : parent.model.getProvidedTags()) {
            elseIf = b.startIf(elseIf);
            b.string("tag == ").typeLiteral(tag).end().startBlock();
            int mask = 1 << index;
            b.startReturn().string("(tags & 0x", Integer.toHexString(mask), ") != 0").end();
            b.end();
            index++;
        }
        b.returnFalse();
        return ex;
    }

}
