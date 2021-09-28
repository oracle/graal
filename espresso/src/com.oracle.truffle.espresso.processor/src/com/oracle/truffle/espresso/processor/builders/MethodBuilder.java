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
import java.util.List;

public final class MethodBuilder extends AbstractCodeBuilder {
    public static final String OVERRIDE = "@Override";
    public static final char TEMPLATE_OPEN = '<';
    public static final char TEMPLATE_CLOSE = '>';

    private boolean constructor = false;
    private final String methodName;
    private String returnType = "void";
    private QualifierBuilder qualifierBuilder;
    private final List<String> annotations = new ArrayList<>();
    private final List<String> body = new ArrayList<>();
    private final List<String> params = new ArrayList<>();
    private final List<String> templateParams = new ArrayList<>();


    public MethodBuilder(String methodName) {
        this.methodName = methodName;
        setIndentLevel(1);
    }

    public MethodBuilder withQualifiers(QualifierBuilder qualifiers) {
        if (qualifierBuilder == null) {
            qualifierBuilder = qualifiers;
        } else {
            qualifierBuilder.combineWith(qualifiers);
        }
        return this;
    }

    public MethodBuilder withReturnType(String type) {
        this.returnType = type;
        return this;
    }

    public MethodBuilder addBodyLine(Object... parts) {
        body.add(joinParts(parts));
        return this;
    }

    public MethodBuilder addIndentedBodyLine(int lvl, Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lvl; ++i) {
            sb.append(TAB_1);
        }
        sb.append(joinParts(parts));
        body.add(sb.toString());
        return this;
    }

    public MethodBuilder withAnnotation(String annotation) {
        annotations.add(annotation);
        return this;
    }

    public MethodBuilder withParams(String... params) {
        this.params.addAll(Arrays.asList(params));
        return this;
    }

    public MethodBuilder withTemplateParams(String... params) {
        this.templateParams.addAll(Arrays.asList(params));
        return this;
    }

    public MethodBuilder asConstructor() {
        this.constructor = true;
        return this;
    }

    public MethodBuilder withOverrideAnnotation() {
        return withAnnotation(OVERRIDE);
    }

    @Override
    String build() {
        StringBuilder sb = new StringBuilder();
        for (String annotation : annotations) {
            sb.append(baseIndent).append(annotation);
            sb.append(NEWLINE);
        }
        sb.append(baseIndent);
        sb.append(qualifierBuilder.build());
        if (templateParams.size() > 0) {
            sb.append(TEMPLATE_OPEN).append(joinPartsWith(", ", templateParams)).append(TEMPLATE_CLOSE);
            sb.append(' ');
        }
        if (!constructor) {
            sb.append(returnType).append(' ');
        }
        sb.append(methodName);
        sb.append(PAREN_OPEN).append(joinPartsWith(", ", params)).append(PAREN_CLOSE).append(' ');
        sb.append(BLOCK_OPEN).append(NEWLINE);
        for (String line : body) {
            sb.append(baseIndent).append(TAB_1).append(line).append(NEWLINE);
        }
        sb.append(baseIndent).append(BLOCK_CLOSE).append(NEWLINE);
        return sb.toString();
    }
}
