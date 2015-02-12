/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import static java.lang.reflect.Modifier.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;

public class InstalledCodeExecuteHelperTest extends GraalCompilerTest {

    private static final int ITERATIONS = 100000;
    Object[] argsToBind;

    @Test
    public void test1() throws InvalidInstalledCodeException {
        final ResolvedJavaMethod fooMethod = getResolvedJavaMethod("foo");
        final HotSpotInstalledCode fooCode = (HotSpotInstalledCode) getCode(fooMethod);

        argsToBind = new Object[]{fooCode};

        final ResolvedJavaMethod benchmarkMethod = getResolvedJavaMethod("benchmark");
        final HotSpotInstalledCode installedBenchmarkCode = (HotSpotInstalledCode) getCode(benchmarkMethod);

        Assert.assertEquals(Integer.valueOf(42), benchmark(fooCode));

        Assert.assertEquals(Integer.valueOf(42), installedBenchmarkCode.executeVarargs(argsToBind[0]));

    }

    public static Integer benchmark(HotSpotInstalledCode code) throws InvalidInstalledCodeException {
        int val = 0;
        for (int j = 0; j < 100; j++) {
            for (int i = 0; i < ITERATIONS; i++) {
                val = (Integer) code.executeVarargs();
            }
        }
        return val;
    }

    public static Integer foo() {
        return 42;
    }

    @Override
    protected StructuredGraph parseEager(ResolvedJavaMethod m, AllowAssumptions allowAssumptions) {
        StructuredGraph graph = super.parseEager(m, allowAssumptions);
        if (argsToBind != null) {
            Object receiver = isStatic(m.getModifiers()) ? null : this;
            Object[] args = argsWithReceiver(receiver, argsToBind);
            JavaType[] parameterTypes = m.toParameterTypes();
            assert parameterTypes.length == args.length;
            for (int i = 0; i < argsToBind.length; i++) {
                ParameterNode param = graph.getParameter(i);
                JavaConstant c = HotSpotObjectConstantImpl.forBoxedValue(parameterTypes[i].getKind(), argsToBind[i]);
                ConstantNode replacement = ConstantNode.forConstant(c, getMetaAccess(), graph);
                param.replaceAtUsages(replacement);
            }
        }
        return graph;
    }
}
