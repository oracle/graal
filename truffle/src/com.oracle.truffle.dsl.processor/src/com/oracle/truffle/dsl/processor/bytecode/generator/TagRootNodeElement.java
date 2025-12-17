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
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class TagRootNodeElement extends AbstractElement {

    TagRootNodeElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "TagRootNode");
        this.setSuperClass(types.Node);
        parent.child(this.add(new CodeVariableElement(Set.of(), parent.tagNode.asType(), "root")));
        this.add(parent.compFinal(1, new CodeVariableElement(Set.of(FINAL), arrayOf(parent.tagNode.asType()), "tagNodes")));
        this.add(GeneratorUtils.createConstructorUsingFields(Set.of(), this));

        parent.child(this.add(new CodeVariableElement(Set.of(), types.ProbeNode, "probe")));
        CodeExecutableElement getProbe = this.add(new CodeExecutableElement(Set.of(), types.ProbeNode, "getProbe"));
        CodeTreeBuilder b = getProbe.createBuilder();
        b.declaration(types.ProbeNode, "localProbe", "this.probe");
        b.startIf().string("localProbe == null").end().startBlock();
        b.statement("this.probe = localProbe = insert(root.createProbe(null))");
        b.end();
        b.statement("return localProbe");

        this.add(createCopy());
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

}
