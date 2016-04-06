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
package com.oracle.graal.replacements.test;

import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.nodes.UnwindNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.BytecodeExceptionNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;

public abstract class BytecodeExceptionTest extends GraalCompilerTest {

    protected boolean throwBytecodeException(GraphBuilderContext b, Class<? extends Throwable> exception, ValueNode... arguments) {
        BytecodeExceptionNode exceptionNode = b.add(new BytecodeExceptionNode(b.getMetaAccess(), exception, arguments));
        b.add(new UnwindNode(exceptionNode));
        return true;
    }

    protected abstract void registerPlugin(InvocationPlugins plugins);

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        GraphBuilderConfiguration ret = super.editGraphBuilderConfiguration(conf);
        registerPlugin(ret.getPlugins().getInvocationPlugins());
        return ret;
    }
}
