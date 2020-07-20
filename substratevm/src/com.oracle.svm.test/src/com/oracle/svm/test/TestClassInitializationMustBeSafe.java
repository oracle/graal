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
package com.oracle.svm.test;

// Checkstyle: stop

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import sun.misc.Unsafe;

class PureMustBeSafeEarly {
    static int v;
    static {
        v = 1;
        v = 42;
    }
}

class InitializesPureMustBeDelayed {
    static int v;
    static {
        v = PureMustBeSafeEarly.v;
    }
}

/* this one should not even show up */
class NonPureAccessedFinal {
    static final int v = 1;
    static {
        System.out.println("Must not be called at runtime or compile time.");
        System.exit(1);
    }
}

class PureCallMustBeSafeEarly {
    static int v;
    static {
        v = TestClassInitializationMustBeSafe.pure();
    }
}

class NonPureMustBeDelayed {
    static int v = 1;
    static {
        System.out.println("Analysis should not reach here.");
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

class CreatesAFileMustBeDelayed {
    static int v = 1;
    static File f = new File("./");
}

class CreatesAnExceptionMustBeDelayed {
    static Exception e;
    static {
        e = new Exception("should fire at runtime");
    }
}

class ThrowsAnExceptionMustBeDelayed {
    static int v = 1;
    static {
        if (PureMustBeSafeEarly.v == 42) {
            throw new RuntimeException("should fire at runtime");
        }
    }
}

interface PureInterfaceMustBeSafeEarly {
}

class PureSubclassMustBeDelayed extends SuperClassMustBeDelayed {
    static int v = 1;
}

class SuperClassMustBeDelayed implements PureInterfaceMustBeSafeEarly {
    static {
        System.out.println("Delaying this class.");
    }
}

interface InterfaceNonPureMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying this class.");
        }
    }
}

interface InterfaceNonPureDefaultMustBeDelayed {
    int v = B.v;

    class B {
        static int v = 1;
        static {
            System.out.println("Delaying this class.");
        }
    }

    default int m() {
        return v;
    }
}

class PureSubclassInheritsDelayedInterfaceMustBeSafeEarly implements InterfaceNonPureMustBeDelayed {
    static int v = 1;
}

class PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed implements InterfaceNonPureDefaultMustBeDelayed {
    static int v = 1;
}

class ImplicitExceptionInInitializerMustBeDelayed {

    static int a = 10;
    static int b = 0;
    static int res;

    static {
        res = a / b;
    }
}

class PureDependsOnImplicitExceptionMustBeDelayed {

    static int a;

    static {
        a = ImplicitExceptionInInitializerMustBeDelayed.res;
    }
}

class StaticFieldHolderMustBeSafeEarly {
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
        StaticFieldHolderMustBeSafeEarly.a = 222;
    }

    static void triggerInitialization() {
    }
}

class StaticFieldModifer2MustBeDelayed {
    static {
        StaticFieldHolderMustBeSafeEarly.setA(333);
    }

    static void triggerInitialization() {
    }
}

class RecursionInInitializerMustBeSafeLate {
    static int i = compute(200);

    static int compute(int n) {
        if (n <= 1) {
            return 1;
        } else {
            return n + compute(n - 1);
        }
    }
}

class UnsafeAccessMustBeSafeLate {
    static UnsafeAccessMustBeSafeLate value = compute();

    int f01, f02, f03, f04, f05, f06, f07, f08, f09, f10, f11, f12, f13, f14, f15, f16;

    static UnsafeAccessMustBeSafeLate compute() {
        UnsafeAccessMustBeSafeLate result = new UnsafeAccessMustBeSafeLate();
        /*
         * We are writing a random instance field, depending on the header size. But the object is
         * big enough so that the write is one of the fields. The unsafe write is converted to a
         * proper store field node because the offset is constant, so in the static analysis graph
         * there is no unsafe access node.
         */
        UnsafeAccess.UNSAFE.putInt(result, 32L, 1234);
        return result;
    }
}

