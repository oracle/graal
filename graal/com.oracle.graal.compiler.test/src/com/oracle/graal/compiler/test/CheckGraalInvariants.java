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

import static com.oracle.graal.debug.DelegatingDebugConfig.Feature.INTERCEPT;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.LocationIdentity;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.compiler.CompilerThreadFactory;
import com.oracle.graal.compiler.CompilerThreadFactory.DebugConfigAccess;
import com.oracle.graal.compiler.common.type.ArithmeticOpTable;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.debug.DebugEnvironment;
import com.oracle.graal.debug.DelegatingDebugConfig;
import com.oracle.graal.debug.GraalDebugConfig;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.java.GraphBuilderPhase;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.PhiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.PhaseSuite;
import com.oracle.graal.phases.VerifyPhase;
import com.oracle.graal.phases.VerifyPhase.VerificationError;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;
import com.oracle.graal.phases.verify.VerifyCallerSensitiveMethods;
import com.oracle.graal.phases.verify.VerifyDebugUsage;
import com.oracle.graal.phases.verify.VerifyUsageWithEquals;
import com.oracle.graal.runtime.RuntimeProvider;
import com.oracle.graal.test.GraalTest;

/**
 * Checks that all classes in *graal*.jar and *jvmci*.jar entries on the boot class path comply with
 * global invariants such as using {@link Object#equals(Object)} to compare certain types instead of
 * identity comparisons.
 */
public class CheckGraalInvariants extends GraalTest {

    private static boolean shouldVerifyEquals(ResolvedJavaMethod m) {
        if (m.getName().equals("identityEquals")) {
            ResolvedJavaType c = m.getDeclaringClass();
            if (c.getName().equals("Ljdk/vm/ci/meta/AbstractValue;") || c.getName().equals("jdk/vm/ci/meta/Value")) {
                return false;
            }
        }

        return true;
    }

    private static boolean shouldProcess(String classpathEntry) {
        if (classpathEntry.endsWith(".jar")) {
            String name = new File(classpathEntry).getName();
            return name.contains("jvmci") || name.contains("graal");
        }
        return false;
    }

    @Test
    @SuppressWarnings("try")
    public void test() {
        RuntimeProvider rt = Graal.getRequiredCapability(RuntimeProvider.class);
        Providers providers = rt.getHostBackend().getProviders();
        MetaAccessProvider metaAccess = providers.getMetaAccess();

        PhaseSuite<HighTierContext> graphBuilderSuite = new PhaseSuite<>();
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getEagerDefault(new Plugins(new InvocationPlugins(metaAccess)));
        graphBuilderSuite.appendPhase(new GraphBuilderPhase(config));
        HighTierContext context = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.NONE);

        Assume.assumeTrue(VerifyPhase.class.desiredAssertionStatus());

        String bootclasspath = System.getProperty("sun.boot.class.path");
        Assert.assertNotNull("Cannot find value of boot class path", bootclasspath);

        bootclasspath.split(File.pathSeparator);

        final List<String> classNames = new ArrayList<>();
        for (String path : bootclasspath.split(File.pathSeparator)) {
            if (shouldProcess(path)) {
                try {
                    final ZipFile zipFile = new ZipFile(new File(path));
                    for (final Enumeration<? extends ZipEntry> entry = zipFile.entries(); entry.hasMoreElements();) {
                        final ZipEntry zipEntry = entry.nextElement();
                        String name = zipEntry.getName();
                        if (name.endsWith(".class")) {
                            String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                            classNames.add(className);
                        }
                    }
                } catch (IOException ex) {
                    Assert.fail(ex.toString());
                }
            }
        }
        Assert.assertFalse("Could not find graal jars on boot class path: " + bootclasspath, classNames.isEmpty());

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
        // Order outer classes before the inner classes
        classNames.sort((String a, String b) -> a.compareTo(b));
        // Initialize classes in single thread to avoid deadlocking issues during initialization
        List<Class<?>> classes = initializeClasses(classNames);
        for (Class<?> c : classes) {
            String className = c.getName();
            executor.execute(() -> {
                try {
                    checkClass(c, metaAccess);
                } catch (Throwable e) {
                    errors.add(String.format("Error while checking %s:%n%s", className, printStackTraceToString(e)));
                }
            });

            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isNative(m.getModifiers()) || Modifier.isAbstract(m.getModifiers())) {
                    // ignore
                } else {
                    String methodName = className + "." + m.getName();
                    if (matches(filters, methodName)) {
                        executor.execute(() -> {
                            ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                            StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO);
                            try (DebugConfigScope s = Debug.setConfig(new DelegatingDebugConfig().disable(INTERCEPT)); Debug.Scope ds = Debug.scope("CheckingGraph", graph, method)) {
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
                                errors.add(String.format("Error while checking %s:%n%s", methodName, printStackTraceToString(e)));
                            }
                        });
                    }
                }
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

    private static List<Class<?>> initializeClasses(List<String> classNames) {
        List<Class<?>> classes = new ArrayList<>(classNames.size());
        for (String className : classNames) {
            try {
                Class<?> c = Class.forName(className, true, CheckGraalInvariants.class.getClassLoader());
                classes.add(c);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return classes;
    }

    /**
     * @param metaAccess
     */
    private static void checkClass(Class<?> c, MetaAccessProvider metaAccess) {
        if (Node.class.isAssignableFrom(c)) {
            if (c.getAnnotation(NodeInfo.class) == null) {
                throw new AssertionError(String.format("Node subclass %s requires %s annotation", c.getName(), NodeClass.class.getSimpleName()));
            }
        }
    }

    /**
     * Checks the invariants for a single graph.
     */
    private static void checkGraph(HighTierContext context, StructuredGraph graph) {
        if (shouldVerifyEquals(graph.method())) {
            new VerifyUsageWithEquals(Value.class).apply(graph, context);
            new VerifyUsageWithEquals(Register.class).apply(graph, context);
            new VerifyUsageWithEquals(RegisterCategory.class).apply(graph, context);
            new VerifyUsageWithEquals(JavaType.class).apply(graph, context);
            new VerifyUsageWithEquals(JavaMethod.class).apply(graph, context);
            new VerifyUsageWithEquals(JavaField.class).apply(graph, context);
            new VerifyUsageWithEquals(LocationIdentity.class).apply(graph, context);
            new VerifyUsageWithEquals(LIRKind.class).apply(graph, context);
            new VerifyUsageWithEquals(ArithmeticOpTable.class).apply(graph, context);
            new VerifyUsageWithEquals(ArithmeticOpTable.Op.class).apply(graph, context);
        }
        new VerifyDebugUsage().apply(graph, context);
        new VerifyCallerSensitiveMethods().apply(graph, context);
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

    private static String printStackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
