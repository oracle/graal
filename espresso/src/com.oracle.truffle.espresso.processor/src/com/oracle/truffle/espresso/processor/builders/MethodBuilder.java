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
    private ModifierBuilder modifierBuilder = new ModifierBuilder();
    private final List<String> annotations = new ArrayList<>();
    private final List<MethodBodyLine> body = new ArrayList<>();
    private final List<String> params = new ArrayList<>();
    private final List<String> templateParams = new ArrayList<>();

    public MethodBuilder(String methodName) {
        this.methodName = methodName;
    }

    public MethodBuilder withModifiers(ModifierBuilder qualifiers) {
        if (modifierBuilder == null) {
            modifierBuilder = qualifiers;
        } else {
            modifierBuilder.combineWith(qualifiers);
        }
        return this;
    }

    public MethodBuilder withReturnType(String type) {
        returnType = type;
        return this;
    }

    public MethodBuilder addBodyLine(Object... parts) {
        body.add(new MethodBodyLine(parts));
        return this;
    }

    public MethodBuilder addIndentedBodyLine(int lvl, Object... parts) {
        body.add(new MethodBodyLine(parts, lvl));
        return this;
    }

    public MethodBuilder withAnnotation(String annotation) {
        annotations.add(annotation);
        return this;
    }

    public MethodBuilder withParams(String... ps) {
        params.addAll(Arrays.asList(ps));
        return this;
    }

    public MethodBuilder withTemplateParams(String... ps) {
        templateParams.addAll(Arrays.asList(ps));
        return this;
    }

    public MethodBuilder asConstructor() {
        constructor = true;
        return this;
    }

    public MethodBuilder withOverrideAnnotation() {
        return withAnnotation(OVERRIDE);
    }

    @Override
    void buildImpl(IndentingStringBuilder sb) {
        for (String annotation : annotations) {
            sb.appendLine(annotation);
        }
        modifierBuilder.buildImpl(sb);
        if (templateParams.size() > 0) {
            sb.append(TEMPLATE_OPEN).join(", ", templateParams).appendSpace(TEMPLATE_CLOSE);
        }
        if (!constructor) {
            sb.appendSpace(returnType);
        }
        sb.append(methodName);
        sb.append(PAREN_OPEN).join(", ", params).appendSpace(PAREN_CLOSE);
        sb.appendLine(BLOCK_OPEN);
        sb.raiseIndentLevel();
        for (MethodBodyLine ln : body) {
            sb.appendIndent(ln.additionalIndent);
            for (Object part : ln.line) {
                if (part instanceof AbstractCodeBuilder) {
                    ((AbstractCodeBuilder) part).buildImpl(sb);
                } else {
                    sb.append(part.toString());
                }
            }
            sb.appendLine();
        }
        sb.lowerIndentLevel();
        sb.appendLine(BLOCK_CLOSE);
    }

    private static class MethodBodyLine {
        Object[] line;
        int additionalIndent;

        MethodBodyLine(Object[] lineContents) {
            line = lineContents;
        }

        MethodBodyLine(Object[] line, int additionalIndent) {
            this.line = line;
            this.additionalIndent = additionalIndent;
        }
    }
}