enum EnumMustBeSafeEarly {
    V1(null),
    V2("Hello"),
    V3(new Object());

    final Object value;

    EnumMustBeSafeEarly(Object value) {
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
        Object obj = new Object();
        return System.identityHashCode(obj);
    }

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

class UnsafeAccess {
    static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe", e);
        }
    }
}

class TestClassInitializationMustBeSafeFeature implements Feature {

    static final Class<?>[] checkedClasses = new Class<?>[]{
                    PureMustBeSafeEarly.class,
                    NonPureMustBeDelayed.class,
                    PureCallMustBeSafeEarly.class,
                    InitializesNonPureMustBeDelayed.class,
                    SystemPropReadMustBeDelayed.class,
                    SystemPropWriteMustBeDelayed.class,
                    StartsAThreadMustBeDelayed.class,
                    CreatesAFileMustBeDelayed.class,
                    CreatesAnExceptionMustBeDelayed.class,
                    ThrowsAnExceptionMustBeDelayed.class,
                    PureInterfaceMustBeSafeEarly.class,
                    PureSubclassMustBeDelayed.class,
                    SuperClassMustBeDelayed.class,
                    InterfaceNonPureMustBeDelayed.class,
                    InterfaceNonPureDefaultMustBeDelayed.class,
                    PureSubclassInheritsDelayedInterfaceMustBeSafeEarly.class,
                    PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed.class,
                    ImplicitExceptionInInitializerMustBeDelayed.class,
                    PureDependsOnImplicitExceptionMustBeDelayed.class,
                    StaticFieldHolderMustBeSafeEarly.class,
                    StaticFieldModifer1MustBeDelayed.class,
                    StaticFieldModifer2MustBeDelayed.class,
                    RecursionInInitializerMustBeSafeLate.class,
                    UnsafeAccessMustBeSafeLate.class,
                    EnumMustBeSafeEarly.class,
                    NativeMethodMustBeDelayed.class};

