/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.dsl.processor.java.transform;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.io.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.java.model.*;

public class FixWarningsVisitor extends CodeElementScanner<Void, Void> {

    private final Set<String> symbolsUsed = new HashSet<>();

    private final ProcessingEnvironment processingEnv;
    private final DeclaredType unusedAnnotation;
    private final DeclaredType overrideType;

    public FixWarningsVisitor(ProcessingEnvironment processingEnv, DeclaredType unusedAnnotation, DeclaredType overrideType) {
        this.processingEnv = processingEnv;
        this.unusedAnnotation = unusedAnnotation;
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
                e.getAnnotationMirrors().add(createUnusedAnnotationMirror());
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

    private CodeAnnotationMirror createUnusedAnnotationMirror() {
        CodeAnnotationMirror mirror = new CodeAnnotationMirror(unusedAnnotation);
        mirror.setElementValue(mirror.findExecutableElement("value"), new CodeAnnotationValue("unused"));
        return mirror;
    }

    @Override
    public void visitTree(CodeTree e, Void p) {
        if (e.getString() != null) {
            computeSymbols(e.getString());
        }
        super.visitTree(e, p);
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
