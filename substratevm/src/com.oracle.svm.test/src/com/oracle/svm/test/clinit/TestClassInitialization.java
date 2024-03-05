/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.clinit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.hosted.fieldfolding.IsStaticFinalFieldInitializedNode;

import jdk.internal.misc.Unsafe;

/*
 * The suffix of class names indicates the time when the class initializer is determined to be safe
 * for simulation at image build time.
 *
 * - MustBeSimulated: The simulation of class initializer succeeded, i.e., the class starts out as initialized at run time.
 *
 * - MustBeDelayed: The class initializer has side effects, it must be executed at run time.
 *
 * The suffixes are checked in the feature at build time (code in this file), at run time in the
 * main method (code in this file), and by an external script that parses the class initialization
 * log output (code in mx_substratevm.py).
 */

class PureMustBeSimulated {
    static int v;
    static {
        v = 1;
        v = 42;
    }
}

class InitializesPureMustBeDelayed {
    static int v;
    static {
        v = PureMustBeSimulated.v;
    }
}

/**
 * The class initializer of this type is actually never executed because static final int fields
 * assigned directly at the field definition site are already constant folded by javac at all usage
 * sites.
 */
class NonPureAccessedFinal {
    static final int v = 1;
    static {
        if (alwaysTrue()) {
            throw new RuntimeException("Must not be called at runtime or compile time.");
        }
    }

    static boolean alwaysTrue() {
        return true;
    }
}

class PureCallMustBeSimulated {
    static int v;
    static {
        v = TestClassInitialization.pure();
    }
}

class NonPureMustBeDelayed {
    static int v = 1;
    static {
        System.out.println("Delaying NonPureMustBeDelayed");
    }
}

class InitializesNonPureMustBeDelayed {
    static int v = NonPureMustBeDelayed.v;
}

class SystemPropReadMustBeDelayed {
    static int v = 1;
    static {
        System.getProperty("test");
    }
}

class SystemPropWriteMustBeDelayed {
    static int v = 1;
    static {
        System.setProperty("test", "");
    }
}

class StartsAThreadMustBeDelayed {
    static int v = 1;
    static {
        new Thread().start();
    }
}

class CreatesAnExceptionMustBeDelayed {
    static Exception e;
    static {
        e = new Exception("should fire at runtime");
    }
}

class ThrowsAnExceptionUninitializedMustBeDelayed {
    static int v = 1;
    static {
        if (PureMustBeSimulated.v == 42) {
            throw new RuntimeException("should fire at runtime");
        }
    }
}

interface PureInterfaceMustBeSimulated {
}

class PureSubclassMustBeDelayed extends SuperClassMustBeDelayed {
    static int v = 1;
}

class SuperClassMustBeDelayed implements PureInterfaceMustBeSimulated {
    static {
        System.out.println("Delaying SuperClassMustBeDelayed");
    }
}

interface InterfaceNonPureMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying InterfaceNonPureMustBeDelayed");
        }
    }
}

interface InterfaceNonPureDefaultMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying InterfaceNonPureDefaultMustBeDelayed");
        }
    }

    default int m() {
        return v;
    }
}

class PureSubclassInheritsDelayedInterfaceMustBeSimulated implements InterfaceNonPureMustBeDelayed {
    static int v = 1;
}

class PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed implements InterfaceNonPureDefaultMustBeDelayed {
    static int v = 1;
}

class ImplicitExceptionInInitializerUninitializedMustBeDelayed {

    static int a = 10;
    static int b = 0;
    static int res;

    static {
        res = a / b;
    }
}

class PureDependsOnImplicitExceptionUninitializedMustBeDelayed {

    static int a;

    static {
        a = ImplicitExceptionInInitializerUninitializedMustBeDelayed.res;
    }
}

class StaticFieldHolderMustBeSimulated {
    /**
     * Other class initializers that modify {@link #a} must not run at image build time so that the
     * initial value 111 assigned here can be read at run time.
     */
    static int a = 111;

