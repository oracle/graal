package com.oracle.truffle.espresso.runtime;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.types.TypeDescriptor;

/**
 * A {@link KlassRegistry} maps class names to resolved {@link Klass} instances. Each class loader
 * is associated with a {@link KlassRegistry} and vice versa.
 *
 * This class is analogous to the ClassLoaderData C++ class in HotSpot.
 */
public class KlassRegistry {

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
    private final DynamicObject classLoader;

    private KlassRegistry(EspressoContext context, DynamicObject classLoader) {
        this.context = context;
        this.classLoader = classLoader;
    }

    /**
     * Searches for a given type in a registry associated with a given class loader.
     *
     * @param classLoader the class loader to start searching in
     * @param typeDescriptor the type to look for
     * @return the resolved actor corresponding to {@code typeDescriptor} or {@code null} if not
     *         found
     */
    public static Klass get(EspressoContext context, DynamicObject classLoader, TypeDescriptor typeDescriptor) {
        KlassRegistry klassRegistry = makeRegistry(context, classLoader);
        return klassRegistry.resolveKlass(context, classLoader, typeDescriptor);
    }

    private Klass resolveKlass(EspressoContext context, DynamicObject classLoader, TypeDescriptor typeDescriptor) {
        Klass klass = classes.get(typeDescriptor);
        if (klass == null) {
            if (classLoader != null) {
                throw EspressoLanguage.unimplemented();
            } else {
                Klass hostClass = null;
                String className = typeDescriptor.toJavaName();

                ClasspathFile classpathFile = context.getClasspath().readClassFile(className);
                if (classpathFile == null) {
                    throw new NoClassDefFoundError(className);
                }
                ClassfileParser parser = new ClassfileParser(classLoader, classpathFile, typeDescriptor, hostClass, context);
                klass = parser.loadClass();
                classes.put(typeDescriptor, klass);
            }
        }
        return klass;
    }

    private static KlassRegistry makeRegistry(EspressoContext context, DynamicObject classLoader) {
        List<KlassRegistry> klassRegistries = context.getKlassRegistries();
        synchronized (klassRegistries) {
            KlassRegistry registry = findRegistry(classLoader, klassRegistries);
            if (registry == null) {
                registry = new KlassRegistry(context, classLoader);
                klassRegistries.add(registry);
            }
            return registry;
        }
    }

    private static KlassRegistry findRegistry(DynamicObject classLoader, List<KlassRegistry> klassRegistries) {
        for (KlassRegistry kr : klassRegistries) {
            if (kr.classLoader == classLoader) {
                return kr;
            }
        }
        return null;
    }
}
