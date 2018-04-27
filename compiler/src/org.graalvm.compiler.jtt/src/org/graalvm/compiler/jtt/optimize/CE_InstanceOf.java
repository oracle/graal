package org.graalvm.compiler.jtt.optimize;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;

public class CE_InstanceOf extends JTTTest {

    static class A {
    }

    static class B extends A {
    }

    static class C extends A {
    }

    public static A testRedundantCast(Object value) {
        if (value != null && value.getClass() == A.class) {
            return (A) value;
        }
        return null;
    }

    public static boolean testRedundantInstanceOf(Object value) {
        if (value != null && value.getClass() == A.class) {
            return (value instanceof A);
        }
        return false;
    }

    public static boolean testRedundantInstanceOf2(Object value) {
        if (value.getClass() == A.class) {
            return (value instanceof A);
        }
        return false;
    }

    public static boolean testNonRedundantInstanceOf(Object value) {
        if (value instanceof A) {
            return (value != null && value.getClass() == A.class);
        }
        return false;
    }

    public static Object testNonRedundantInstanceOf2(Object value) {
        if (value != null && value.getClass() == Object[].class) {
            return ((Object[]) value)[0];
        }
        return null;
    }

    private static final List<A> testArgs = Collections.unmodifiableList(Arrays.asList(new A(), new B(), null));

    @Test
    public void run0() throws Throwable {
        for (A a : testArgs) {
            runTest("testRedundantCast", a);
        }
    }

    @Test
    public void run1() throws Throwable {
        for (A a : testArgs) {
            runTest("testRedundantInstanceOf", a);
        }
    }

    @Test
    public void run2() throws Throwable {
        for (A a : testArgs) {
            runTest("testRedundantInstanceOf2", a);
        }
    }

    @Test
    public void run3() throws Throwable {
        for (A a : testArgs) {
            runTest("testNonRedundantInstanceOf", a);
        }
    }

    @Test
    public void run4() throws Throwable {
        for (A a : testArgs) {
            runTest("testNonRedundantInstanceOf2", a);
        }
    }
}