    static void setA(int value) {
        a = value;
    }
}

class StaticFieldModifer1MustBeDelayed {
    static {
        StaticFieldHolderMustBeSimulated.a = 222;
    }

    static void triggerInitialization() {
    }
}

class StaticFieldModifer2MustBeDelayed {
    static {
        StaticFieldHolderMustBeSimulated.setA(333);
    }

    static void triggerInitialization() {
    }
}

class RecursionInInitializerMustBeSimulated {
    static int i = compute(200);

    static int compute(int n) {
        if (n <= 1) {
            return 1;
        } else {
            return n + compute(n - 1);
        }
    }
}

class UnsafeAccessMustBeSimulated {
    static UnsafeAccessMustBeSimulated value = compute();

    int f01;
    int f02;
    int f03;
    int f04;
    int f05;
    int f06;
    int f07;
    int f08;
    int f09;
    int f10;
    int f11;
    int f12;
    int f13;
    int f14;
    int f15;
    int f16;

    static UnsafeAccessMustBeSimulated compute() {
        UnsafeAccessMustBeSimulated result = new UnsafeAccessMustBeSimulated();
        /*
         * We are writing a random instance field, depending on the header size. But the object is
         * big enough so that the write is one of the fields. The unsafe write is converted to a
         * proper store field node because the offset is constant, so in the static analysis graph
         * there is no unsafe access node.
         */
        Unsafe.getUnsafe().putInt(result, 32L, 1234);
        return result;
    }
}

enum EnumMustBeSimulated {
    V1(null),
    V2("Hello"),
    V3(new Object());

    final Object value;

    EnumMustBeSimulated(Object value) {
        this.value = value;
    }

    Object getValue() {
        /*
         * Use an assertion, so that the static final field that stores the assertion status is
         * filled in the class initializer. We want to test that using assertions does not impact
         * the class initialization analysis.
         */
        assert value != null;
        return value;
    }
}

class NativeMethodMustBeDelayed {
    static int i = compute();

    static int compute() {
        try {
            nativeMethod();
        } catch (LinkageError ignored) {
            /* Expected since the native method is not implemented. */
        }
        return 42;
    }

    static native void nativeMethod();

    static void foo() {
        /*
         * Even when a class is initialized at run time, the check whether assertions are included
         * must be constant folded at image build time. Otherwise we have a performance problem.
         */
        assert assertionOnlyCode();
    }

    static boolean assertionOnlyCode() {
        AssertionOnlyClassMustBeUnreachable.reference();
        return false;
    }
}

class AssertionOnlyClassMustBeUnreachable {
    static void reference() {
    }
}

/**
 * Cycle between this class and a helper class.
 */
class CycleMustBeSimulated {
    static {
        HelperClassMustBeSimulated.foo();
    }

    static void foo() {
    }
}

class HelperClassMustBeSimulated {
    static {
        CycleMustBeSimulated.foo();
    }

    static void foo() {
    }
}

/** Various reflection lookup methods are safe for execution at image build time. */
class ReflectionMustBeSimulated {
    static Class<?> c1;
    static Class<?> c2;
    static Method m1;
    static Field f2;

    static {
        try {
            Class<?> c1Local = Class.forName("com.oracle.svm.test.clinit.ForNameMustBeSimulated", true, ReflectionMustBeSimulated.class.getClassLoader());
            c1 = c1Local;

            /**
             * Looking up a class that cannot be initialized at build time is allowed, as long as
             * `initialize` is `false`.
             */
            Class<?> c2Local = Class.forName("com.oracle.svm.test.clinit.ForNameUninitializedMustBeDelayed", false, ReflectionMustBeSimulated.class.getClassLoader());
            c2 = c2Local;

            /*
             * GR-51519: Calling getDeclaredMethod on the field c1 instead of the variable c1Local
             * would not work, the ReflectionPlugins do not see through simulated image heap
             * constants for the parameterTypes array yet.
             */
            m1 = c1Local.getDeclaredMethod("foo", int.class);
            f2 = c2Local.getDeclaredField("field");

            /*
             * Check that reflective class lookup and the elimination of the class initialization
             * check also works when the class name is not constant yet during bytecode parsing.
             */
            if (c1Local != Class.forName(forNameMustBeSimulated(), true, ReflectionMustBeSimulated.class.getClassLoader())) {
                throw new Error("wrong class");
            }

        } catch (ReflectiveOperationException ex) {
            throw new Error(ex);
        }
    }

