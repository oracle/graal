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

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public final class ClassFileBuilder extends AbstractCodeBuilder {
    public static final String PACKAGE = "package";
    public static final String IMPORT = "import";
    public static final String COPYRIGHT = "/* Copyright (c) " + Year.now() + " Oracle and/or its affiliates. All rights reserved.\n" +
                    " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                    " *\n" +
                    " * This code is free software; you can redistribute it and/or modify it\n" +
                    " * under the terms of the GNU General Public License version 2 only, as\n" +
                    " * published by the Free Software Foundation.\n" +
                    " *\n" +
                    " * This code is distributed in the hope that it will be useful, but WITHOUT\n" +
                    " * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n" +
                    " * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n" +
                    " * version 2 for more details (a copy is included in the LICENSE file that\n" +
                    " * accompanied this code).\n" +
                    " *\n" +
                    " * You should have received a copy of the GNU General Public License version\n" +
                    " * 2 along with this work; if not, write to the Free Software Foundation,\n" +
                    " * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n" +
                    " *\n" +
                    " * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n" +
                    " * or visit www.oracle.com if you need additional information or have any\n" +
                    " * questions.\n" +
                    " */\n";

    private boolean copyright;
    private String packageName;
    private final List<List<String>> importGroups = new ArrayList<>();
    private final List<ClassBuilder> classes = new ArrayList<>();

    public ClassFileBuilder() {
        importGroups.add(new ArrayList<>());
    }

    public ClassFileBuilder inPackage(String pkg) {
        packageName = pkg;
        return this;
    }

    public ClassFileBuilder withImport(String pkg) {
        importGroups.get(0).add(pkg);
        return this;
    }

    public ClassFileBuilder withImportGroup(List<String> imports) {
        importGroups.add(imports);
        return this;
    }

    public ClassFileBuilder withClass(ClassBuilder classBuilder) {
        classes.add(classBuilder);
        return this;
    }

    public ClassFileBuilder withCopyright() {
        copyright = true;
        return this;
    }

    public String build() {
        IndentingStringBuilder sb = new IndentingStringBuilder(0);
        buildImpl(sb);
        return sb.toString();
    }

    @Override
    void buildImpl(IndentingStringBuilder sb) {
        if (classes.size() == 0) {
            throw new IllegalStateException("Cannot build a file with no classes defined");
        }

        if (copyright) {
            sb.appendLine(COPYRIGHT);
        }

        if (packageName != null) {
            sb.appendSpace(PACKAGE).append(packageName).appendLine(SEMICOLON);
        }
        sb.appendLine();

        for (List<String> imports : importGroups) {
            for (String importStr : imports) {
                sb.appendSpace(IMPORT).append(importStr).appendLine(SEMICOLON);
            }
            if (imports.size() > 0) {
                sb.appendLine();
            }
        }

        for (ClassBuilder classBuilder : classes) {
            classBuilder.buildImpl(sb);
            sb.appendLine();
        }
    }
}
