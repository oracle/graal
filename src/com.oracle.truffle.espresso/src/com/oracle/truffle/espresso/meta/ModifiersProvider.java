package com.oracle.truffle.espresso.meta;

import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.TRANSIENT;
import static java.lang.reflect.Modifier.VOLATILE;

import java.lang.reflect.Modifier;

/**
 * A Java element (i.e., a class, interface, field or method) that is described by a set of Java
 * language {@linkplain #getModifiers() modifiers}.
 */
public interface ModifiersProvider {

    /**
     * Returns the modifiers for this element.
     */
    int getModifiers();

    /**
     * @see Modifier#isInterface(int)
     */
    default boolean isInterface() {
        return Modifier.isInterface(getModifiers());
    }

    /**
     * @see Modifier#isSynchronized(int)
     */
    default boolean isSynchronized() {
        return Modifier.isSynchronized(getModifiers());
    }

    /**
     * @see Modifier#isStatic(int)
     */
    default boolean isStatic() {
        return Modifier.isStatic(getModifiers());
    }

    /**
     * The setting of the final modifier bit for types is somewhat confusing, so don't export
     * isFinal by default. Subclasses like {@link ResolvedJavaField} and {@link ResolvedJavaMethod}
     * can export it as isFinal, but {@link ResolvedJavaType} can provide a more sensible equivalent
     * like {@link ResolvedJavaType#isLeaf}.
     *
     * @see Modifier#isFinal(int)
     */
    default boolean isFinalFlagSet() {
        return Modifier.isFinal(getModifiers());
    }

    /**
     * @see Modifier#isPublic(int)
     */
    default boolean isPublic() {
        return Modifier.isPublic(getModifiers());
    }

    /**
     * Determines if this element is neither {@linkplain #isPublic() public},
     * {@linkplain #isProtected() protected} nor {@linkplain #isPrivate() private}.
     */
    default boolean isPackagePrivate() {
        return ((PUBLIC | PROTECTED | PRIVATE) & getModifiers()) == 0;
    }

    /**
     * @see Modifier#isPrivate(int)
     */
    default boolean isPrivate() {
        return Modifier.isPrivate(getModifiers());
    }

    /**
     * @see Modifier#isProtected(int)
     */
    default boolean isProtected() {
        return Modifier.isProtected(getModifiers());
    }

    /**
     * @see Modifier#isTransient(int)
     */
    default boolean isTransient() {
        return Modifier.isTransient(getModifiers());
    }

    /**
     * @see Modifier#isStrict(int)
     */
    default boolean isStrict() {
        return Modifier.isStrict(getModifiers());
    }

    /**
     * @see Modifier#isVolatile(int)
     */
    default boolean isVolatile() {
        return Modifier.isVolatile(getModifiers());
    }

    /**
     * @see Modifier#isNative(int)
     */
    default boolean isNative() {
        return Modifier.isNative(getModifiers());
    }

    /**
     * @see Modifier#isAbstract(int)
     */
    default boolean isAbstract() {
        return Modifier.isAbstract(getModifiers());
    }

    /**
     * Checks that the method is concrete and not abstract.
     *
     * @return whether the method is a concrete method
     */
    default boolean isConcrete() {
        return !isAbstract();
    }

    /**
     * This is removed in JDK 9 and should not be used. It used to rely on use of reflection to read
     * non-public values from {@link Modifier}. Such cross-module (i.e., {@code jdk.vm.ci} to
     * {@code java.base}) reflection is disabled in 9 and will require a VM option such
     * {@code --add-exports-private} to be enabled.
     */
    // @Deprecated
    static int jvmFieldModifiers() {
        // Reflection replaced with hard coded values (which will not change in JDK 8).
        final int accEnum = 0x4000;
        final int accSynthethic = 0x1000;
        return PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | VOLATILE | TRANSIENT | accEnum | accSynthethic;
    }

}
