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
package jdk.graal.compiler.replacements.test;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Checks the deferred registration implementation shared with the Native Image runtime. */
public class DeferredInvocationPluginsTest extends GraalCompilerTest {
    /** A lookup must execute pending registrations exactly once, including after closing again. */
    @Test
    public void lookupExecutesDeferredRegistrationOnce() throws ReflectiveOperationException {
        InvocationPlugins plugins = new InvocationPlugins();
        AtomicInteger executions = new AtomicInteger();
        plugins.defer(() -> {
            executions.incrementAndGet();
            var registration = new InvocationPlugins.Registration(plugins, Math.class);
            registration.register(new InvocationPlugin.InlineOnlyInvocationPlugin("sin", double.class) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod target, Receiver receiver, ValueNode value) {
                    b.addPush(JavaKind.Double, UnaryMathIntrinsicNode.create(value, UnaryMathIntrinsicNode.UnaryOperation.SIN));
                    return true;
                }
            });
        });
        var method = getMetaAccess().lookupJavaMethod(Math.class.getDeclaredMethod("sin", double.class));
        Assert.assertEquals(0, executions.get());
        InvocationPlugin plugin = plugins.lookupInvocation(method, getInitialOptions());
        Assert.assertNotNull(plugin);
        Assert.assertEquals(1, executions.get());
        plugins.closeRegistration();
        Assert.assertSame(plugin, plugins.lookupInvocation(method, getInitialOptions()));
        Assert.assertEquals(1, executions.get());
    }

    /** A failed registration must not run again or expose a partially registered set as complete. */
    @Test
    public void registrationFailureIsRetained() {
        InvocationPlugins plugins = new InvocationPlugins();
        AtomicInteger executions = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("registration failed");
        plugins.defer(() -> {
            executions.incrementAndGet();
            throw failure;
        });
        GraalError first = Assert.assertThrows(GraalError.class, plugins::closeRegistration);
        GraalError second = Assert.assertThrows(GraalError.class, plugins::closeRegistration);
        Assert.assertSame(failure, first.getCause());
        Assert.assertSame(failure, second.getCause());
        Assert.assertEquals(1, executions.get());
    }

    /** Recursive lookup while registering must report an error instead of recursing indefinitely. */
    @Test
    public void recursiveRegistrationIsRejected() {
        InvocationPlugins plugins = new InvocationPlugins();
        plugins.defer(plugins::closeRegistration);
        GraalError failure = Assert.assertThrows(GraalError.class, plugins::closeRegistration);
        Assert.assertTrue(failure.getCause().getMessage().contains("recursively performing deferred registration"));
    }
}
