package com.oracle.truffle.espresso.impl;

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectClass;
import com.oracle.truffle.espresso.runtime.Utils;
import com.oracle.truffle.espresso.types.TypeDescriptor;

/**
 * A {@link ClassRegistryImpl} maps class names to resolved {@link Klass} instances. Each class loader
 * is associated with a {@link ClassRegistryImpl} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public class ClassRegistryImpl implements ClassRegistry {

    private final EspressoContext context;

    /**
     * The map from symbol to classes for the classes defined by the class loader associated with
     * this registry. Use of {@link ConcurrentHashMap} allows for atomic insertion while still
     * supporting fast, non-blocking lookup. There's no need for deletion as class unloading removes
     * a whole class registry and all its contained classes.
     */
    private final ConcurrentHashMap<TypeDescriptor, Klass> classes = new ConcurrentHashMap<>();

    /**
     * The class loader associated with this registry.
     */
    private final Object classLoader;

    public ClassRegistryImpl(EspressoContext context, Object classLoader) {
        this.context = context;
        this.classLoader = classLoader;
    }

    @Override
    public Klass resolve(TypeDescriptor type) {
        if (type.isArray()) {
            return resolve(type.getComponentType()).getArrayClass();
        }
        assert classLoader != null;



        MethodInfo loadClass = ((StaticObject) classLoader).getKlass().findMethod("loadClass", context.getSignatureDescriptors().make("(Ljava/lang/String;Z)Ljava/lang/Class;"));
        // TODO(peterssen): Should the class be resolved?
        StaticObjectClass guestClass = (StaticObjectClass) loadClass.getCallTarget().call(classLoader, Utils.toGuestString(context, type.toJavaName()), false);
        Klass k = guestClass.getMirror();
        classes.put(type, k);
        return k;
    }

    @Override
    public Klass findLoadedClass(TypeDescriptor type) {
        if (type.isArray()) {
            return findLoadedClass(type.getComponentType()).getArrayClass();
        }
        return classes.get(type);
    }
}
