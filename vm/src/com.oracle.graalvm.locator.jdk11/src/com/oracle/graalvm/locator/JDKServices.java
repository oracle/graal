package com.oracle.graalvm.locator;

class JDKServices {

    static ClassLoader getBaseClassLoader(@SuppressWarnings("unused") Class<?> c) {
        return ClassLoader.getPlatformClassLoader();
    }

}
