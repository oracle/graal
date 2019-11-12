/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.debug.DebugOptions.DumpOnError;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ForeignCallTest extends GraalCompilerTest {
    public static final ForeignCallDescriptor TEST_CALL = new ForeignCallDescriptor("test", int.class, int.class);

    public static class A {
        static void foo(@SuppressWarnings("unused") int phi) {

        }

        static void bar(@SuppressWarnings("unused") int phi) {

        }

        static void barReexecutable(@SuppressWarnings("unused") int phi) {

        }

        static void crep(@SuppressWarnings("unused") int phi) {

        }
    }

    @ClassSubstitution(A.class)
    public static class ASubstitutions {

        /*
         * Invalid: two consecutive states, something can float in between something that is not a
         * framestate.
         */
        @MethodSubstitution
        static void foo(int phi) {
            testDeopt(phi);
            // invalid two consecutive calls
            testDeopt(phi);
        }

        /*
         * Invalid: two consecutive states, something can float in between something that is not a
         * framestate. Same applies for non-deopting framestates if they are not re-executable. If
         * they are, we are good.
         */
        @MethodSubstitution
        static void bar(int phi) {
            testNonDeopting(phi);
            testNonDeopting(phi);
        }

        /*
         * Invalid: two consecutive states, something can float in between something that is not a
         * framestate. Same applies for non-deopting framestates if they are not re-executable. If
         * they are, we are good.
         */

        @MethodSubstitution
        static void barReexecutable(int phi) {
            testPureReexectuable(phi);
            testPureReexectuable(phi);
        }

        @MethodSubstitution
        static void crep(int phi) {
            if (SideEffect == 0) {
                testDeopt(phi);
            } else {
                CurrentJavaThreadNode.get().writeByte(0, (byte) 0);
                testDeopt(phi);
            }
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {

        invocationPlugins.register(new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ForeignCallsProvider foreignCalls = new ForeignCallsProvider() {

                    @Override
                    public LIRKind getValueKind(JavaKind javaKind) {
                        return LIRKind.fromJavaKind(getTarget().arch, javaKind);
                    }

                    @Override
                    public ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
                        return null;
                    }

                    @Override
                    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public boolean isGuaranteedSafepoint(ForeignCallDescriptor descriptor) {
                        return true;
                    }

                    @Override
                    public boolean isAvailable(ForeignCallDescriptor descriptor) {
                        return true;
                    }

                    @Override
                    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
                        return new LocationIdentity[]{LocationIdentity.any()};
                    }

                    @Override
                    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
                        return true;
                    }
                };
                ForeignCallNode node = new ForeignCallNode(foreignCalls, TEST_CALL, arg);
                node.setBci(b.bci());
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, ForeignCallTest.class, "testDeopt", int.class);
        invocationPlugins.register(new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ForeignCallsProvider foreignCalls = new ForeignCallsProvider() {

                    @Override
                    public LIRKind getValueKind(JavaKind javaKind) {
                        return LIRKind.fromJavaKind(getTarget().arch, javaKind);
                    }

                    @Override
                    public ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
                        return null;
                    }

                    @Override
                    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public boolean isGuaranteedSafepoint(ForeignCallDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public boolean isAvailable(ForeignCallDescriptor descriptor) {
                        return true;
                    }

                    @Override
                    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
                        return new LocationIdentity[]{LocationIdentity.any()};
                    }

                    @Override
                    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
                        return false;
                    }
                };
                ForeignCallNode node = new ForeignCallNode(foreignCalls, TEST_CALL, arg);
                node.setBci(b.bci());
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, ForeignCallTest.class, "testNonDeopting", int.class);
        invocationPlugins.register(new InvocationPlugin() {

            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                ForeignCallsProvider foreignCalls = new ForeignCallsProvider() {

                    @Override
                    public LIRKind getValueKind(JavaKind javaKind) {
                        return LIRKind.fromJavaKind(getTarget().arch, javaKind);
                    }

                    @Override
                    public ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
                        return null;
                    }

                    @Override
                    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
                        return true;
                    }

                    @Override
                    public boolean isGuaranteedSafepoint(ForeignCallDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public boolean isAvailable(ForeignCallDescriptor descriptor) {
                        return true;
                    }

                    @Override
                    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
                        return new LocationIdentity[]{LocationIdentity.any()};
                    }

                    @Override
                    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
                        return false;
                    }
                };
                ForeignCallNode node = new ForeignCallNode(foreignCalls, TEST_CALL, arg);
                node.setBci(b.bci());
                b.addPush(JavaKind.Int, node);
                return true;
            }
        }, ForeignCallTest.class, "testPureReexectuable", int.class);
        ClassfileBytecodeProvider bytecodeProvider = getSystemClassLoaderBytecodeProvider();
        Registration r = new Registration(invocationPlugins, A.class, getReplacements(), bytecodeProvider);
        r.registerMethodSubstitution(ASubstitutions.class, "foo", int.class);
        r.registerMethodSubstitution(ASubstitutions.class, "bar", int.class);
        r.registerMethodSubstitution(ASubstitutions.class, "barReexecutable", int.class);
        r.registerMethodSubstitution(ASubstitutions.class, "crep", int.class);
        super.registerInvocationPlugins(invocationPlugins);
    }

    private ClassfileBytecodeProvider getSystemClassLoaderBytecodeProvider() {
        ReplacementsImpl d = (ReplacementsImpl) getReplacements();
        MetaAccessProvider metaAccess = d.getProviders().getMetaAccess();
        ClassfileBytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, d.snippetReflection, ClassLoader.getSystemClassLoader());
        return bytecodeProvider;
    }

    public static int SideEffect;

    public static int testDeopt(int value) {
        SideEffect = value;
        return value;
    }

    public static int testNonDeopting(int value) {
        return value;
    }

    public static int testPureReexectuable(int value) {
        return value;
    }

    public static void testSnippetInvalidSequential() {
        A.foo(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    public static void testNonDeoptingInvalid() {
        A.bar(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    public static void testNonDeoptingSplit() {
        A.crep(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    public static void testNonDeoptingReexectuable() {
        A.barReexecutable(SideEffect);
        if (SideEffect == 1) {
            GraalDirectives.deoptimize();
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test1() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
            StructuredGraph g = parseEager(getResolvedJavaMethod("testSnippetInvalidSequential"), AllowAssumptions.NO, options);
            Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
            s.getHighTier().apply(g, getDefaultHighTierContext());
            s.getMidTier().apply(g, getDefaultMidTierContext());
            Assert.fail();
        } catch (Throwable t) {
            if (t.getCause() instanceof GraalError && t.getMessage().contains("invalid framestate")) {
                return;
            }
            Assert.fail();
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test2() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
            StructuredGraph g = parseEager(getResolvedJavaMethod("testSnippetInvalidSequential"), AllowAssumptions.NO, options);
            Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
            s.getHighTier().apply(g, getDefaultHighTierContext());
            s.getMidTier().apply(g, getDefaultMidTierContext());
            Assert.fail();
        } catch (Throwable t) {
            if (t.getCause() instanceof GraalError && t.getMessage().contains("invalid framestate")) {
                return;
            }
            Assert.fail();
        }
    }

    @Test
    @SuppressWarnings("try")
    public void test3() {
        try (AutoCloseable c = new TTY.Filter()) {
            OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
            StructuredGraph g = parseEager(getResolvedJavaMethod("testNonDeoptingSplit"), AllowAssumptions.NO, options);
            Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
            s.getHighTier().apply(g, getDefaultHighTierContext());
            s.getMidTier().apply(g, getDefaultMidTierContext());
            Assert.fail();
        } catch (Throwable t) {
            if (t.getCause() instanceof GraalError && t.getMessage().contains("invalid framestate")) {
                return;
            }
            Assert.fail();
        }
    }

    @Test
    public void test4() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("testNonDeoptingReexectuable"), AllowAssumptions.NO);
        Suites s = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getSuites().getDefaultSuites(getInitialOptions());
        s.getHighTier().apply(g, getDefaultHighTierContext());
        s.getMidTier().apply(g, getDefaultMidTierContext());
    }

}
