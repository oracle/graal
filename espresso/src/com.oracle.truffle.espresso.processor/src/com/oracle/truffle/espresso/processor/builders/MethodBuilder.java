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
    public static char PAREN_OPEN = '(';
    public static char PAREN_CLOSE = ')';
    public static char TEMPLATE_OPEN = '<';
    public static char TEMPLATE_CLOSE = '>';
    
    private final String methodName;
    private String returnType = "void";
    private QualifierBuilder qualifierBuilder;
    private final List<String> body = new ArrayList<>();
    private String[] params;
    private String[] templateParams;
    private String baseIndent = TAB_1;


    public MethodBuilder(String methodName) {
        this.methodName = methodName;
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
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            sb.append(part.toString());
        }
        body.add(sb.toString());
        return this;
    }

    public MethodBuilder withParams(String... params) {
        this.params = params;
        return this;
    }

    public MethodBuilder withTemplateParams(String... params) {
        this.templateParams = params;
        return this;
    }

    public void setTabLevel(int lvl) {
        if (lvl < 0 || lvl > 10) {
            throw new IllegalArgumentException("Invalid indent level");
        }
        final char[] array = new char[lvl * TAB_1.length()];
        Arrays.fill(array, ' ');
        this.baseIndent = new String(array);
    }

    @Override
    String build() {
        StringBuilder sb = new StringBuilder();
        sb.append(baseIndent);
        sb.append(qualifierBuilder.build());
        sb.append(TEMPLATE_OPEN).append(String.join(", ", templateParams)).append(TEMPLATE_CLOSE);
        sb.append(' ').append(returnType).append(' ');
        sb.append(methodName);
        sb.append(PAREN_OPEN).append(String.join(", ", params)).append(PAREN_CLOSE).append(' ');
        sb.append(BLOCK_OPEN).append(NEWLINE);
        for (String line : body) {
            sb.append(baseIndent).append(TAB_1).append(line).append(NEWLINE);
        }
        sb.append(baseIndent).append(BLOCK_CLOSE).append(NEWLINE);
        return sb.toString();
    }
}
