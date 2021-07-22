package org.graalvm.polyglot;

public interface RuntimeNameMapper {

    /**
     * Returns the mapped name of a class
     * @param name The original name
     * @return The mapped name
     */
    String getClass(String name);
    /**
     * Returns the mapped name of an inner class
     * @param clazz The enclosing class
     * @param name The original name
     * @return The mapped name
     */
    String getClass(Class<?> clazz, String name);

    /**
     * Returns the mapped name of a field
     * @param clazz The enclosing class
     * @param name The original name
     * @return The mapped name
     */
    String getField(Class<?> clazz, String name);

    /**
     * Returns the mapped name of a method
     * @param clazz The enclosing class
     * @param name The original name
     * @return The mapped name
     */
    String getMethod(Class<?> clazz, String name);
}
