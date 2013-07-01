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
package com.oracle.truffle.dsl.processor.codewriter;

import static com.oracle.truffle.dsl.processor.Utils.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.ast.*;

public class GenerateOverrideVisitor extends CodeElementScanner<Void, Void> {

    private final DeclaredType overrideType;

    public GenerateOverrideVisitor(DeclaredType overrideType) {
        this.overrideType = overrideType;
    }

    @Override
    public Void visitExecutable(CodeExecutableElement e, Void p) {
        if (!e.getModifiers().contains(Modifier.STATIC) && !e.getModifiers().contains(Modifier.PRIVATE)) {
            String name = e.getSimpleName().toString();
            TypeMirror[] params = e.getParameterTypes();

            for (AnnotationMirror mirror : e.getAnnotationMirrors()) {
                if (Utils.typeEquals(overrideType, mirror.getAnnotationType())) {
                    // already declared (may happen if method copied from super class)
                    return super.visitExecutable(e, p);
                }
            }

            if (isDeclaredMethodInSuperType(e.getEnclosingClass(), name, params)) {
                e.addAnnotationMirror(new CodeAnnotationMirror(overrideType));
            }
        }
        return super.visitExecutable(e, p);
    }

}
