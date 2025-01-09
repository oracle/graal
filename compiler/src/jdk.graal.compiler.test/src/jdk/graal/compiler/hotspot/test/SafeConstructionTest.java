/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import com.oracle.truffle.api.impl.asm.Opcodes;

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
public class SafeConstructionTest extends SubprocessTest {

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
            Class<?> clazz = generatePrivilegedTestClass();
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

    /**
     * This generates TestClass but in privileged location, allowing @Stable to be effective.
     */
    private static Class<?> generatePrivilegedTestClass() throws IllegalAccessException {
        String clazzInternalName = "java/lang/TestClass";
        String init = "<init>";
        String aName = "a";
        String bName = "b";
        String intTypeDesc = int.class.descriptorString();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, clazzInternalName, null, "java/lang/Object", null);
        MethodVisitor mv;
        cw.visitField(Opcodes.ACC_PRIVATE, aName, intTypeDesc, null, null);
        FieldVisitor fv = cw.visitField(Opcodes.ACC_PRIVATE, bName, intTypeDesc, null, null);
        fv.visitAnnotation(Stable.class.descriptorString(), true);

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, init, MethodType.methodType(void.class).descriptorString(), null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ICONST_4);
        mv.visitFieldInsn(Opcodes.PUTFIELD, clazzInternalName, aName, intTypeDesc);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, init, MethodType.methodType(void.class, int.class).descriptorString(), null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.ALOAD, 0);
        mv.visitIntInsn(Opcodes.ILOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, clazzInternalName, bName, intTypeDesc);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, init, MethodType.methodType(void.class, short.class).descriptorString(), null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.DUP);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitFieldInsn(Opcodes.PUTFIELD, clazzInternalName, bName, intTypeDesc);
        mv.visitIntInsn(Opcodes.ILOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, clazzInternalName, aName, intTypeDesc);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();

        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
        return lookup.defineClass(cw.toByteArray());
    }
}
