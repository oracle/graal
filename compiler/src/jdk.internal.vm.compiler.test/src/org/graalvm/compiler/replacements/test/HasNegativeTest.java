/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.test.AddExports;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AddExports("java.base/java.lang")
public class HasNegativeTest extends GraalCompilerTest {

    protected final String[] testData = new String[]{
                    "A", "\uFF21", "AB", "A", "a", "Ab", "AA", "\uFF21",
                    "A\uFF21", "ABC", "AB", "ABcD", "ABCD\uFF21\uFF21", "ABCD\uFF21", "ABCDEFG\uFF21", "ABCD",
                    "ABCDEFGH\uFF21\uFF21", "\uFF22", "\uFF21\uFF22", "\uFF21A",
                    "\uFF21\uFF21",
                    "\u043c\u0430\u043c\u0430\u0020\u043c\u044b\u043b\u0430\u0020\u0440\u0430\u043c\u0443\u002c\u0020\u0440\u0430\u043c\u0430\u0020\u0441\u044a\u0435\u043b\u0430\u0020\u043c\u0430\u043c\u0443",
                    "crazy dog jumps over laszy fox",
                    "some-string\0xff",
                    "XMM-XMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM+YMM-YMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-YMM-YMM+ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM+",
                    "XMM-XMM-XMM-XMM-YMM-YMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-XMM-XMM+YMM-YMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-XMM-XMM-YMM-YMM-YMM-YMM+ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-",
                    "XMM-XMM-XMM-XMM-YMM-YMM-YMM-YMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM-ZMM+",
                    ""
    };

    @Test
    public void testStringCoding() throws ClassNotFoundException {
        Class<?> klass = Class.forName("java.lang.StringCoding");
        ResolvedJavaMethod method = getResolvedJavaMethod(klass, "hasNegatives");

        for (String input : testData) {
            byte[] bytes = input.getBytes();
            for (int off : new int[]{0, 2, -2}) {
                for (int len : new int[]{bytes.length, 2, 0, -2}) {
                    test(method, null, bytes, off, len);
                }
            }
        }
    }

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        StructuredGraph graph = getReplacements().getIntrinsicGraph(method, CompilationIdentifier.INVALID_COMPILATION_ID, getDebugContext(), StructuredGraph.AllowAssumptions.YES, null);
        if (graph != null) {
            return graph;
        }
        return super.parseForCompile(method, compilationId, options);
    }
}
