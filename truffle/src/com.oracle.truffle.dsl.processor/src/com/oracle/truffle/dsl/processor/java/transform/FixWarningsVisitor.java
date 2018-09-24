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
package com.oracle.truffle.dsl.processor.java.transform;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.modifiers;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeElementScanner;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

public class FixWarningsVisitor extends CodeElementScanner<Void, Void> {

    private final Set<String> symbolsUsed = new HashSet<>();

    private final ProcessingEnvironment processingEnv;
    private final DeclaredType suppressWarnings;
    private final DeclaredType overrideType;

    public FixWarningsVisitor(ProcessingEnvironment processingEnv, DeclaredType suppressWarnings, DeclaredType overrideType) {
        this.processingEnv = processingEnv;
        this.suppressWarnings = suppressWarnings;
        this.overrideType = overrideType;
    }

    @Override
    public Void visitType(CodeTypeElement e, Void p) {
        List<TypeElement> superTypes = ElementUtils.getSuperTypes(e);
        for (TypeElement type : superTypes) {
            String qualifiedName = ElementUtils.getQualifiedName(type);
            if (qualifiedName.equals(Serializable.class.getCanonicalName())) {
                if (!e.containsField("serialVersionUID")) {
                    e.add(new CodeVariableElement(modifiers(PRIVATE, STATIC, FINAL), ElementUtils.getType(processingEnv, long.class), "serialVersionUID", "1L"));
                }
                break;
            }
        }
        if (ElementUtils.isDeprecated(e)) {
            if (e.getEnclosingClass() == null) {
                e.getAnnotationMirrors().add(createIgnoreDeprecations());
            }
        }
        return super.visitType(e, p);
    }

    @Override
    public Void visitExecutable(CodeExecutableElement e, Void p) {
        if (e.getParameters().isEmpty()) {
            return null;
        } else if (e.getModifiers().contains(Modifier.ABSTRACT)) {
            return null;
        } else if (containsOverride(e)) {
            return null;
        }

        symbolsUsed.clear();
        super.visitExecutable(e, p);

        for (VariableElement parameter : e.getParameters()) {
            if (!symbolsUsed.contains(parameter.getSimpleName().toString())) {
                e.getAnnotationMirrors().add(createUnused());
                break;
            }
        }
        return null;
    }

    private boolean containsOverride(CodeExecutableElement e) {
        for (AnnotationMirror mirror : e.getAnnotationMirrors()) {
            if (ElementUtils.typeEquals(overrideType, mirror.getAnnotationType())) {
                return true;
            }
        }
        return false;
    }

    private CodeAnnotationMirror createUnused() {
        CodeAnnotationMirror mirror = new CodeAnnotationMirror(suppressWarnings);
        mirror.setElementValue(mirror.findExecutableElement("value"), new CodeAnnotationValue("unused"));
        return mirror;
    }

    private CodeAnnotationMirror createIgnoreDeprecations() {
        CodeAnnotationMirror mirror = new CodeAnnotationMirror(suppressWarnings);
        mirror.setElementValue(mirror.findExecutableElement("value"), new CodeAnnotationValue("deprecation"));
        return mirror;
    }

    @Override
    public void visitTree(CodeTree e, Void p, Element enclosingElement) {
        if (e.getString() != null) {
            computeSymbols(e.getString());
        }
        super.visitTree(e, p, enclosingElement);
    }

    private void computeSymbols(String s) {
        // TODO there should not be any need for a StringTokenizer if we have a real AST for
        // method bodies. Also the current solution is not perfect. What if one token
        // is spread across multiple CodeTree instances? But for now that works.
        StringTokenizer tokenizer = new StringTokenizer(s, ".= :,()[];{}\"\"'' ", false);
        while (tokenizer.hasMoreElements()) {
            String token = tokenizer.nextToken().trim();
            if (token.length() > 0) {
                symbolsUsed.add(token);
            }
        }
    }

}
