/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.java.model;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementScanner8;

public abstract class CodeElementScanner<R, P> extends ElementScanner8<R, P> {

    @Override
    public final R visitExecutable(ExecutableElement e, P p) {
        if (!(e instanceof CodeExecutableElement)) {
            throw new ClassCastException(e.toString());
        }
        return visitExecutable(cast(e, CodeExecutableElement.class), p);
    }

    public R visitExecutable(CodeExecutableElement e, P p) {
        R ret = super.visitExecutable(e, p);
        if (e.getBodyTree() != null) {
            visitTree(e.getBodyTree(), p, e);
        }
        return ret;
    }

    @Override
    public R visitVariable(VariableElement e, P p) {
        if (e instanceof CodeVariableElement) {
            CodeTree init = ((CodeVariableElement) e).getInit();
            if (init != null) {
                visitTree(init, p, e);
            }
        }
        return super.visitVariable(e, p);
    }

    @Override
    public R visitPackage(PackageElement e, P p) {
        return super.visitPackage(e, p);
    }

    @Override
    public final R visitType(TypeElement e, P p) {
        return visitType(cast(e, CodeTypeElement.class), p);
    }

    public R visitType(CodeTypeElement e, P p) {
        return super.visitType(e, p);
    }

    @Override
    public R visitTypeParameter(TypeParameterElement e, P p) {
        return super.visitTypeParameter(e, p);
    }

    private static <E> E cast(Element element, Class<E> clazz) {
        return clazz.cast(element);
    }

    public void visitTree(CodeTree e, P p, Element parent) {
        List<CodeTree> elements = e.getEnclosedElements();
        if (elements != null) {
            for (CodeTree tree : e.getEnclosedElements()) {
                visitTree(tree, p, parent);
            }
        }
    }

    @SuppressWarnings("unused")
    public void visitImport(CodeImport e, P p) {
    }

}
