package org.graalvm.polyglot;

public class DefaultRuntimeNameMapper implements RuntimeNameMapper {

    @Override
    public String getClass(String name) {
        return name;
    }
    @Override
    public String getClass(Class<?> clazz, String name) {
        return name;
    }

    @Override
    public String getField(Class<?> clazz, String name) {
        return name;
    }

    @Override
    public String getMethod(Class<?> clazz, String name) {
        return name;
    }
}
