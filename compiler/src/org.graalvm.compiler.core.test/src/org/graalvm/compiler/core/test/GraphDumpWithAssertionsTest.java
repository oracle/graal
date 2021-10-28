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
package org.graalvm.compiler.core.test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Force a dump with a lot of detail to exercise encoding step.
 */
public class GraphDumpWithAssertionsTest extends GraalCompilerTest {

    public static Object snippet(Object[] array) {
        return Arrays.toString(array);
    }

    @Test
    public void testDump() throws IOException {
        try (TemporaryDirectory temp = new TemporaryDirectory(Paths.get("."), "GraphDumpWithAssertionsTest")) {
            EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
            overrides.put(DebugOptions.DumpPath, temp.toString());
            overrides.put(DebugOptions.Dump, ":3");
            overrides.put(DebugOptions.PrintGraph, PrintGraphTarget.File);
            overrides.put(DebugOptions.MethodFilter, null);

            // Generate dump files.
            ResolvedJavaMethod method = getResolvedJavaMethod("snippet");
            StructuredGraph graph = parseForCompile(method, new OptionValues(getInitialOptions(), overrides));
            getCode(method, graph);
        }
    }
}
