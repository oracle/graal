/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
/*
 */
package org.graalvm.compiler.jtt.threads;

import static org.graalvm.compiler.debug.Debug.INFO_LEVEL;
import static org.graalvm.compiler.debug.DelegatingDebugConfig.Feature.DUMP_METHOD;
import static org.graalvm.compiler.debug.DelegatingDebugConfig.Level.DUMP;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DelegatingDebugConfig;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class Object_wait03 extends JTTTest {

    @Rule public TestRule timeout = new DisableOnDebug(Timeout.seconds(20));

    private static class TestClass implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ex) {

            }
            synchronized (object) {
                done = true;
                object.notifyAll();
            }
        }
    }

    static volatile boolean done;
    static final Object object = new Object();
    static int sleep;

    public static boolean test(int i) throws InterruptedException {
        done = false;
        sleep = i * 200;
        synchronized (object) {
            new Thread(new TestClass()).start();
            dowait();
        }
        return done;
    }

    private static void dowait() throws InterruptedException {
        synchronized (object) {
            while (!done) {
                object.wait(200);
            }
        }
    }

    @SuppressWarnings("try")
    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        if (!GraalDebugConfig.Options.Dump.hasBeenSet(getInitialOptions())) {
            // Dump the compilation to try and determine why the compilation
            // regularly times out in the gate.
            DelegatingDebugConfig config = new DelegatingDebugConfig();
            config.override(DUMP, INFO_LEVEL).enable(DUMP_METHOD);
            try (Scope d = Debug.sandbox("DumpingCompilation", config)) {
                return super.getCode(method, graph, forceCompile, installAsDefault, options);
            } catch (Throwable t2) {
                throw Debug.handle(t2);
            }
        } else {
            return super.getCode(method, graph, forceCompile, installAsDefault, options);
        }
    }

    private void run(int i) throws Throwable {
        initializeForTimeout();
        runTest("test", i);
    }

    @Test
    public void run0() throws Throwable {
        run(0);
    }

    @Test
    public void run1() throws Throwable {
        run(1);
    }

    @Test
    public void run2() throws Throwable {
        run(2);
    }

}
