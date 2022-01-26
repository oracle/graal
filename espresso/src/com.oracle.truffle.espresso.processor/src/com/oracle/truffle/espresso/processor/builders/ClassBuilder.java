/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.processor.builders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClassBuilder extends AbstractCodeBuilder {
    private static final String CLASS = "class";
    private static final String EXTENDS = "extends";
    private static final String IMPLEMENTS = "implements";

    private final String className;
    private JavadocBuilder javaDoc;
    private ModifierBuilder modifierBuilder = new ModifierBuilder();
    private String superClass = null;
    private final List<String> annotations = new ArrayList<>();
    private final Set<String> superInterfaces = new HashSet<>();
    private final List<AbstractCodeBuilder> members = new ArrayList<>();

    public ClassBuilder(String className) {
        this.className = className;
    }

    public ClassBuilder withQualifiers(ModifierBuilder qualifiers) {
        if (modifierBuilder == null) {
            modifierBuilder = qualifiers;
        } else {
            modifierBuilder.combineWith(qualifiers);
        }
        return this;
    }

    public ClassBuilder withSuperClass(String superClassName) {
        if (this.superClass != null) {
            throw new IllegalStateException("Super class is already set");
        }
        superClass = superClassName;
        return this;
    }

    public ClassBuilder withAnnotation(String... annotationParts) {
        annotations.add(String.join("", annotationParts));
        return this;
    }

    public ClassBuilder withSuperInterfaces(String... interfaces) {
        superInterfaces.addAll(Arrays.stream(interfaces).collect(Collectors.toList()));
        return this;
    }

    public ClassBuilder withField(FieldBuilder fieldBuilder) {
        members.add(fieldBuilder);
        return this;
    }

    public ClassBuilder withInnerClass(ClassBuilder innerClass) {
        members.add(innerClass);
        return this;
    }

    public ClassBuilder withMethod(MethodBuilder methodBuilder) {
        members.add(methodBuilder);
        return this;
    }

    public ClassBuilder withJavaDoc(JavadocBuilder javaDocBuilder) {
        javaDoc = javaDocBuilder;
        return this;
    }

    @Override
    void buildImpl(IndentingStringBuilder sb) {
        if (javaDoc != null) {
            javaDoc.buildImpl(sb);
        }

        for (String annotation : annotations) {
            sb.appendLine(annotation);
        }

        modifierBuilder.buildImpl(sb);
        sb.appendSpace(CLASS).append(className);
        if (superClass != null) {
            sb.appendSpace().appendSpace(EXTENDS).append(superClass);
        }
        if (superInterfaces.size() > 0) {
            sb.appendSpace().appendSpace(IMPLEMENTS).join(", ", superInterfaces);
        }
        sb.appendSpace().appendLine(BLOCK_OPEN).appendLine();

        sb.raiseIndentLevel();
        for (AbstractCodeBuilder member : members) {
            member.buildImpl(sb);
            sb.appendLine();
        }
        sb.lowerIndentLevel();
        sb.appendLine(BLOCK_CLOSE);
    }
}
