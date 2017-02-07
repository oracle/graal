/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class StringIndexOfConstantTest extends StringIndexOfTestBase {
    Object[] constantArgs;

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        if (constantArgs != null) {
            ConstantBindingParameterPlugin constantBinding = new ConstantBindingParameterPlugin(constantArgs, this.getMetaAccess(), this.getSnippetReflection());
            conf.getPlugins().appendParameterPlugin(constantBinding);
        }
        return super.editGraphBuilderConfiguration(conf);
    }

    @Test
    public void testStringIndexOfConstant() {
        test("testStringIndexOf", new Object[]{this.sourceString, this.constantString});
    }

    @Test
    public void testStringIndexOfConstantOffset() {
        test("testStringIndexOfOffset", new Object[]{this.sourceString, this.constantString, Math.min(sourceString.length() - 1, 3)});
    }

    @Test
    public void testStringBuilderIndexOfConstant() {
        test("testStringBuilderIndexOf", new Object[]{new StringBuilder(this.sourceString), this.constantString});
    }

    @Override
    protected Result test(String name, Object... args) {
        constantArgs = new Object[args.length + 1];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == constantString) {
                constantArgs[i + 1] = constantString;
            }
        }
        return super.test(name, args);
    }

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph0, boolean forceCompile) {
        // Force recompile if constant binding should be done
        return getCode(installedCodeOwner, graph0, true, false);
    }
}
