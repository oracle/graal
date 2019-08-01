package com.oracle.svm.test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class GetConstructorsTest {

    @Test
    public void testGetConstructors() {

        Constructor<?>[] constructors = Foo.class.getConstructors();

        assertHasConstructor(constructors);
        assertHasConstructor(constructors, String.class);
        assertHasNotConstructor(constructors, boolean.class);
    }

    private void assertHasConstructor(Constructor<?>[] constructors, Class<?>... expectedParamTypes) {
        boolean found = false;
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (!Arrays.equals(paramTypes, expectedParamTypes)) {
                continue;
            }
            found = true;
        }

        Assert.assertTrue("Expected to find constructor matching " + Arrays.asList(expectedParamTypes), found);
    }

    private void assertHasNotConstructor(Constructor<?>[] constructors, Class<?>... expectedParamTypes) {
        boolean found = false;
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (!Arrays.equals(paramTypes, expectedParamTypes)) {
                continue;
            }
            found = true;
        }

        Assert.assertFalse("Expected to not find constructor matching " + Arrays.asList(expectedParamTypes), found);
    }

    static class Foo {

        public Foo() {

        }

        public Foo(String name) {

        }

        private Foo(boolean truthiness) {

        }

    }
}
