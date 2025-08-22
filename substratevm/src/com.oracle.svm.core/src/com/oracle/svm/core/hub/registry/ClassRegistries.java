/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.hub.registry;

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.ClassLoadingSupport;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.JavaVersion;
import com.oracle.svm.espresso.classfile.ParsingContext;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.classfile.perf.TimerCollection;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.internal.loader.BootLoader;
import jdk.internal.misc.PreviewFeatures;

/**
 * Class registries are used when native image respects the class loader hierarchy. There is one
 * {@linkplain AbstractClassRegistry registry} per class loader. Each registry maps class names to
 * classes. This allows multiple class loaders to load classes with the same name without conflicts.
 * <p>
 * Class registries are attached to class loaders through an
 * {@linkplain Target_java_lang_ClassLoader#classRegistry injected field}, which binds their
 * lifetime to that of the class loader.
 * <p>
 * Classes that require dynamic lookup via reflection or other mechanisms at runtime are
 * pre-registered in their respective declaring class loader's registry during build time. At
 * runtime class registries can grow in 2 ways:
 * <ul>
 * <li>When a class lookup is answered through delegation rather than directly. This means the next
 * lookup can use that cached answer directly as required by JVMS sect. 5.3.1 & 5.3.2.</li>
 * <li>When a class is defined (i.e., by runtime class loading when it's enabled)</li>
 * </ul>
 */
public final class ClassRegistries implements ParsingContext {
    public final TimerCollection timers = TimerCollection.create(false);

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final ConcurrentHashMap<ClassLoader, AbstractClassRegistry> buildTimeRegistries;

    private final AbstractClassRegistry bootRegistry;
    private final EconomicMap<String, String> bootPackageToModule;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ClassRegistries() {
        if (RuntimeClassLoading.isSupported()) {
            bootRegistry = new BootClassRegistry();
        } else {
            bootRegistry = new AOTClassRegistry(null);
        }
        buildTimeRegistries = new ConcurrentHashMap<>();
        bootPackageToModule = computeBootPackageToModuleMap();
    }

