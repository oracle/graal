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
    private final String className;
    private JavadocBuilder javaDoc;
    private QualifierBuilder qualifierBuilder;
    private String superClass = null;
    private final List<String> annotations = new ArrayList<>();
    private final Set<String> superInterfaces = new HashSet<>();
    private final List<AbstractCodeBuilder> members = new ArrayList<>();

    public ClassBuilder(String className) {
        this.className = className;
    }

    public ClassBuilder withQualifiers(QualifierBuilder qualifiers) {
        if (qualifierBuilder == null) {
            qualifierBuilder = qualifiers;
        } else {
            qualifierBuilder.combineWith(qualifiers);
        }
        return this;
    }

    public ClassBuilder withSuperClass(String superClass) {
        if (this.superClass != null) {
            throw new IllegalStateException("Super class is already set");
        }
        this.superClass = superClass;
        return this;
    }

    public ClassBuilder withAnnotation(Object... annotationParts) {
        annotations.add(joinParts(annotationParts));
        return this;
    }

    public ClassBuilder withSuperInterfaces(Object... superInterfaces) {
        this.superInterfaces.addAll(Arrays.stream(superInterfaces).map(Object::toString).collect(Collectors.toList()));
        return this;
    }

    public ClassBuilder withField(FieldBuilder fieldBuilder) {
        if (fieldBuilder != null) {
            this.members.add(fieldBuilder);
        }
        return this;
    }

    public ClassBuilder withInnerClass(ClassBuilder innerClass) {
        this.members.add(innerClass);
        return this;
    }

    public ClassBuilder withMethod(MethodBuilder methodBuilder) {
        this.members.add(methodBuilder);
        return this;
    }

    public ClassBuilder withJavaDoc(JavadocBuilder javaDoc) {
        this.javaDoc = javaDoc;
        return this;
    }

    @Override
    String build() {
        StringBuilder sb = new StringBuilder();

        if (javaDoc != null) {
            sb.append(baseIndent).append(javaDoc.build());
        }

        for (String annotation : annotations) {
            sb.append(baseIndent).append(annotation);
            sb.append(NEWLINE);
        }

        sb.append(baseIndent).append(qualifierBuilder.build());
        sb.append("class ").append(className);
        if (superClass != null) {
            sb.append(" extends ").append(superClass);
        }
        if (superInterfaces.size() > 0) {
            sb.append(" implements ").append(joinPartsWith(", ", superInterfaces));
        }
        sb.append(' ').append(BLOCK_OPEN).append(NEWLINE);

        for (AbstractCodeBuilder member : members) {
            member.setIndentLevel(tabLevel + 1);
            sb.append(member.build());
            sb.append(NEWLINE);
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append(baseIndent).append(BLOCK_CLOSE).append(NEWLINE);

        return sb.toString();
    }
}
