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

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeElementScanner;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;

public class FixWarningsVisitor extends CodeElementScanner<Void, Void> {

    private final Set<String> symbolsUsed = new HashSet<>();

    private final DeclaredType overrideType;

    private final Element generatedBy;
    private Set<String> suppressedWarnings = new HashSet<>();
    private boolean computeSymbols = false;
    private boolean seenDeprecatedType = false;

    public FixWarningsVisitor(Element generatedBy, DeclaredType overrideType) {
        this.overrideType = overrideType;
        this.generatedBy = generatedBy;
    }

    @Override
    public Void visitType(CodeTypeElement e, Void p) {
        boolean rootType = e.getEnclosingClass() == null;
        if (rootType) {
            suppressedWarnings.clear();
        }

        List<TypeElement> superTypes = ElementUtils.getSuperTypes(e);
        for (TypeElement type : superTypes) {
            String qualifiedName = ElementUtils.getQualifiedName(type);
            if (qualifiedName.equals(Serializable.class.getCanonicalName())) {
                if (!e.containsField("serialVersionUID")) {
                    suppressedWarnings.add("serial");
                }
                break;
            }
        }

        if (ElementUtils.isPackageDeprecated(e)) {
            suppressedWarnings.add("deprecation");
        }
        super.visitType(e, p);

        if (seenDeprecatedType && rootType) {
            AnnotationMirror suppressWarnings = ElementUtils.findAnnotationMirror(generatedBy, SuppressWarnings.class);
            if (suppressWarnings != null) {
                List<String> currentValues = ElementUtils.getAnnotationValueList(String.class, suppressWarnings, "value");
                if (currentValues.contains("deprecation")) {
                    suppressedWarnings.add("deprecation");
                }
            }
        }

        if (rootType && !suppressedWarnings.isEmpty()) {
            GeneratorUtils.mergeSupressWarnings(e, suppressedWarnings.toArray(new String[0]));
        }
        return null;
    }

    @Override
    public Void visitExecutable(CodeExecutableElement e, Void p) {
        boolean checkIgnored = !suppressedWarnings.contains("unused");
        if (e.getParameters().isEmpty()) {
            checkIgnored = false;
        } else if (e.getModifiers().contains(Modifier.ABSTRACT)) {
            checkIgnored = false;
        } else if (containsOverride(e)) {
            checkIgnored = false;
        }

        symbolsUsed.clear();
        computeSymbols = checkIgnored;

        super.visitExecutable(e, p);

        checkDeprecated(e.getReturnType());
        for (VariableElement parameter : e.getParameters()) {
            checkDeprecated(parameter.asType());
        }

        if (checkIgnored) {
            for (VariableElement parameter : e.getParameters()) {
                if (!symbolsUsed.contains(parameter.getSimpleName().toString())) {
                    suppressedWarnings.add("unused");
                    break;
                }
            }
        }
        return null;
    }

    private void checkDeprecated(TypeMirror mirror) {
        if (ElementUtils.isDeprecated(mirror)) {
            seenDeprecatedType = true;
        }
    }

    private boolean containsOverride(CodeExecutableElement e) {
        for (AnnotationMirror mirror : e.getAnnotationMirrors()) {
            if (ElementUtils.typeEquals(overrideType, mirror.getAnnotationType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void visitTree(CodeTree e, Void p, Element enclosingElement) {
        if (computeSymbols && e.getString() != null) {
            computeSymbols(e.getString());
        }
        if (e.getType() != null) {
            checkDeprecated(e.getType());
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
