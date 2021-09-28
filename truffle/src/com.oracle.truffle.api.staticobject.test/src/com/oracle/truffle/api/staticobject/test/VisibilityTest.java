package com.oracle.truffle.api.staticobject.test;

public class VisibilityTest {

    static Class<?> getPrivateClass() {
        return PrivateClass.class;
    }

    private static class PrivateClass {

    }
}