    private static String forNameMustBeSimulated() {
        return "com.oracle.svm.test.clinit.ForNameMustBeSimulated";
    }
}

@SuppressWarnings("unused")
class ForNameMustBeSimulated {
    static void foo(int arg) {
    }
}

class ForNameUninitializedMustBeDelayed {
    static {
        System.out.println("Delaying ForNameUninitializedMustBeDelayed");
    }

    int field;
}

class DevirtualizedCallMustBeDelayed {
    static {
        System.out.println("Delaying DevirtualizedCallMustBeDelayed");
    }

    static final Object value = 42;
}

class DevirtualizedCallSuperMustBeSimulated {
    Object foo() {
        return -1;
    }
}

class DevirtualizedCallSubMustBeSimulated extends DevirtualizedCallSuperMustBeSimulated {
    @Override
    Object foo() {
        return DevirtualizedCallMustBeDelayed.value;
    }
}

class DevirtualizedCallUsageMustBeDelayed {
    static final Object value = computeValue();

    private static Object computeValue() {
        DevirtualizedCallSuperMustBeSimulated provider = createProvider();

        /*
         * The static analysis can prove that DevirtualizedCallSubMustBeDelayed.foo is the only
         * callee and de-virtualize this call. So the original target method of the call site and
         * the actually invoked method are different - and the analysis that automatically
         * initializes classes must properly pick up this dependency.
         */
        return provider.foo();
    }

    private static DevirtualizedCallSuperMustBeSimulated createProvider() {
        return new DevirtualizedCallSubMustBeSimulated();
    }
}

class LargeAllocation1MustBeDelayed {
    static final Object value = computeValue();

    private static Object computeValue() {
        return new Object[200_000];
    }
}

class LargeAllocation2MustBeDelayed {
    static final Object value = computeValue();

    private static Object computeValue() {
        return new int[1][200_000];
    }
}

enum ComplexEnumMustBeSimulated {
    V1 {
        @Override
        int virtualMethod() {
            return 41;
        }
    },
    V2 {
        @Override
        int virtualMethod() {
            return 42;
        }
    };

    abstract int virtualMethod();

    static final Map<String, ComplexEnumMustBeSimulated> lookup;

    static {
        lookup = new HashMap<>();
        for (var v : values()) {
            lookup.put(v.name(), v);
        }
    }
}

class StaticFinalFieldFoldingMustBeSimulated {

    Object f1;
    Object f2;
    Object f3;

    StaticFinalFieldFoldingMustBeSimulated() {
        this.f1 = F1;
        this.f2 = F2;
        this.f3 = F3;
    }

    static final StaticFinalFieldFoldingMustBeSimulated before = new StaticFinalFieldFoldingMustBeSimulated();

    /**
     * Field value is stored in the class file attribute, so it is available even before this
     * assignment.
     */
    static final String F1 = "abc";
    /**
     * Field is optimized by our {@link IsStaticFinalFieldInitializedNode static final field folding
     * feature}.
     */
    static final Object F2 = "abc";
    /** Just a regular field. */
    static final Object F3 = new String[]{"abc"};

    static final StaticFinalFieldFoldingMustBeSimulated after = new StaticFinalFieldFoldingMustBeSimulated();
}

class LambdaMustBeSimulated {
    private static final Predicate<String> IS_AUTOMATIC = s -> s.equals("Hello");

    static boolean matches(List<String> l) {
        return l.stream().anyMatch(IS_AUTOMATIC);
    }
}

