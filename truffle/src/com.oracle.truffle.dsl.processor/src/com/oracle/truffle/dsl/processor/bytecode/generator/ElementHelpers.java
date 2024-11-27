/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.WildcardTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

interface ElementHelpers {

    static TypeMirror type(Class<?> t) {
        return ProcessorContext.getInstance().getType(t);
    }

    static TypeElement element(Class<?> t) {
        TypeElement type = ElementUtils.castTypeElement(ProcessorContext.getInstance().getDeclaredType(t));
        if (type == null) {
            throw new NullPointerException("Cannot cast to type element " + t);
        }
        return type;
    }

    static ArrayType arrayOf(TypeMirror t) {
        return new CodeTypeMirror.ArrayCodeTypeMirror(t);
    }

    static TypeElement element(TypeMirror t) {
        TypeElement type = ElementUtils.castTypeElement(t);
        if (type == null) {
            throw new NullPointerException("Cannot cast to type element " + t);
        }
        return type;
    }

    static TypeMirror[] types(Class<?>... types) {
        TypeMirror[] array = new TypeMirror[types.length];
        for (int i = 0; i < types.length; i++) {
            array[i] = type(types[i]);
        }
        return array;
    }

    static TypeMirror wildcard(TypeMirror extendsBounds, TypeMirror superbounds) {
        return new WildcardTypeMirror(extendsBounds, superbounds);
    }

    static DeclaredType generic(TypeMirror type, TypeMirror genericType1) {
        return new CodeTypeMirror.DeclaredCodeTypeMirror(element(type), List.of(genericType1));
    }

    static DeclaredType generic(TypeMirror type, TypeMirror... genericTypes) {
        return new CodeTypeMirror.DeclaredCodeTypeMirror(element(type), List.of(genericTypes));
    }

    static DeclaredType generic(Class<?> type, TypeMirror genericType1) {
        return new CodeTypeMirror.DeclaredCodeTypeMirror(element(type), List.of(genericType1));
    }

    static DeclaredType generic(Class<?> type, TypeMirror... genericType1) {
        return new CodeTypeMirror.DeclaredCodeTypeMirror(element(type), List.of(genericType1));
    }

    static DeclaredType generic(Class<?> type, Class<?>... genericTypes) {
        return new CodeTypeMirror.DeclaredCodeTypeMirror(element(type), List.of(types(genericTypes)));
    }

    static CodeVariableElement addField(CodeElement<? super Element> e, Set<Modifier> modifiers, TypeMirror type, String name) {
        CodeVariableElement var = new CodeVariableElement(modifiers, type, name);
        e.getEnclosedElements().add(var);
        return var;
    }

    static CodeVariableElement addField(CodeElement<? super Element> e, Set<Modifier> modifiers, Class<?> type, String name) {
        return addField(e, modifiers, type(type), name);
    }

    static CodeVariableElement addField(CodeElement<? super Element> e, Set<Modifier> modifiers, Class<?> type, String name, String initString) {
        CodeVariableElement var = createInitializedVariable(modifiers, type, name, initString);
        e.add(var);
        return var;
    }

    static CodeVariableElement createInitializedVariable(Set<Modifier> modifiers, Class<?> type, String name, String initString) {
        CodeTree init = CodeTreeBuilder.singleString(initString);
        CodeVariableElement var = new CodeVariableElement(modifiers, ProcessorContext.getInstance().getType(type), name);
        var.createInitBuilder().tree(init);
        return var;
    }

}
