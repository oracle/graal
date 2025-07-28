/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static java.lang.classfile.ClassFile.ACC_PRIVATE;
import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_short;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.java.FinalFieldBarrierNode;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.vm.annotation.Stable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Assert that a barrier is inserted at the end of a constructor that writes to Stable fields or if
 * -XX:+AlwaysSafeConstructors is specified.
 */
@AddExports("java.base/jdk.internal.vm.annotation")
public class SafeConstructionTest extends SubprocessTest implements CustomizedBytecodePattern {

    static class TestClass {
        int a;

        @Stable int b;

        TestClass() {
            this.a = 4;
        }

        TestClass(int b) {
            this.b = b;
        }

        TestClass(short a) {
            this.b = 0;
            this.a = a;
        }
    }

    @Test
    public void checkAlwaysSafeConstructors() throws NoSuchMethodException {
        Class<TestClass> clazz = TestClass.class;
        List<Constructor<TestClass>> constructors = List.of(clazz.getDeclaredConstructor(), clazz.getDeclaredConstructor(int.class), clazz.getDeclaredConstructor(short.class));

        GraphBuilderConfiguration suitConfig = GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withEagerResolving(true).withAlwaysSafeConstructors();
        PhaseSuite<HighTierContext> suit = getCustomGraphBuilderSuite(suitConfig);
        for (Constructor<TestClass> constructor : constructors) {
            ResolvedJavaMethod method = asResolvedJavaMethod(constructor);
            StructuredGraph.Builder builder = builder(method, StructuredGraph.AllowAssumptions.YES);
            StructuredGraph graph = parse(builder, suit);
            Assert.assertEquals(1, graph.getNodes().filter(FinalFieldBarrierNode.class).count());
        }
    }

    @Test
    public void runCheckStableWriteConstructors() throws IOException, InterruptedException {
        launchSubprocess(this::checkStableWriteConstructors, "--add-opens=java.base/java.lang=ALL-UNNAMED");
    }

    private void checkStableWriteConstructors() {
        try {
            Class<?> clazz = lookupClass(MethodHandles.privateLookupIn(String.class, MethodHandles.lookup()), "java.lang.TestClass");
            List<Constructor<?>> constructors = List.of(clazz.getConstructor(), clazz.getConstructor(int.class), clazz.getConstructor(short.class));
            List<Constructor<?>> constructorsWithStableWrite = List.of(clazz.getConstructor(int.class), clazz.getConstructor(short.class));

            for (Constructor<?> constructor : constructors) {
                boolean hasStableWrite = constructorsWithStableWrite.contains(constructor);
                StructuredGraph graph = parseForCompile(asResolvedJavaMethod(constructor));
                Assert.assertEquals(hasStableWrite ? 1 : 0, graph.getNodes().filter(FinalFieldBarrierNode.class).count());
            }
        } catch (IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] generateClass(String className) {
        ClassDesc thisClass = ClassDesc.of(className);
        // @formatter:off
        return ClassFile.of().build(thisClass, classBuilder -> classBuilder
                        .withField("a", CD_int, ACC_PRIVATE)
                        .withField("b", CD_int, fieldBuilder -> fieldBuilder
                                        .withFlags(ACC_PRIVATE)
                                        .with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("jdk.internal.vm.annotation.Stable")))))
                        .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, b -> b
                                        .aload(0)
                                        .iconst_4()
                                        .putfield(thisClass, "a", CD_int)
                                        .return_())
                        .withMethodBody(INIT_NAME, MethodTypeDesc.of(CD_void, CD_int), ACC_PUBLIC, b -> b
                                        .aload(0)
                                        .iload(1)
                                        .putfield(thisClass, "b", CD_int)
                                        .return_())
                        .withMethodBody(INIT_NAME, MethodTypeDesc.of(CD_void, CD_short), ACC_PUBLIC, b -> b
                                        .aload(0)
                                        .dup()
                                        .iconst_0()
                                        .putfield(thisClass, "b", CD_int)
                                        .iload(1)
                                        .putfield(thisClass, "a", CD_int)
                                        .return_()));
        // @formatter:on
    }
}
