package com.oracle.svm.test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class GetDeclaredConstructorsTest {

    @Test
    public void testGetConstructors() {

        Constructor<?>[] constructors = Foo.class.getDeclaredConstructors();

        assertHasConstructor(constructors);
        assertHasConstructor(constructors, String.class);
        assertHasConstructor(constructors, boolean.class);
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

    static class Foo {

        public Foo() {

        }

        public Foo(String name) {

        }

        private Foo(boolean truthiness) {

        }

    }
}
