/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import static com.oracle.graal.debug.DelegatingDebugConfig.Feature.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.compiler.CompilerThreadFactory.DebugConfigAccess;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.VerifyPhase.VerificationError;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.phases.verify.*;
import com.oracle.graal.printer.*;
import com.oracle.graal.runtime.*;
import com.oracle.graal.test.*;

/**
 * Checks that all classes in graal.jar (which must be on the class path) comply with global
 * invariants such as using {@link Object#equals(Object)} to compare certain types instead of
 * identity comparisons.
 */
public class CheckGraalInvariants extends GraalTest {

    @Test
    public void test() {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();

        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(GraphBuilderConfiguration.getEagerDefault()));
        HighTierContext context = new HighTierContext(providers, new Assumptions(false), null, graphBuilderSuite, OptimisticOptimizations.NONE);

        Assume.assumeTrue(VerifyPhase.class.desiredAssertionStatus());

        String bootclasspath = System.getProperty("sun.boot.class.path");
        Assert.assertNotNull("Cannot find value of boot class path", bootclasspath);

        bootclasspath.split(File.pathSeparator);

        String graalJar = null;
        for (String e : bootclasspath.split(File.pathSeparator)) {
            if (e.endsWith("graal.jar")) {
                graalJar = e;
                break;
            }
        }
        Assert.assertNotNull("Could not find graal.jar on boot class path: " + bootclasspath, graalJar);

        final List<String> classNames = new ArrayList<>();
        try {
            final ZipFile zipFile = new ZipFile(new File(graalJar));
            for (final Enumeration<? extends ZipEntry> e = zipFile.entries(); e.hasMoreElements();) {
                final ZipEntry zipEntry = e.nextElement();
                String name = zipEntry.getName();
                if (name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    classNames.add(className);
                }
            }
        } catch (IOException e) {
            Assert.fail(e.toString());
        }

        // Allows a subset of methods to be checked through use of a system property
        String property = System.getProperty(CheckGraalInvariants.class.getName() + ".filters");
        String[] filters = property == null ? null : property.split(",");

        CompilerThreadFactory factory = new CompilerThreadFactory("CheckInvariantsThread", new DebugConfigAccess() {
            public GraalDebugConfig getDebugConfig() {
                return DebugEnvironment.initialize(System.out);
            }
        });
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(availableProcessors, availableProcessors, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), factory);

        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        for (String className : classNames) {
            try {
                Class<?> c = Class.forName(className, false, CheckGraalInvariants.class.getClassLoader());
                for (Method m : c.getDeclaredMethods()) {
                    if (Modifier.isNative(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())) {
                        // ignore
                    } else {
                        String methodName = className + "." + m.getName();
                        if (matches(filters, methodName)) {
                            executor.execute(() -> {
                                StructuredGraph graph = new StructuredGraph(metaAccess.lookupJavaMethod(m));
                                try (DebugConfigScope s = Debug.setConfig(new DelegatingDebugConfig().disable(INTERCEPT))) {
                                    graphBuilderSuite.apply(graph, context);
                                    // update phi stamps
                                    graph.getNodes().filter(PhiNode.class).forEach(PhiNode::inferStamp);
                                    checkGraph(context, graph);
                                } catch (VerificationError e) {
                                    errors.add(e.getMessage());
                                } catch (LinkageError e) {
                                    // suppress linkages errors resulting from eager resolution
                                } catch (BailoutException e) {
                                    // Graal bail outs on certain patterns in Java bytecode (e.g.,
                                    // unbalanced monitors introduced by jacoco).
                                } catch (Throwable e) {
                                    StringWriter sw = new StringWriter();
                                    e.printStackTrace(new PrintWriter(sw));
                                    errors.add(String.format("Error while checking %s:%n%s", methodName, sw));
                                }
                            });
                        }
                    }
                }

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e1) {
            throw new RuntimeException(e1);
        }

        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            String nl = String.format("%n");
            for (String e : errors) {
                if (msg.length() != 0) {
                    msg.append(nl);
                }
                msg.append(e);
            }
            Assert.fail(msg.toString());
        }
    }

    /**
     * Checks the invariants for a single graph.
     */
    private static void checkGraph(HighTierContext context, StructuredGraph graph) {
        new VerifyUsageWithEquals(Value.class).apply(graph, context);
        new VerifyUsageWithEquals(Register.class).apply(graph, context);
        new VerifyUsageWithEquals(JavaType.class).apply(graph, context);
        new VerifyUsageWithEquals(JavaMethod.class).apply(graph, context);
        new VerifyUsageWithEquals(JavaField.class).apply(graph, context);
        new VerifyUsageWithEquals(LIRKind.class).apply(graph, context);
        new VerifyDebugUsage().apply(graph, context);
    }

    private static boolean matches(String[] filters, String s) {
        if (filters == null || filters.length == 0) {
            return true;
        }
        for (String filter : filters) {
            if (s.contains(filter)) {
                return true;
            }
        }
        return false;
    }
}
