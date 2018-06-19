package com.oracle.truffle.espresso.runtime;

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.espresso.types.TypeDescriptor;

/**
 * A {@link KlassRegistry} maps class names to resolved {@link Klass} instances. Each class loader
 * is associated with a {@link KlassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public class KlassRegistry {

    /**
     * The map from symbol to classes for the classes defined by the class loader associated with
     * this registry. Use of {@link ConcurrentHashMap} allows for atomic insertion while still
     * supporting fast, non-blocking lookup. There's no need for deletion as class unloading removes
     * a whole class registry and all its contained classes.
     */
    private final ConcurrentHashMap<String, Klass> classes = new ConcurrentHashMap<>();

    /**
     * The class loader associated with this registry.
     */
    private final DynamicObject classLoader;

    /**
     * Head of global {@link KlassRegistry} list.
     */
    private static KlassRegistry head;

    /**
     * Link in global {@link KlassRegistry} list.
     */
    private KlassRegistry next;

    public KlassRegistry(DynamicObject classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * Searches for a given type in a registry associated with a given class loader.
     *
     * @param classLoader the class loader to start searching in
     * @param typeDescriptor the type to look for
     * @param searchParents specifies if the {@linkplain ClassLoader#getParent() parents} of
     *            {@code classLoader} should be searched if the type is not in the registry of
     *            {@code classLoader}
     * @return the resolved actor corresponding to {@code typeDescriptor} or {@code null} if not
     *         found
     */
    public static Klass get(DynamicObject classLoader, TypeDescriptor typeDescriptor, boolean searchParents) {
        // TODO
        return null;
    }

}
