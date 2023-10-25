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
import java.util.List;

public final class FieldBuilder extends AbstractCodeBuilder {
    private final String name;
    private final String type;
    private ModifierBuilder modifierBuilder = new ModifierBuilder();
    private final List<AnnotationBuilder> annotations = new ArrayList<>();
    private StatementBuilder declaration;

    public FieldBuilder(Object type, Object name) {
        this.name = name.toString();
        this.type = type.toString();
    }

    public FieldBuilder withAnnotation(AnnotationBuilder annotation) {
        annotations.add(annotation);
        return this;
    }

    public FieldBuilder withQualifiers(ModifierBuilder qualifiers) {
        if (modifierBuilder == null) {
            modifierBuilder = qualifiers;
        } else {
            modifierBuilder.combineWith(qualifiers);
        }
        return this;
    }

    public FieldBuilder withDeclaration(StatementBuilder statement) {
        this.declaration = statement;
        return this;
    }

    @Override
    void buildImpl(IndentingStringBuilder sb) {
        for (AnnotationBuilder annotation : annotations) {
            annotation.buildImpl(sb);
        }
        modifierBuilder.buildImpl(sb);
        sb.appendSpace(type).append(name);
        if (declaration != null) {
            sb.append(" = ");
            declaration.buildImpl(sb);
        }
        sb.appendLine(SEMICOLON);
    }
}
