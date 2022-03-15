/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.core.jdk9.test;

import java.io.PrintStream;
import java.util.EnumSet;

import jdk.vm.ci.meta.JavaKind;

/**
 * Emit the test case patterns for {@link ReadConstantFoldingTest}.
 */
public class ReadConstantFoldingTestGenerator {

    public static void main(String[] args) throws Exception {
        EnumSet<JavaKind> kinds = EnumSet.allOf(JavaKind.class);
        kinds.remove(JavaKind.Void);
        kinds.remove(JavaKind.Illegal);
        for (JavaKind castKind : kinds) {
            for (JavaKind readKind : kinds) {
                for (JavaKind fromKind : kinds) {
                    emit(System.out, readKind, fromKind, castKind);
                }
            }
        }
    }

    static String camelcase(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static void emit(PrintStream out, JavaKind readKind, JavaKind fromKind, JavaKind castKind) {
        if (readKind.isObject() != castKind.isObject()) {
            return;
        }
        if ((readKind == JavaKind.Boolean) != (castKind == JavaKind.Boolean)) {
            return;
        }
        String read = camelcase(readKind.getJavaName());
        String from = camelcase(fromKind.getJavaName());
        String cast = camelcase(castKind.getJavaName());
        String castString = '(' + castKind.getJavaName() + ") ";
        if (readKind == castKind) {
            castString = "";
        } else if (readKind.isNumericInteger()) {
            if (castKind.isNumericInteger()) {
                if (readKind.getBitCount() < castKind.getBitCount() && castKind != JavaKind.Char) {
                    castString = "";
                }
            } else if (castKind.isNumericFloat()) {
                castString = "";
            }
        } else if (readKind.isNumericFloat()) {
            if (castKind.isNumericFloat() && readKind.getBitCount() < castKind.getBitCount()) {
                castString = "";
            }
        } else {
            throw new InternalError("unhandled " + readKind + " " + castKind);
        }

        out.printf("    public %s read%sFrom%sCast%s() {\n", castKind.getJavaName(), read, from, cast);
        out.printf("        return %sget%s(STABLE_%s_ARRAY, ARRAY_%s_BASE_OFFSET);\n", castString, read, from.toUpperCase(), from.toUpperCase());
        out.printf("    }\n");
        out.printf("\n");
        out.printf("    @Test\n");
        out.printf("    public void test%sFrom%sCast%s() {\n", read, from, cast);
        if (fromKind.isObject() != readKind.isObject()) {
            out.printf("        // Mixing Object and primitive produces unstable results and crashes\n");
            out.printf("        // so just compile these patterns to exercise the folding paths.\n");
            out.printf("        shouldFold = false;\n");
            out.printf("        doParse(\"read%sFrom%sCast%s\");\n", read, from, cast);
        } else {
            out.printf("        doTest(\"read%sFrom%sCast%s\");\n", read, from, cast);
        }
        out.printf("    }\n");
        out.printf("\n");
    }
}
