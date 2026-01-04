/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.inlining;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.StructuredGraph.Builder;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.BlackholeNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that arguments to inlined function calls with less precise stamps than the inlined
 * function's parameters are wrapped by PiNodes.
 */
public class ImpreciseArgumentStampInliningTest extends GraalCompilerTest {

    private static final class InlineMethodHolder {
        static void inlineMe(UnresolveableClass argument) {
            GraalDirectives.blackhole(argument);
        }

        static UnresolveableClass doNotInlineMe() {
            return new UnresolveableClass();
        }

        @SuppressWarnings("all")
        public static void snippet() {
            InlineMethodHolder.inlineMe(InlineMethodHolder.doNotInlineMe());
        }
    }

    // Will be unresolved during bytecode parsing, and resolved during inlining.
    private static final class UnresolveableClass {
    }

    @Test
    public void testAddsPiForUnresolvedArgument() {
        // Leave UnresolveableClass unresolved
        StructuredGraph graph = getGraph("snippet", CollectionsUtil.setOf(InlineMethodHolder.class.getName()));
        // Check that "inlineMe" was inlined successfully.
        Assert.assertEquals(1, graph.getNodes().filter(BlackholeNode.class).count());
        ValueNode inlinedCallArgument = graph.getNodes().filter(BlackholeNode.class).first().getValue();
        // Check that the original argument is wrapped by a PiNode that refines its stamp to the
        // expected (resolved) type.
        Assert.assertTrue("Call argument with less precise stamp than parameter is not wrapped by PiNode", inlinedCallArgument instanceof PiNode);
        JavaType expectedType = getMetaAccess().lookupJavaType(UnresolveableClass.class);
        JavaType actualType = inlinedCallArgument.stamp(NodeView.DEFAULT).javaType(getMetaAccess());
        Assert.assertEquals("Inlined call argument is not of the correct type", expectedType.getName(), actualType.getName());
    }

    @Test
    public void testNoPiForResolvedArgument() {
        // Resolve and initialize both classes
        StructuredGraph graph = getGraph("snippet", CollectionsUtil.setOf(InlineMethodHolder.class.getName(), UnresolveableClass.class.getName()));
        // Check that "inlineMe" was inlined successfully.
        Assert.assertEquals(1, graph.getNodes().filter(BlackholeNode.class).count());
        ValueNode inlinedCallArgument = graph.getNodes().filter(BlackholeNode.class).first().getValue();
        // Check that the original argument is not wrapped by a PiNode
        Assert.assertTrue("Call argument with same stamp as parameter is unnecessarily wrapped by PiNode", inlinedCallArgument instanceof InvokeNode);
        JavaType expectedType = getMetaAccess().lookupJavaType(UnresolveableClass.class);
        JavaType actualType = inlinedCallArgument.stamp(NodeView.DEFAULT).javaType(getMetaAccess());
        Assert.assertEquals("Inlined call argument is not of the correct type", expectedType.getName(), actualType.getName());
    }

    /**
     * Can be used to control the class loading process, as it will only resolve classes whose name
     * is added to {@link #resolveableClasses}, returning {@code null} for all others.
     */
    private static class ManualClassLoader extends URLClassLoader {
        public Set<String> resolveableClasses = new EconomicHashSet<>();

        ManualClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException {
            if (resolveableClasses.contains(name)) {
                return super.findClass(name);
            }
            return null;
        }
    }

    private static ManualClassLoader getClassLoader(Set<String> initialLoadedClasses) {
        Class<?> thisClass = ImpreciseArgumentStampInliningTest.class;
        final ClassLoader parent = thisClass.getClassLoader();
        final URL[] urls = new URL[]{requireNonNull(thisClass.getProtectionDomain().getCodeSource().getLocation())};
        ManualClassLoader loader = new ManualClassLoader(urls, parent);
        loader.resolveableClasses.addAll(initialLoadedClasses);
        for (String className : initialLoadedClasses) {
            try {
                // Force class initialization
                loader.loadClass(className).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // Swallow exception
            }
        }
        return loader;
    }

    private StructuredGraph getGraph(final String snippet, Set<String> initialLoadedClasses) {
        DebugContext debug = getDebugContext(new OptionValues(getInitialOptions(), BytecodeParserOptions.InlineDuringParsing, false));
        try (ManualClassLoader loader = getClassLoader(initialLoadedClasses);
                        DebugContext.Scope _ = debug.scope("InliningTest", new DebugDumpScope(snippet, true))) {
            Class<?> holderClass = loader.loadClass(InlineMethodHolder.class.getName());
            ResolvedJavaMethod method = getResolvedJavaMethod(holderClass, snippet);
            Builder builder = builder(method, AllowAssumptions.YES, debug);
            StructuredGraph graph = parse(builder, getDefaultGraphBuilderSuite());
            /*
             * At this point, the return stamp of doNotInlineMe is a plain Object stamp, since
             * UnresolveableClass is unresolved.
             */
            try (DebugContext.Scope _ = debug.scope("Inlining", graph)) {
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, getDefaultHighTierContext());

                /*
                 * Force inline "inlineMe", and disable inlining for other methods.
                 */
                Map<Invoke, Double> hints = new EconomicHashMap<>();
                for (Invoke invoke : graph.getInvokes()) {
                    if (invoke.getTargetMethod().getName().equals("inlineMe")) {
                        hints.put(invoke, 1000d);
                    } else {
                        hints.put(invoke, -1000d);
                    }
                }
                loader.resolveableClasses.add(UnresolveableClass.class.getName());
                /*
                 * With eager resolving and the set of resolveable classes updated, the inlined
                 * method will have succesfully resolved UnresolveableClass, but the caller will
                 * not.
                 */
                createInliningPhase(hints, createCanonicalizerPhase()).apply(graph, getEagerHighTierContext());
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                return graph;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }
}
