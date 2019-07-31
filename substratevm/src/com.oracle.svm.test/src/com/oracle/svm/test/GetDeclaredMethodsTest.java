package com.oracle.svm.test;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class GetDeclaredMethodsTest {

    @Test
    public void testGetMethods() {
        Method[] methods = null;

        methods = Child.class.getDeclaredMethods();
        assertHasExactlyMethods(methods,
                        "childDoSomething",
                        "childDoSomethingPrivate");
    }

    private void assertHasExactlyMethods(Method[] methods, String... names) {
        for (Method method : methods) {
            boolean found = false;
            for (String name : names) {
                if (method.getName().equals(name)) {
                    found = true;
                    break;
                }
            }
            Assert.assertTrue("Method with name " + method.getName() + " not expected in " + Arrays.asList(names), found);
        }
        for (String name : names) {
            assertHasMethod(methods, name);
        }
    }

    private void assertHasMethod(Method[] methods, String name) {
        boolean found = false;

        for (Method method : methods) {
            if (method.getName().equals(name)) {
                found = true;
                break;
            }
        }

        Assert.assertTrue("Expected method " + name + " in " + Arrays.asList(methods), found);
    }

    class Parent {

        public void parentDoSomething() {

        }

        private void parentDoSomethingPrivate() {

        }
    }

    class Child extends Parent {

        public void childDoSomething() {

        }

        private void childDoSomethingPrivate() {

        }

    }
}