    private static void checkClasses(boolean checkSafeEarly, boolean checkSafeLate) {
        System.out.println("=== Checking initialization state of classes: checkSafeEarly=" + checkSafeEarly + ", checkSafeLate=" + checkSafeLate);

        List<String> errors = new ArrayList<>();
        for (Class<?> checkedClass : checkedClasses) {
            boolean nameHasSafeEarly = checkedClass.getName().contains("MustBeSafeEarly");
            boolean nameHasSafeLate = checkedClass.getName().contains("MustBeSafeLate");
            boolean nameHasDelayed = checkedClass.getName().contains("MustBeDelayed");

            if ((nameHasSafeEarly ? 1 : 0) + (nameHasSafeLate ? 1 : 0) + (nameHasDelayed ? 1 : 0) != 1) {
                errors.add(checkedClass.getName() + ": Wrongly named class (nameHasSafeEarly: " + nameHasSafeEarly + ", nameHasSafeLate: " + nameHasSafeLate + ", nameHasDelayed: " + nameHasDelayed);
            } else {

                boolean initialized = !UnsafeAccess.UNSAFE.shouldBeInitialized(checkedClass);

                if (nameHasDelayed && initialized) {
                    errors.add(checkedClass.getName() + ": Check for MustBeDelayed failed");
                }
                if (nameHasSafeEarly && initialized != checkSafeEarly) {
                    errors.add(checkedClass.getName() + ": Check for MustBeSafeEarly failed");
                }
                if (nameHasSafeLate && initialized != checkSafeLate) {
                    errors.add(checkedClass.getName() + ": Check for MustBeSafeLate failed");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new Error(errors.stream().collect(Collectors.joining(System.lineSeparator())));
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime(UnsafeAccess.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        checkClasses(false, false);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        checkClasses(true, false);
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
        checkClasses(true, true);
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        checkClasses(true, true);
    }
}

/**
 * In addition to the initialization checks in {@link TestClassInitializationMustBeSafeFeature},
 * suffixes MustBeSafe and MustBeDelayed are parsed by an external script in the tests after the
 * image is built. Every class that ends with `MustBeSafe` should be eagerly initialized and every
 * class that ends with `MustBeDelayed` should be initialized at runtime.
 */
public class TestClassInitializationMustBeSafe {
    static int pure() {
        return transitivelyPure() + 42;
    }

    private static int transitivelyPure() {
        return 42;
    }

    public static void main(String[] args) {
        System.out.println(PureMustBeSafeEarly.v);
        System.out.println(PureCallMustBeSafeEarly.v);
        System.out.println(InitializesPureMustBeDelayed.v);
        System.out.println(NonPureMustBeDelayed.v);
        System.out.println(NonPureAccessedFinal.v);
        System.out.println(InitializesNonPureMustBeDelayed.v);
        System.out.println(SystemPropReadMustBeDelayed.v);
        System.out.println(SystemPropWriteMustBeDelayed.v);
        System.out.println(StartsAThreadMustBeDelayed.v);
        System.out.println(CreatesAFileMustBeDelayed.v);
        System.out.println(PureSubclassMustBeDelayed.v);
        System.out.println(PureSubclassInheritsDelayedInterfaceMustBeSafeEarly.v);
        System.out.println(PureSubclassInheritsDelayedDefaultInterfaceMustBeDelayed.v);
        System.out.println(InterfaceNonPureMustBeDelayed.v);
        try {
            System.out.println(ThrowsAnExceptionMustBeDelayed.v);
        } catch (Throwable t) {
            System.out.println(CreatesAnExceptionMustBeDelayed.e.getMessage());
        }
        try {
            System.out.println(ImplicitExceptionInInitializerMustBeDelayed.res);
            throw new RuntimeException("should not reach here");
        } catch (ExceptionInInitializerError ae) {
            if (!(ae.getCause() instanceof ArithmeticException)) {
                throw new RuntimeException("should not reach here");
            }
        }
        try {
            System.out.println(PureDependsOnImplicitExceptionMustBeDelayed.a);
            throw new RuntimeException("should not reach here");
        } catch (NoClassDefFoundError ae) {
            /* This is OK */
        }

        int a = StaticFieldHolderMustBeSafeEarly.a;
        if (a != 111) {
            throw new RuntimeException("expected 111 but found " + a);
        }

        StaticFieldModifer1MustBeDelayed.triggerInitialization();
        a = StaticFieldHolderMustBeSafeEarly.a;
        if (a != 222) {
            throw new RuntimeException("expected 222 but found " + a);
        }

        StaticFieldModifer2MustBeDelayed.triggerInitialization();
        a = StaticFieldHolderMustBeSafeEarly.a;
        if (a != 333) {
            throw new RuntimeException("expected 333 but found " + a);
        }

        System.out.println(RecursionInInitializerMustBeSafeLate.i);

        UnsafeAccessMustBeSafeLate value = UnsafeAccessMustBeSafeLate.value;
        System.out.println(value.f01);
        System.out.println(value.f02);
        System.out.println(value.f03);
        System.out.println(value.f04);
        System.out.println(value.f05);
        System.out.println(value.f06);
        System.out.println(value.f07);
        System.out.println(value.f08);
        System.out.println(value.f09);
        System.out.println(value.f10);
        System.out.println(value.f11);
        System.out.println(value.f12);
        System.out.println(value.f13);
        System.out.println(value.f14);
        System.out.println(value.f15);
        System.out.println(value.f16);

        for (EnumMustBeSafeEarly e : EnumMustBeSafeEarly.values()) {
            System.out.println(e.getValue());
        }

        System.out.println(NativeMethodMustBeDelayed.i);
        NativeMethodMustBeDelayed.foo();
    }
}
// Checkstyle: resume
