/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.junit.Test;

import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Exercise handling of unbalanced monitor operations by the parser. Algorithmically Graal assumes
 * that locks are statically block structured but that isn't something enforced by the bytecodes. In
 * HotSpot a dataflow is performed to ensure they are properly structured and methods with
 * unstructured locking aren't compiled and fall back to the interpreter. Having the Graal parser
 * handle this directly is simplifying for targets of Graal since they don't have to provide a data
 * flow that checks this property.
 * </p>
 * Since [GR-51446], Graal defers some checks to run time, e.g., if it cannot be proven statically
 * that an unlocked object matches the object on top of the monitor stack.
 */
public class UnbalancedMonitorsTest extends GraalCompilerTest implements CustomizedBytecodePattern {
    private static final String CLASS_NAME = UnbalancedMonitorsTest.class.getName();
    private static final String INNER_CLASS_NAME = CLASS_NAME + "$UnbalancedMonitors";

    @Test
    public void runWrongOrder() throws Exception {
        Class<?> clazz = getClass(INNER_CLASS_NAME);
        ResolvedJavaMethod method = getResolvedJavaMethod(clazz, "wrongOrder");
        Object instance = clazz.getName();
        InstalledCode code = getCode(method);
        code.executeVarargs(instance, new Object(), new Object());
        assertTrue("Deopt expected due to unlocked object not matching top of monitor stack.", !code.isValid());
    }

    @Test
    public void runTooFewExits() throws Exception {
        checkForBailout("tooFewExits");
    }

    @Test
    public void runTooManyExits() throws Exception {
        checkForBailout("tooManyExits");
    }

    @Test
    public void runTooFewExitsExceptional() throws Exception {
        checkForBailout("tooFewExitsExceptional");
    }

    @Test
    public void runTooManyExitsExceptional() throws Exception {
        checkForBailout("tooManyExitsExceptional");
    }

    private void checkForBailout(String name) throws ClassNotFoundException {
        ResolvedJavaMethod method = getResolvedJavaMethod(getClass(INNER_CLASS_NAME), name);
        try {
            OptionValues options = getInitialOptions();
            StructuredGraph graph = new StructuredGraph.Builder(options, getDebugContext(options, null, method)).method(method).build();
            Plugins plugins = new Plugins(new InvocationPlugins());
            GraphBuilderConfiguration graphBuilderConfig = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withUnresolvedIsError(true);
            OptimisticOptimizations optimisticOpts = OptimisticOptimizations.NONE;

            GraphBuilderPhase.Instance graphBuilder = new TestGraphBuilderPhase.Instance(getProviders(), graphBuilderConfig, optimisticOpts, null);
            graphBuilder.apply(graph);
        } catch (BailoutException e) {
            if (e.getMessage().toLowerCase().contains("unstructured locking") ||
                            // tooFewExits is caught by the FrameStateBuilder
                            e.getMessage().contains("will underflow the bytecode stack")) {
                return;
            }
            throw e;
        }
        assertTrue("should have bailed out", false);
    }