@SuppressWarnings("deprecation")
class BoxingMustBeSimulated {
    static Integer i1 = 41;
    static Integer i2 = new Integer(42);

    static int sum = i1 + i2;

    static final Map<Class<?>, Object> defaultValues = new HashMap<>();

    static {
        defaultValues.put(boolean.class, Boolean.FALSE);
        defaultValues.put(byte.class, (byte) 0);
        defaultValues.put(short.class, (short) 0);
        defaultValues.put(int.class, 0);
        defaultValues.put(long.class, 0L);
        defaultValues.put(char.class, '\0');
        defaultValues.put(float.class, 0.0F);
        defaultValues.put(double.class, 0.0);
    }

    public static Object defaultValue(Class<?> clazz) {
        return defaultValues.get(clazz);
    }

    static Object S1;
    static Object O1;
    static Object O2;

    static {
        short[] shorts = {42, 43, 44, 45, 46, 47, 48};
        S1 = new short[12];
        System.arraycopy(shorts, 1, S1, 2, 5);
        System.arraycopy(S1, 3, S1, 5, 5);

        Object[] objects = {"42", null, "44", "45", null, "47", "48"};
        O1 = Arrays.copyOf(objects, 3);
        O2 = Arrays.copyOfRange(objects, 3, 6, String[].class);
    }
}

class SingleByteFieldMustBeSimulated {
    static SingleByteFieldMustBeSimulated instance1 = new SingleByteFieldMustBeSimulated((byte) 42);
    static SingleByteFieldMustBeSimulated instance2 = new SingleByteFieldMustBeSimulated((byte) -42);

    byte b;

    SingleByteFieldMustBeSimulated(byte b) {
        this.b = b;
    }
}

class SynchronizedMustBeSimulated {

    static Vector<String> vector;

    static {
        /*
         * Using the normally disallowed "old" synchronized collection classes is the easiest way to
         * test what we want to test.
         */
        // Checkstyle: stop
        vector = new Vector<>();
        // Checkstyle: resume
        for (int i = 0; i < 42; i++) {
            vector.add(String.valueOf(i));
        }
    }
}

class SynchronizedMustBeDelayed {
    static {
        synchronizedMethod();
    }

    /**
     * This method synchronizes on an object that exists before class initialization is started: the
     * class object itself. So we cannot determine at image build time if the class initializer
     * would ever finish execution at image run time. Another thread could hold the lock
     * indefinitely.
     */
    static synchronized int synchronizedMethod() {
        return 42;
    }
}

class InitializationOrder {
    static final List<Class<?>> initializationOrder = Collections.synchronizedList(new ArrayList<>());
}

interface Test1_I1 {
    default void defaultI1() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I1.class);
        return 42;
    }
}

interface Test1_I2 extends Test1_I1 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I2.class);
        return 42;
    }
}

interface Test1_I3 extends Test1_I2 {
    default void defaultI3() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I3.class);
        return 42;
    }
}

interface Test1_I4 extends Test1_I3 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test1_I4.class);
        return 42;
    }
}

class Test1_A implements Test1_I4 {
    static {
        InitializationOrder.initializationOrder.add(Test1_A.class);
    }
}

interface Test2_I1 {
    default void defaultI1() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I1.class);
        return 42;
    }
}

interface Test2_I2 extends Test2_I1 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I2.class);
        return 42;
    }
}

interface Test2_I3 extends Test2_I2 {
    default void defaultI3() {
    }

    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I3.class);
        return 42;
    }
}

interface Test2_I4 extends Test2_I3 {
    int order = add();

    static int add() {
        InitializationOrder.initializationOrder.add(Test2_I4.class);
        return 42;
    }
}

class TestClassInitializationFeature implements Feature {

