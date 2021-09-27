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

public final class ClassFileBuilder extends AbstractCodeBuilder {
    private String packageName;
    private final List<String[]> importGroups = new ArrayList<>();
    private final List<ClassBuilder> classes = new ArrayList<>();

    public ClassFileBuilder inPackage(String packageName) {
        this.packageName = packageName;
        return this;
    }

    public ClassFileBuilder withImportGroup(String... imports) {
        this.importGroups.add(imports);
        return this;
    }

    public ClassFileBuilder withClass(ClassBuilder classBuilder) {
        this.classes.add(classBuilder);
        return this;
    }

    @Override
    String build() {
        if (classes.size() == 0) {
            throw new IllegalStateException("Cannot build a file with no classes defined");
        }

        StringBuilder sb = new StringBuilder();

        if (packageName != null) {
            sb.append("package ").append(packageName).append(SEMICOLON_NEWLINE);
        }
        sb.append(NEWLINE);

        for (String[] imports : importGroups) {
            for (String importStr : imports) {
                sb.append("import ").append(importStr).append(';').append(NEWLINE);
            }
            //sb.append(NEWLINE);
        }

        for (ClassBuilder classBuilder : classes) {
            sb.append(classBuilder.build());
            sb.append(NEWLINE);
        }

        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