    private static EconomicMap<String, String> computeBootPackageToModuleMap() {
        Field moduleField = ReflectionUtil.lookupField(ReflectionUtil.lookupClass(false, "java.lang.NamedPackage"), "module");
        EconomicMap<String, String> bootPackageToModule = EconomicMap.create();
        BootLoader.packages().forEach(p -> {
            try {
                bootPackageToModule.put(p.getName(), ((Module) moduleField.get(p)).getName());
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        });
        return bootPackageToModule;
    }

    @Fold
    public static ClassRegistries singleton() {
        return ImageSingletons.lookup(ClassRegistries.class);
    }

    static String getBootModuleForPackage(String pkg) {
        return singleton().bootPackageToModule.get(pkg);
    }

    public static String[] getSystemPackageNames() {
        String[] result = new String[singleton().bootPackageToModule.size()];
        MapCursor<String, String> cursor = singleton().bootPackageToModule.getEntries();
        int i = 0;
        while (cursor.advance()) {
            result[i++] = cursor.getKey();
        }
        assert i == result.length;
        return result;
    }

    public static Class<?> findBootstrapClass(String name) {
        try {
            return singleton().resolve(name, null);
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("The boot class loader shouldn't throw ClassNotFoundException", e);
        }
    }

    public static Class<?> findLoadedClass(String name, ClassLoader loader) {
        if (throwMissingRegistrationErrors() && shouldFollowReflectionConfiguration() && !ClassForNameSupport.isRegisteredClass(name)) {
            MissingReflectionRegistrationUtils.reportClassAccess(name);
            return null;
        }
        ByteSequence typeBytes = ByteSequence.createTypeFromName(name);
        Symbol<Type> type = SymbolsSupport.getTypes().lookupValidType(typeBytes);
        Class<?> result = null;
        if (type != null) {
            result = singleton().getRegistry(loader).findLoadedClass(type);
        }
        return result;
    }

    public static ParsingContext getParsingContext() {
        assert RuntimeClassLoading.isSupported();
        return singleton();
    }

    public static Class<?> forName(String name, ClassLoader loader) throws ClassNotFoundException {
        return singleton().resolveOrThrowException(name, loader);
    }

    private Class<?> resolveOrThrowException(String name, ClassLoader loader) throws ClassNotFoundException {
        Class<?> clazz = resolve(name, loader);
        if (clazz == null) {
            throw new ClassNotFoundException(name);
        }
        return clazz;
    }

    /**
     * This resolves the given class name. Expects dot-names.
     * <p>
     * It may or may not throw a {@link ClassNotFoundException} if the class is not found: the boot
     * class loader returns null while user-defined class loaders throw
     * {@link ClassNotFoundException}. This approach avoids unnecessary exceptions during class
     * loader delegation.
     */
    private Class<?> resolve(String name, ClassLoader loader) throws ClassNotFoundException {
        if (shouldFollowReflectionConfiguration()) {
            if (throwMissingRegistrationErrors() && !ClassForNameSupport.isRegisteredClass(name)) {
                MissingReflectionRegistrationUtils.reportClassAccess(name);
                if (loader == null) {
                    return null;
                }
                throw new ClassNotFoundException(name);
            }
            if (!RuntimeClassLoading.isSupported()) {
                Throwable savedException = ClassForNameSupport.getSavedException(name);
                if (savedException != null) {
                    if (savedException instanceof Error error) {
                        throw error;
                    } else if (savedException instanceof ClassNotFoundException cnfe) {
                        throw cnfe;
                    }
                    throw VMError.shouldNotReachHere("Unexpected exception type", savedException);
                }
            }
        }
        int arrayDimensions = 0;
        while (arrayDimensions < name.length() && name.charAt(arrayDimensions) == '[') {
            arrayDimensions++;
        }
        if (arrayDimensions == name.length()) {
            throw new ClassNotFoundException(name);
        }
        Class<?> elementalResult;
        if (arrayDimensions > 0) {
            /*
             * We know that the array name was registered for reflection. The elemental type might
             * not be, so we have to ignore registration during its lookup.
             */
            ClassLoadingSupport classLoadingSupport = ClassLoadingSupport.singleton();
            classLoadingSupport.startIgnoreReflectionConfigurationScope();
            try {
                elementalResult = resolveElementalType(name, arrayDimensions, loader);
            } finally {
                classLoadingSupport.endIgnoreReflectionConfigurationScope();
            }
        } else {
            elementalResult = resolveInstanceType(name, loader);
        }
        if (elementalResult == null) {
            if (loader == null) {
                return null;
            }
            throw new ClassNotFoundException(name);
        }
        if (arrayDimensions > 0) {
            Class<?> result = getArrayClass(name, elementalResult, arrayDimensions);
            if (result == null && loader != null) {
                throw new ClassNotFoundException(name);
            }
            return result;
        }
        return elementalResult;
    }

    private Class<?> resolveElementalType(String fullName, int arrayDimensions, ClassLoader loader) throws ClassNotFoundException {
        if (fullName.length() == arrayDimensions + 1) {
            return switch (fullName.charAt(arrayDimensions)) {
                case 'Z' -> boolean.class;
                case 'B' -> byte.class;
                case 'C' -> char.class;
                case 'S' -> short.class;
                case 'I' -> int.class;
                case 'F' -> float.class;
                case 'J' -> long.class;
                case 'D' -> double.class;
                default -> null; // also 'V'
            };
        }
        assert fullName.length() > arrayDimensions;
        ByteSequence elementalType = ByteSequence.createReplacingDot(fullName, arrayDimensions);
        return resolveInstanceType(loader, elementalType);
    }

    private Class<?> resolveInstanceType(String name, ClassLoader loader) throws ClassNotFoundException {
        ByteSequence elementalType = ByteSequence.createTypeFromName(name);
        return resolveInstanceType(loader, elementalType);
    }

    private Class<?> resolveInstanceType(ClassLoader loader, ByteSequence elementalType) throws ClassNotFoundException {
        Symbol<Type> type = SymbolsSupport.getTypes().getOrCreateValidType(elementalType);
        if (type == null) {
            return null;
        }
        return getRegistry(loader).loadClass(type);
    }

    private static Class<?> getArrayClass(String name, Class<?> elementalResult, int arrayDimensions) {
        DynamicHub hub = SubstrateUtil.cast(elementalResult, DynamicHub.class);
        int remainingDims = arrayDimensions;
        while (remainingDims > 0) {
            if (hub.getArrayHub() == null) {
                if (RuntimeClassLoading.isSupported()) {
                    RuntimeClassLoading.getOrCreateArrayHub(hub);
                } else {
                    if (throwMissingRegistrationErrors()) {
                        MissingReflectionRegistrationUtils.reportClassAccess(name);
                    }
                    return null;
                }
            }
            remainingDims--;
            hub = hub.getArrayHub();
        }
        return SubstrateUtil.cast(hub, Class.class);
    }

    public static Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ClassDefinitionInfo info) {
        // name is a "binary name": `foo.Bar$1`
        assert RuntimeClassLoading.isSupported();
        if (shouldFollowReflectionConfiguration() && throwMissingRegistrationErrors() && !ClassForNameSupport.isRegisteredClass(name)) {
            MissingReflectionRegistrationUtils.reportClassAccess(name);
            // The defineClass path usually can't throw ClassNotFoundException
            throw sneakyThrow(new ClassNotFoundException(name));
        }
        ByteSequence typeBytes = ByteSequence.createTypeFromName(name);
        Symbol<Type> type = SymbolsSupport.getTypes().getOrCreateValidType(typeBytes);
        if (type == null) {
            throw new NoClassDefFoundError(name);
        }
        AbstractRuntimeClassRegistry registry = (AbstractRuntimeClassRegistry) singleton().getRegistry(loader);
        return registry.defineClass(type, b, off, len, info);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    public static String loaderNameAndId(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        return SubstrateUtil.cast(loader, Target_java_lang_ClassLoader.class).nameAndId();
    }

    private AbstractClassRegistry getRegistry(ClassLoader loader) {
        if (loader == null) {
            return bootRegistry;
        }
        Target_java_lang_ClassLoader svmLoader = SubstrateUtil.cast(loader, Target_java_lang_ClassLoader.class);
        AbstractClassRegistry registry = svmLoader.classRegistry;
        if (registry == null) {
            synchronized (loader) {
                registry = svmLoader.classRegistry;
                if (registry == null) {
                    if (RuntimeClassLoading.isSupported()) {
                        registry = new UserDefinedClassRegistry(loader);
                    } else {
                        registry = new AOTClassRegistry(loader);
                    }
                    svmLoader.classRegistry = registry;
                }
            }
        }
        return registry;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void addAOTClass(ClassLoader loader, Class<?> cls) {
        singleton().getBuildTimeRegistry(loader).addAOTType(cls);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private AbstractClassRegistry getBuildTimeRegistry(ClassLoader loader) {
        if (loader == null) {
            return bootRegistry;
        }
        return this.buildTimeRegistries.computeIfAbsent(loader, l -> {
            AbstractClassRegistry newRegistry;
            if (RuntimeClassLoading.isSupported()) {
                newRegistry = new UserDefinedClassRegistry(l);
            } else {
                newRegistry = new AOTClassRegistry(l);
            }
            return newRegistry;
        });
    }

    public static class ClassRegistryComputer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            if (ClassForNameSupport.respectClassLoader()) {
                assert receiver != null;
                return ClassRegistries.singleton().getBuildTimeRegistry((ClassLoader) receiver);
            }
            return originalValue;
        }
    }

    @Override
    public JavaVersion getJavaVersion() {
        return JavaVersion.HOST_VERSION;
    }

    @Override
    public boolean isStrictJavaCompliance() {
        return false;
    }

    @Override
    public TimerCollection getTimers() {
        return timers;
    }

    @Override
    public boolean isPreviewEnabled() {
        return PreviewFeatures.isEnabled();
    }

    final Logger logger = new Logger() {
        // Checkstyle: Allow raw info or warning printing - begin
        @Override
        public void log(String message) {
            Log.log().string("Warning: ").string(message).newline();
        }

        @Override
        public void log(Supplier<String> messageSupplier) {
            Log.log().string("Warning: ").string(messageSupplier.get()).newline();
        }

        @Override
        public void log(String message, Throwable throwable) {
            Log.log().string("Warning: ").string(message).newline();
            Log.log().exception(throwable);
        }
        // Checkstyle: Allow raw info or warning printing - end
    };

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public Symbol<Name> getOrCreateName(ByteSequence byteSequence) {
        return SymbolsSupport.getNames().getOrCreate(byteSequence);
    }

    @Override
    public Symbol<Type> getOrCreateTypeFromName(ByteSequence byteSequence) {
        return SymbolsSupport.getTypes().getOrCreateValidType(TypeSymbols.nameToType(byteSequence));
    }

    @Override
    public Symbol<? extends ModifiedUTF8> getOrCreateUtf8(ByteSequence byteSequence) {
        // Note: all symbols are strong for now
        return SymbolsSupport.getUtf8().getOrCreateValidUtf8(byteSequence, true);
    }

    private static boolean shouldFollowReflectionConfiguration() {
        return ClassLoadingSupport.singleton().followReflectionConfiguration();
    }
}
