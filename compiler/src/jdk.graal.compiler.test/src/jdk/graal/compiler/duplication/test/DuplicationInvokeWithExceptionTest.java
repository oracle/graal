/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.test;

import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
import static jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;

import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DuplicationInvokeWithExceptionTest extends GraalCompilerTest {

    private static boolean exceptionRaised;

    public static int SideEffectI;
    public static int SideEffectI1;

    public static void invokeWithException() {
        if (!exceptionRaised) {
            exceptionRaised = true;
            return;
        }
        throw new Error("Should never re-execute this method");
    }

    public static int snippetInvokeWithException(int a, int b) {
        int phi = a;
        if (a > b) {
            phi = 1;
            SideEffectI = a;
        } else {
            SideEffectI = b;
        }
        invokeWithException();
        if (phi > 1) {
            phi = SideEffectI;
            if (exceptionRaised) {
                GraalDirectives.deoptimize();
            }
        } else {
            phi = SideEffectI1;
            if (exceptionRaised) {
                GraalDirectives.deoptimize();
            }
        }
        GraalDirectives.controlFlowAnchor();
        SideEffectI = phi;
        return a * b + phi;
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        conf.getPlugins().prependInlineInvokePlugin(new InlineInvokePlugin() {

            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (method.getName().equals("invokeWithException")) {
                    return DO_NOT_INLINE_WITH_EXCEPTION;
                }
                return DO_NOT_INLINE_NO_EXCEPTION;
            }
        });
        return super.editGraphBuilderConfiguration(conf);
    }

    @Test
    public void test01() {
        OptionValues options = new OptionValues(getInitialOptions(), PriorityInliningPhase.Options.UsePriorityInlining, false, HighTier.Options.Inline, false);
        Result r = executeActual(options, getResolvedJavaMethod("snippetInvokeWithException"), null, 1, 2);
        Throwable t = r.exception;
        if (t instanceof Error) {
            Assert.fail(t.getMessage());
        }
    }

}
