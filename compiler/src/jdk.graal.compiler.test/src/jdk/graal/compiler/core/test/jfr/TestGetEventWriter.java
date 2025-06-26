/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.jfr;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

import java.io.IOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.InvocationTargetException;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.test.CustomizedBytecodePattern;
import jdk.graal.compiler.core.test.SubprocessTest;
import jdk.graal.compiler.test.AddExports;
import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Derived from {@code test/jdk/jdk/jfr/jvm/TestGetEventWriter.java} to test that JVMCI only allows
 * a compiler to link a call to
 * {@code jdk.jfr.internal.event.EventWriterFactory.getEventWriter(long)} from a JFR instrumented
 * {@code jdk.jfr.Event.commit()} method.
 *
 * See the documentation attached to {@code jdk.jfr.internal.event.EventWriter} for more detail.
 *
 * This test must run in a separate JVM process. It enables JFR instrumentation, which affects
 * Truffle PartialEvaluationTests.
 */
@AddExports("jdk.jfr/jdk.jfr.internal.event")
public class TestGetEventWriter extends SubprocessTest {

    private static boolean isJFRAvailable() {
        return ModuleLayer.boot().findModule("jdk.jfr").isPresent();
    }

    private static void initializeJFR() {
        try (Recording r = new Recording()) {
            r.start();
            // Unlocks access to jdk.jfr.internal.event
            InitializationEvent e = new InitializationEvent();
            e.commit();
        }
        // Make sure EventWriterFactory can be accessed.
        try {
            Class.forName("jdk.jfr.internal.event.EventWriter");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Not able to access jdk.jfr.internal.event.EventWriter class", e);
        }
    }

    static class InitializationEvent extends Event {
    }

    @Test
    public void test() throws IOException, InterruptedException {
        String[] args;
        if (isJFRAvailable()) {
            args = new String[0];
        } else {
            args = new String[]{"--add-modules", "jdk.jfr"};
        }
        launchSubprocess(() -> {
            try {
                initializeJFR();
                testInitializationEvent();
                testNonEvent();
                testRegisteredTrueEvent();
                testRegisteredFalseEvent();
                testMyCommitRegisteredTrue();
                testMyCommitRegisteredFalse();
                testStaticCommit();
            } catch (Throwable t) {
                throw rethrowSilently(RuntimeException.class, t);
            }
        }, args);
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static <E extends Throwable> E rethrowSilently(Class<E> type, Throwable ex) throws E {
        throw (E) ex;
    }

    public void testInitializationEvent() {
        InitializationEvent event = new InitializationEvent();
        ResolvedJavaMethod method = getResolvedJavaMethod(event.getClass(), "commit");

        // Ensure that compiling the blessed method succeeds
        getCode(method);
    }

    // The class does not inherit jdk.jfr.Event and, as such, does not implement the
    // API. It has its own stand-alone "commit()V", which is not an override, that
    // attempts to resolve and link against EventWriterFactory. This user implementation
    // is not blessed for linkage.
    public void testNonEvent() throws Throwable {
        testEvent("Non", "java.lang.Object", null, "commit", false);
    }

    // The user has defined a class which overrides and implements the "commit()V"
    // method declared final in jdk.jfr.Event.
    // This user implementation is not blessed for linkage.
    public void testRegisteredTrueEvent() throws Throwable {
        testEvent("Registered", "jdk.jfr.Event", true, "commit", false);
    }

    // The user has defined a class which overrides and implements the "commit()V"
    // method declared final in jdk.jfr.Event. This user implementation is not
    // blessed for linkage. If a class have user-defined implementations
    // of any methods declared final, it is not instrumented.
    // Although it is a subclass of jdk.jfr.Event, on initial load, we will
    // classify it as being outside of the JFR system. Attempting to register
    // such a class throws an IllegalArgumentException. The user-defined
    // "commit()V" method is still not blessed for linkage, even after registration.
    public void testRegisteredFalseEvent() throws Throwable {
        testEvent("Registered", "jdk.jfr.Event", false, "commit", false);
    }

    // The user has implemented another method, "myCommit()V", not an override nor
    // overload. that attempts to resolve and link EventWriterFactory. This will fail,
    // because "myCommit()V" is not blessed for linkage.
    public void testMyCommitRegisteredTrue() throws Throwable {
        testEvent("MyCommit", "jdk.jfr.Event", true, "myCommit", false);
    }

    // The user has implemented another method, "myCommit()V", not an override,
    // nor overload. This linkage will fail because "myCommit()V" is not blessed.
    // Since the user has not defined any final methods in jdk.jfr.Event,
    // the class is not excluded wholesale from the JFR system.
    // Invoking the real "commit()V", installed by the framework, is OK.
    public void testMyCommitRegisteredFalse() throws Throwable {
        testEvent("MyCommit", "jdk.jfr.Event", false, "myCommit", false);
    }

    // Events located in the boot class loader can create a static
    // commit-method to emit events. It must not be used by code
    // outside of the boot class loader.
    public void testStaticCommit() throws Throwable {
        testEvent("StaticCommit", "jdk.jfr.Event", null, "commit", true);
    }

    private void testEvent(String name, String superClass, Boolean isRegistered, String commitName, boolean isStatic) throws Throwable {
        String className = getClass().getPackageName() + "." + name + "Event";
        Runnable event = newEventObject(superClass, isRegistered, commitName, isStatic, className);
        try {
            event.run();
            throw new AssertionError("should not reach here: " + className);
        } catch (IllegalAccessError e) {
            // OK
        }

        ResolvedJavaMethod method = getResolvedJavaMethod(event.getClass(), commitName);
        try {
            // Ensure that compiling the non-blessed method throws the expected bailout
            getCode(method);
            throw new AssertionError("should not reach here: " + method.format("%H.%n(%p)"));
        } catch (PermanentBailoutException e) {
            Assert.assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof IllegalAccessError);
        }
    }