    // @formatter:off
    // Template class used with Bytecode Outline to generate ASM code
    //    public static class UnbalancedMonitors {
    //
    //        public UnbalancedMonitors() {
    //        }
    //
    //        public Object wrongOrder(Object a, Object b) {
    //            synchronized (a) {
    //                synchronized (b) {
    //                    return b;
    //                }
    //            }
    //        }
    //
    //        public Object tooFewExits(Object a, Object b) {
    //            synchronized (a) {
    //                synchronized (b) {
    //                    return b;
    //                }
    //            }
    //        }
    //
    //        public boolean tooFewExitsExceptional(Object a, Object b) {
    //            synchronized (a) {
    //                synchronized (b) {
    //                    return b.equals(a);
    //                }
    //            }
    //        }
    //    }
    // @formatter:on
    @Override
    public byte[] generateClass(String className) {
        // @formatter:off
        return ClassFile.of().build(ClassDesc.of(className), classBuilder -> classBuilder
                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, b -> b
                                        .aload(0)
                                        .invokespecial(CD_Object, INIT_NAME, MTD_void)
                                        .return_())
                        .withMethodBody("wrongOrder", MethodTypeDesc.of(CD_Object, CD_Object, CD_Object), ACC_PUBLIC, UnbalancedMonitorsTest::visitWrongOrder)
                        .withMethodBody("tooFewExits", MethodTypeDesc.of(CD_boolean, CD_Object, CD_Object), ACC_PUBLIC, b -> visitBlockStructured(b, true, false))
                        .withMethodBody("tooManyExits", MethodTypeDesc.of(CD_boolean, CD_Object, CD_Object), ACC_PUBLIC, b -> visitBlockStructured(b, true, true))
                        .withMethodBody("tooFewExitsExceptional", MethodTypeDesc.of(CD_boolean, CD_Object, CD_Object), ACC_PUBLIC, b -> visitBlockStructured(b, false, false))
                        .withMethodBody("tooManyExitsExceptional", MethodTypeDesc.of(CD_boolean, CD_Object, CD_Object), ACC_PUBLIC, b -> visitBlockStructured(b, false, true)));
        // @formatter:on
    }

    private static void visitBlockStructured(CodeBuilder b, boolean normalReturnError, boolean tooMany) {
        // Generate too many or too few exits down either the normal or exceptional return paths
        int exceptionalExitCount = normalReturnError ? 1 : (tooMany ? 2 : 0);
        int normalExitCount = normalReturnError ? (tooMany ? 2 : 0) : 1;

        Label l0 = b.newLabel();
        Label l1 = b.newLabel();
        Label l2 = b.newLabel();
        Label l3 = b.newLabel();
        Label l4 = b.newLabel();
        Label l5 = b.newLabel();
        Label l6 = b.newLabel();
        Label l7 = b.newLabel();
        Label l8 = b.newLabel();

        // @formatter:off
        b.labelBinding(l8)
                        .aload(1)
                        .dup()
                        .astore(3)
                        .monitorenter()
                        .labelBinding(l4)
                        .aload(2)
                        .dup()
                        .astore(4)
                        .monitorenter()
                        .labelBinding(l0)
                        .aload(2)
                        .aload(1)
                        .invokevirtual(CD_Object, "equals", MethodTypeDesc.of(CD_boolean, CD_Object))
                        .aload(4)
                        .monitorexit()
                        .labelBinding(l1);
        for (int i = 0; i < normalExitCount; i++) {
            b.aload(3).monitorexit();
        }

        b.labelBinding(l5)
                        .ireturn()
                        .labelBinding(l2)
                        .aload(4)
                        .monitorexit()
                        .labelBinding(l3)
                        .athrow()
                        .labelBinding(l6);

        for (int i = 0; i < exceptionalExitCount; i++) {
            b.aload(3).monitorexit();
        }

        b.labelBinding(l7)
                        .athrow()
                        .exceptionCatchAll(l0, l1, l2)
                        .exceptionCatchAll(l2, l3, l2)
                        .exceptionCatchAll(l4, l5, l6)
                        .exceptionCatchAll(l2, l7, l6);
        // @formatter:on
    }

    private static void visitWrongOrder(CodeBuilder b) {
        Label l0 = b.newLabel();
        Label l1 = b.newLabel();
        Label l2 = b.newLabel();
        Label l3 = b.newLabel();
        Label l4 = b.newLabel();
        Label l5 = b.newLabel();
        Label l6 = b.newLabel();
        Label l7 = b.newLabel();
        Label l8 = b.newLabel();

        // @formatter:off
        b.labelBinding(l8)
                        .aload(1)
                        .dup()
                        .astore(3)
                        .monitorenter()
                        .labelBinding(l4)
                        .aload(2)
                        .dup()
                        .astore(4)
                        .monitorenter()
                        .labelBinding(l0)
                        .aload(2)
                        .aload(3)
                        .monitorexit()
                        .labelBinding(l1)
                        .aload(4)
                        .monitorexit()
                        .labelBinding(l5)
                        .areturn()
                        .labelBinding(l2)
                        .aload(4)
                        .monitorexit()
                        .labelBinding(l3)
                        .athrow()
                        .labelBinding(l6)
                        .aload(3)
                        .monitorexit()
                        .labelBinding(l7)
                        .athrow()
                        .exceptionCatchAll(l0, l1, l2)
                        .exceptionCatchAll(l2, l3, l2)
                        .exceptionCatchAll(l4, l5, l6)
                        .exceptionCatchAll(l2, l7, l6);
        // @formatter:on
    }
}
