/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import org.junit.Test;

import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodHandleImplTest extends MethodSubstitutionTest {

    static final MethodHandle squareHandle;
    static {
        try {
            squareHandle = MethodHandles.lookup().findStatic(MethodHandleImplTest.class, "square", MethodType.methodType(int.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static int square(int a) {
        return a * a;
    }

    public static int invokeSquare() {
        try {
            return (int) squareHandle.invokeExact(6);
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Test {@code MethodHandleImpl.isCompileConstant} by effect: If it is not intrinsified,
     * {@code Invoke#Invokers.maybeCustomize(mh)} will appear in the graph.
     */
    @Test
    public void testIsCompileConstant() {
        test("invokeSquare");
        testGraph("invokeSquare");
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        conf.getPlugins().prependNodePlugin(new NodePlugin() {
            @Override
            public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
                if (field.getName().equals("fieldOffset") && b.getGraph().method().getName().equals("incrementThreadCount")) {
                    // Force a deopt in the testFlock test case
                    b.add(new DeoptimizeNode(DeoptimizationAction.InvalidateRecompile, DeoptimizationReason.Unresolved));
                    return true;
                }
                return false;
            }
        });
        return super.editGraphBuilderConfiguration(conf);
    }

    public static class ThreadFlock {
        private static final VarHandle THREAD_COUNT;

        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                THREAD_COUNT = l.findVarHandle(ThreadFlock.class, "threadCount", int.class);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }

        @SuppressWarnings("unused") private volatile int threadCount;

        public void incrementThreadCount() {
            THREAD_COUNT.getAndAdd(this, 1);
        }
    }

    @Test
    public void testFlock() {
        ThreadFlock flock = new ThreadFlock();
        for (int i = 0; i < 10_000; i++) {
            flock.incrementThreadCount();
        }
        ResolvedJavaMethod method = getResolvedJavaMethod(ThreadFlock.class, "incrementThreadCount");
        getCode(method);
    }
}
