/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.rawnativecall.test;

import java.lang.reflect.*;

import org.junit.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;

public class InstalledCodeExecuteHelperTest extends GraalCompilerTest {

    private static final int ITERATIONS = 10000000;

    @Ignore
    @Test
    public void test1() throws NoSuchMethodException, SecurityException {

        final Method benchrMethod = InstalledCodeExecuteHelperTest.class.getMethod("bench", long.class, long.class);
        final ResolvedJavaMethod benchJavaMethod = Graal.getRequiredCapability(GraalCodeCacheProvider.class).lookupJavaMethod(benchrMethod);
        HotSpotInstalledCode benchCode = (HotSpotInstalledCode) getCode(benchJavaMethod, parse(benchrMethod));

        final Method wrapperMethod = InstalledCodeExecuteHelperTest.class.getMethod("executeWrapper", long.class, long.class, Object.class, Object.class, Object.class);
        final ResolvedJavaMethod wrapperJavaMethod = Graal.getRequiredCapability(GraalCodeCacheProvider.class).lookupJavaMethod(wrapperMethod);
        HotSpotInstalledCode wrapperCode = (HotSpotInstalledCode) getCode(wrapperJavaMethod, parse(wrapperMethod));

        final Method fooMethod = InstalledCodeExecuteHelperTest.class.getMethod("foo", Object.class, Object.class, Object.class);
        final HotSpotResolvedJavaMethod fooJavaMethod = (HotSpotResolvedJavaMethod) Graal.getRequiredCapability(GraalCodeCacheProvider.class).lookupJavaMethod(fooMethod);
        HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooJavaMethod, parse(fooMethod));

        System.out.println(wrapperCode.executeVarargs(fooCode.getnmethod(), fooJavaMethod.getMetaspaceMethod(), null, null, null));

        long nmethod = fooCode.getnmethod();
        long metaspacemethod = fooJavaMethod.getMetaspaceMethod();

        System.out.println("Without replaced InstalledCode.execute:" + bench(nmethod, metaspacemethod));

        System.out.println("WITH replaced InstalledCode.execute:" + benchCode.executeVarargs(nmethod, metaspacemethod));

    }

    public static Long bench(long nmethod, long metaspacemethod) {
        long start = System.currentTimeMillis();

        for (int i = 0; i < ITERATIONS; i++) {
            HotSpotInstalledCode.executeHelper(nmethod, metaspacemethod, null, null, null);
        }

        long end = System.currentTimeMillis();
        return (end - start);
    }

    public static Object foo(@SuppressWarnings("unused") Object a1, @SuppressWarnings("unused") Object a2, @SuppressWarnings("unused") Object a3) {
        return 42;
    }

    public static Object executeWrapper(long nmethod, long metaspaceMethod, Object arg1, Object arg2, Object arg3) {
        return HotSpotInstalledCode.executeHelper(nmethod, metaspaceMethod, arg1, arg2, arg3);
    }

}
