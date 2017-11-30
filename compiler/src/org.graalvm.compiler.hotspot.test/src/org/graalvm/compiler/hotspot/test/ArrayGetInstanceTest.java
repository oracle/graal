/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Test;

import java.lang.reflect.Array;

public class ArrayGetInstanceTest extends GraalCompilerTest {

    public static boolean newArray(Class<?> klass) {
        Array.newInstance(klass, 0);
        return GraalDirectives.inCompiledCode();
    }

    @Test
    public void testNewArray() throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod("newArray");
        InstalledCode code = getCode(method);
        assertTrue((Boolean) code.executeVarargs(ArrayGetInstanceTest.class));
    }

    public static boolean newArrayInLoop(Class<?> klass, int length) {
        for (int i = 0; i < 10; i++) {
            Array.newInstance(klass, length);
        }
        return GraalDirectives.inCompiledCode();
    }

    @Test
    public void testNewArrayInLoop() throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod("newArrayInLoop");
        InstalledCode code = getCode(method, new OptionValues(getInitialOptions(), HighTier.Options.Inline, false));
        try {
            code.executeVarargs(ArrayGetInstanceTest.class, -1);
        } catch (Throwable e) {
        }
        code = getCode(method, new OptionValues(getInitialOptions(), HighTier.Options.Inline, false));
        assertTrue((Boolean) code.executeVarargs(ArrayGetInstanceTest.class, 0));
    }

}
