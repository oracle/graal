package com.oracle.graalvm.locator;

class JDKServices {

    static ClassLoader getBaseClassLoader(@SuppressWarnings("unused") Class<?> c) {
        return c.getClassLoader();
    }

}