    // @formatter:off
    /*
     * Generates a class following this template:
     *
     * [@Registered(<isRegistered>)]  // iff isRegistered != null
     * public class <name>Event extends <superClass> implements Runnable {
     *     public [static] void <commitName>() {
     *         EventWriterFactory.getEventWriter(1L);
     *     }
     *
     *     @Override
     *     public void run() {
     *         <commitName>();
     *     }
     * }
     */
    // @formatter:on
    Runnable newEventObject(String superClassName, Boolean isRegistered, String commitName, boolean isStatic, String className)
                    throws ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        return (Runnable) new CustomizedClass(superClassName, isRegistered, commitName, isStatic).getClass(className).getConstructor().newInstance();
    }

    static class CustomizedClass implements CustomizedBytecodePattern {

        private String superClassName;
        private Boolean isRegistered;
        private String commitName;
        private boolean isStatic;

        CustomizedClass(String superClassName, Boolean isRegistered, String commitName, boolean isStatic) {
            this.superClassName = superClassName;
            this.isRegistered = isRegistered;
            this.commitName = commitName;
            this.isStatic = isStatic;
        }

        @Override
        public byte[] generateClass(String className) {
            ClassDesc thisClass = ClassDesc.of(className);
            ClassDesc superClass = ClassDesc.of(superClassName);

            // @formatter:off
            return ClassFile.of().build(thisClass, classBuilder -> {
                if (isRegistered != null) {
                    classBuilder.with(RuntimeVisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of("jdk.jfr.Registered"),
                                    AnnotationElement.ofBoolean("value", isRegistered))));
                }

                classBuilder
                                .withSuperclass(superClass)
                                .withInterfaceSymbols(cd(Runnable.class))
                                .withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, b -> b
                                                .aload(0)
                                                .invokespecial(superClass, INIT_NAME, MTD_void)
                                                .return_())
                                .withMethodBody(commitName, MTD_void, isStatic ? ACC_PUBLIC_STATIC : ACC_PUBLIC, b -> b
                                                .invokestatic(ClassDesc.of("jdk.jfr.internal.event.EventWriter"), "getEventWriter",
                                                                MethodTypeDesc.of(ClassDesc.of("jdk.jfr.internal.event.EventWriter")))
                                                .pop()
                                                .return_())
                                .withMethodBody("run", MTD_void, ACC_PUBLIC, b -> {
                                    if (isStatic) {
                                        b.invokestatic(thisClass, commitName, MTD_void);
                                    } else {
                                        b.aload(0).invokevirtual(thisClass, commitName, MTD_void);
                                    }
                                    b.return_();
                                });
            });
            // @formatter:on
        }
    }
}