    private static void checkClasses() {
        System.out.println("=== Checking initialization state of classes");

        List<String> errors = new ArrayList<>();
        for (Class<?> checkedClass : TestClassInitialization.checkedClasses) {
            boolean nameHasSimulated = checkedClass.getName().contains("MustBeSimulated");
            boolean nameHasDelayed = checkedClass.getName().contains("MustBeDelayed");

            if ((nameHasSimulated ? 1 : 0) + (nameHasDelayed ? 1 : 0) != 1) {
                errors.add(checkedClass.getName() + ": Wrongly named class: nameHasSimulated=" + nameHasSimulated + ", nameHasDelayed=" + nameHasDelayed);
            } else if (!Unsafe.getUnsafe().shouldBeInitialized(checkedClass)) {
                errors.add(checkedClass.getName() + ": Class already initialized at image build time");
            }
        }

        if (!errors.isEmpty()) {
            throw new Error(errors.stream().collect(Collectors.joining(System.lineSeparator())));
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        /* We need to access the checkedClasses array both at image build time and run time. */
        RuntimeClassInitialization.initializeAtBuildTime(TestClassInitialization.class);

        /*
         * Initialization of a class first triggers initialization of all superinterfaces that
         * declared default methods.
         */
        InitializationOrder.initializationOrder.clear();
        assertNotInitialized(Test1_I1.class, Test1_I2.class, Test1_I3.class, Test1_I4.class, Test1_A.class);
        RuntimeClassInitialization.initializeAtBuildTime(Test1_A.class);
        assertNotInitialized(Test1_I2.class, Test1_I4.class);
        assertInitialized(Test1_I1.class, Test1_I3.class, Test1_A.class);
        assertArraysEqual(new Object[]{Test1_I1.class, Test1_I3.class, Test1_A.class}, InitializationOrder.initializationOrder.toArray());

        /*
         * Initialization of an interface does not trigger initialization of superinterfaces.
         * Regardless whether any of the involved interfaces declare default methods.
         */
        InitializationOrder.initializationOrder.clear();
        assertNotInitialized(Test2_I1.class, Test2_I2.class, Test2_I3.class, Test2_I4.class);
        RuntimeClassInitialization.initializeAtBuildTime(Test2_I4.class);
        assertNotInitialized(Test2_I1.class, Test2_I2.class, Test2_I3.class);
        assertInitialized(Test2_I4.class);
        assertArraysEqual(new Object[]{Test2_I4.class}, InitializationOrder.initializationOrder.toArray());
        RuntimeClassInitialization.initializeAtBuildTime(Test2_I3.class);
        assertNotInitialized(Test2_I1.class, Test2_I2.class);
        assertInitialized(Test2_I3.class, Test2_I4.class);
        assertArraysEqual(new Object[]{Test2_I4.class, Test2_I3.class}, InitializationOrder.initializationOrder.toArray());
        RuntimeClassInitialization.initializeAtBuildTime(Test2_I2.class);
        assertNotInitialized(Test2_I1.class);
        assertInitialized(Test2_I2.class, Test2_I3.class, Test2_I4.class);
        assertArraysEqual(new Object[]{Test2_I4.class, Test2_I3.class, Test2_I2.class}, InitializationOrder.initializationOrder.toArray());
    }

    private static void assertNotInitialized(Class<?>... classes) {
        for (var clazz : classes) {
            if (!Unsafe.getUnsafe().shouldBeInitialized(clazz)) {
                throw new AssertionError("Already initialized: " + clazz);
            }
        }
    }

    private static void assertInitialized(Class<?>... classes) {
        for (var clazz : classes) {
            if (Unsafe.getUnsafe().shouldBeInitialized(clazz)) {
                throw new AssertionError("Not initialized: " + clazz);
            }
        }
    }

    private static void assertArraysEqual(Object[] expected, Object[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new RuntimeException("expected " + Arrays.toString(expected) + " but found " + Arrays.toString(actual));
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        checkClasses();
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        checkClasses();
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (access.isReachable(AssertionOnlyClassMustBeUnreachable.class)) {
            throw new Error("Assertion check was not constant folded for a class that is initialized at run time. " +
                            "We assume here that the image is built with assertions disabled, which is the case for the gate check.");
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        checkClasses();
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        checkClasses();
    }
}

public class TestClassInitialization {

    static final Class<?>[] checkedClasses = new Class<?>[]{
                    PureMustBeSimulated.class,
                    NonPureMustBeDelayed.class,
                    PureCallMustBeSimulated.class,
                    InitializesNonPureMustBeDelayed.class,
                    SystemPropReadMustBeDelayed.class,
                    SystemPropWriteMustBeDelayed.class,
                    StartsAThreadMustBeDelayed.class,
                    CreatesAnExceptionMustBeDelayed.class,
                    ThrowsAnExceptionUninitializedMustBeDelayed.class,
                    PureInterfaceMustBeSimulated.class,
                    PureSubclassMustBeDelayed.class,
                    SuperClassMustBeDelayed.class,
                    InterfaceNonPureMustBeDelayed.class,
                    InterfaceNonPureDefaultMustBeDelayed.class,
                    PureSubclassInheritsDelayedInterfaceMustBeSimulated.class,
                    PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed.class,
                    ImplicitExceptionInInitializerUninitializedMustBeDelayed.class,
                    PureDependsOnImplicitExceptionUninitializedMustBeDelayed.class,
                    StaticFieldHolderMustBeSimulated.class,
                    StaticFieldModifer1MustBeDelayed.class,
                    StaticFieldModifer2MustBeDelayed.class,
                    RecursionInInitializerMustBeSimulated.class,
                    UnsafeAccessMustBeSimulated.class,
                    EnumMustBeSimulated.class,
                    NativeMethodMustBeDelayed.class,
                    CycleMustBeSimulated.class, HelperClassMustBeSimulated.class,
                    ReflectionMustBeSimulated.class, ForNameMustBeSimulated.class, ForNameUninitializedMustBeDelayed.class,
                    DevirtualizedCallMustBeDelayed.class, DevirtualizedCallSuperMustBeSimulated.class, DevirtualizedCallSubMustBeSimulated.class, DevirtualizedCallUsageMustBeDelayed.class,
                    LargeAllocation1MustBeDelayed.class, LargeAllocation2MustBeDelayed.class,
                    ComplexEnumMustBeSimulated.class,
                    StaticFinalFieldFoldingMustBeSimulated.class,
                    LambdaMustBeSimulated.class,
                    BoxingMustBeSimulated.class,
                    SingleByteFieldMustBeSimulated.class,
                    SynchronizedMustBeSimulated.class, SynchronizedMustBeDelayed.class,
    };

    static int pure() {
        return transitivelyPure() + 42;
    }

    private static int transitivelyPure() {
        return 42;
    }

    /*
     * Since {@link Function} is a core JDK type that is always marked as
     * "initialize at build time", it is allowed to have a lambda for it in the image heap.
     */
    static Function<String, String> buildTimeLambda = TestClassInitialization::duplicate;

    static String duplicate(String s) {
        return s + s;
    }

    public static void main(String[] args) {
        for (var checkedClass : checkedClasses) {
            boolean nameHasSimulated = checkedClass.getName().contains("MustBeSimulated");
            boolean nameHasDelayed = checkedClass.getName().contains("MustBeDelayed");
            boolean initialized = !Unsafe.getUnsafe().shouldBeInitialized(checkedClass);
            if (nameHasDelayed == initialized) {
                throw new RuntimeException("Class " + checkedClass.getName() + ": nameHasSimulated=" + nameHasSimulated + ", nameHasDelayed=" + nameHasDelayed + ", initialized=" + initialized);
            }
        }

        assertTrue("123123".equals(buildTimeLambda.apply("123")));

        assertSame(42, PureMustBeSimulated.v);
        assertSame(84, PureCallMustBeSimulated.v);
        assertSame(42, InitializesPureMustBeDelayed.v);
        assertSame(1, NonPureMustBeDelayed.v);
        assertSame(1, NonPureAccessedFinal.v);
        assertSame(1, InitializesNonPureMustBeDelayed.v);
        assertSame(1, SystemPropReadMustBeDelayed.v);
        assertSame(1, SystemPropWriteMustBeDelayed.v);
        assertSame(1, StartsAThreadMustBeDelayed.v);
        assertSame(1, PureSubclassMustBeDelayed.v);
        assertSame(1, PureSubclassInheritsDelayedInterfaceMustBeSimulated.v);
        assertSame(1, PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed.v);
        assertSame(1, InterfaceNonPureMustBeDelayed.v);
        try {
            sink(ThrowsAnExceptionUninitializedMustBeDelayed.v);
            throw new RuntimeException("should not reach here");
        } catch (ExceptionInInitializerError e) {
            assertSame("should fire at runtime", e.getCause().getMessage());
        }
        assertSame("should fire at runtime", CreatesAnExceptionMustBeDelayed.e.getMessage());
        try {
            sink(ImplicitExceptionInInitializerUninitializedMustBeDelayed.res);
            throw new RuntimeException("should not reach here");
        } catch (ExceptionInInitializerError e) {
            assertSame(ArithmeticException.class, e.getCause().getClass());
        }
        try {
            sink(PureDependsOnImplicitExceptionUninitializedMustBeDelayed.a);
            throw new RuntimeException("should not reach here");
        } catch (NoClassDefFoundError e) {
            /* Expected. */
        }

        assertSame(111, StaticFieldHolderMustBeSimulated.a);
        StaticFieldModifer1MustBeDelayed.triggerInitialization();
        assertSame(222, StaticFieldHolderMustBeSimulated.a);
        StaticFieldModifer2MustBeDelayed.triggerInitialization();
        assertSame(333, StaticFieldHolderMustBeSimulated.a);

        assertSame(20100, RecursionInInitializerMustBeSimulated.i);

        UnsafeAccessMustBeSimulated value = UnsafeAccessMustBeSimulated.value;
        assertSame(1234, value.f01 + value.f02 + value.f03 + value.f04 + value.f05 + value.f06 + value.f07 + value.f08 +
                        value.f09 + value.f10 + value.f11 + value.f12 + value.f13 + value.f14 + value.f15 + value.f16);

        EnumMustBeSimulated[] values = EnumMustBeSimulated.values();
        assertSame(null, values[0].getValue());
        assertSame("Hello", values[1].getValue());
        assertSame(Object.class, values[2].getValue().getClass());
        assertSame(EnumMustBeSimulated.V1, stringToEnum("v1"));

        assertSame(42, NativeMethodMustBeDelayed.i);
        NativeMethodMustBeDelayed.foo();
        CycleMustBeSimulated.foo();

        assertSame(ForNameMustBeSimulated.class, ReflectionMustBeSimulated.c1);
        assertSame(ForNameUninitializedMustBeDelayed.class, ReflectionMustBeSimulated.c2);
        assertSame("foo", ReflectionMustBeSimulated.m1.getName());
        assertSame("field", ReflectionMustBeSimulated.f2.getName());

        assertSame(42, DevirtualizedCallUsageMustBeDelayed.value);

        assertSame(200_000, ((Object[]) LargeAllocation1MustBeDelayed.value).length);
        assertSame(1, ((int[][]) LargeAllocation2MustBeDelayed.value).length);
        assertSame(200_000, ((int[][]) LargeAllocation2MustBeDelayed.value)[0].length);

        assertSame(ComplexEnumMustBeSimulated.V1, ComplexEnumMustBeSimulated.lookup.get("V1"));
        assertSame(42, ComplexEnumMustBeSimulated.lookup.get("V2").virtualMethod());

        assertSame("abc", StaticFinalFieldFoldingMustBeSimulated.before.f1);
        assertSame(null, StaticFinalFieldFoldingMustBeSimulated.before.f2);
        assertSame(null, StaticFinalFieldFoldingMustBeSimulated.before.f3);
        assertSame("abc", StaticFinalFieldFoldingMustBeSimulated.after.f1);
        assertSame("abc", StaticFinalFieldFoldingMustBeSimulated.after.f2);
        assertSame(1, ((Object[]) StaticFinalFieldFoldingMustBeSimulated.after.f3).length);

        assertSame(true, LambdaMustBeSimulated.matches(List.of("1", "2", "3", "Hello", "4")));
        assertSame(false, LambdaMustBeSimulated.matches(List.of("1", "2", "3", "4")));

        assertSame(83, BoxingMustBeSimulated.sum);
        assertSame(Character.class, BoxingMustBeSimulated.defaultValue(char.class).getClass());
        assertSame(Short.class, BoxingMustBeSimulated.defaultValue(short.class).getClass());
        assertSame(Float.class, BoxingMustBeSimulated.defaultValue(float.class).getClass());
        assertTrue(Arrays.equals((short[]) BoxingMustBeSimulated.S1, new short[]{0, 0, 43, 44, 45, 44, 45, 46, 47, 0, 0, 0}));
        assertTrue(Arrays.equals((Object[]) BoxingMustBeSimulated.O1, new Object[]{"42", null, "44"}));
        assertTrue(Arrays.equals((Object[]) BoxingMustBeSimulated.O2, new String[]{"45", null, "47"}));

        /*
         * The unsafe field offset lookup is constant folded at image build time, which also
         * registers the field as unsafe accessed.
         */
        long bOffset = Unsafe.getUnsafe().objectFieldOffset(SingleByteFieldMustBeSimulated.class, "b");
        assertTrue(bOffset % 4 == 0);
        /*
         * Check that for sub-int values, the padding after the value is not touched by the image
         * heap writer.
         */
        assertSame(42, readRawByte(SingleByteFieldMustBeSimulated.instance1, bOffset + 0));
        assertSame(0, readRawByte(SingleByteFieldMustBeSimulated.instance1, bOffset + 1));
        assertSame(0, readRawByte(SingleByteFieldMustBeSimulated.instance1, bOffset + 2));
        assertSame(0, readRawByte(SingleByteFieldMustBeSimulated.instance1, bOffset + 3));
        assertSame(-42, readRawByte(SingleByteFieldMustBeSimulated.instance2, bOffset + 0));
        assertSame(0, readRawByte(SingleByteFieldMustBeSimulated.instance2, bOffset + 1));
        assertSame(0, readRawByte(SingleByteFieldMustBeSimulated.instance2, bOffset + 2));
        assertSame(0, readRawByte(SingleByteFieldMustBeSimulated.instance2, bOffset + 3));

        assertSame(42, SynchronizedMustBeSimulated.vector.size());
        assertSame(42, SynchronizedMustBeDelayed.synchronizedMethod());

        for (var checkedClass : checkedClasses) {
            boolean initialized = !Unsafe.getUnsafe().shouldBeInitialized(checkedClass);
            boolean expectedUninitialized = checkedClass.getName().contains("Uninitialized");
            if (initialized == expectedUninitialized) {
                throw new RuntimeException("Class " + checkedClass.getName() + ": initialized=" + initialized + ", expectedUninitialized=" + expectedUninitialized);
            }
        }
    }

    @NeverInline("prevent constant folding, we read the raw memory after the last field")
    static int readRawByte(Object o, long offset) {
        return Unsafe.getUnsafe().getByte(o, offset);
    }

    private static EnumMustBeSimulated stringToEnum(String name) {
        if (EnumMustBeSimulated.V1.name().equalsIgnoreCase(name)) {
            return EnumMustBeSimulated.V1;
        } else {
            return EnumMustBeSimulated.V2;
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new RuntimeException("condition not true");
        }
    }

    private static void assertSame(long expected, long actual) {
        if (expected != actual) {
            throw new RuntimeException("expected " + expected + " but found " + actual);
        }
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new RuntimeException("expected " + expected + " but found " + actual);
        }
    }

    private static void sink(@SuppressWarnings("unused") Object o) {
    }
}
