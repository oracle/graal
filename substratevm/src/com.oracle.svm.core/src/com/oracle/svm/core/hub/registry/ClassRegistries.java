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
import static jdk.graal.compiler.options.OptionStability.EXPERIMENTAL;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.FieldValueTransformer;
import org.graalvm.nativeimage.impl.ClassLoadingSupport;

import com.oracle.svm.configure.ClassNameSupport;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.ClassDefinitionInfo;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.core.jdk.Target_java_lang_ClassLoader;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.espresso.classfile.JavaVersion;
import com.oracle.svm.espresso.classfile.ParsingContext;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.ModifiedUTF8;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.classfile.perf.TimerCollection;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.LayerVerifiedOption;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
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
@SingletonTraits(access = BuiltinTraits.AllAccess.class, layeredCallbacks = BuiltinTraits.NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public final class ClassRegistries implements ParsingContext {
    public static final class Options {
        @LayerVerifiedOption(kind = LayerVerifiedOption.Kind.Changed, severity = LayerVerifiedOption.Severity.Error)//
        @Option(help = "Class.forName and similar respect their class loader argument, and resource lookups " +
                        "use class loader delegation instead of a global namespace.", stability = EXPERIMENTAL)//
        public static final HostedOptionKey<Boolean> ClassForNameRespectsClassLoader = new HostedOptionKey<>(false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class RespectsClassLoader implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return respectClassLoader();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class IgnoresClassLoader implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return !respectClassLoader();
        }
    }

    @Fold
    public static boolean respectClassLoader() {
        return Options.ClassForNameRespectsClassLoader.getValue();
    }

    private static final ReferenceQueue<Object> collectedLoaders = new ReferenceQueue<>();

    public final TimerCollection timers = TimerCollection.create(false);

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final ConcurrentHashMap<ClassLoader, AbstractClassRegistry> buildTimeRegistries;

    private final AbstractClassRegistry bootRegistry;
    private final EconomicMap<String, String> bootPackageToModule;

    /**
     * Holds all class names known to the image build. The value linked to each name is a
     * conditional value specifying when the name can be queried at run-time, and holding a
     * Throwable object if querying the class with this name should throw a specific error at
     * run-time, excluding ClassNotFoundException, or null otherwise.
     */
    private final EconomicMap<String, ConditionalRuntimeValue<Throwable>> knownClassNames;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ClassRegistries() {
        if (RuntimeClassLoading.isSupported()) {
            bootRegistry = new BootClassRegistry();
        } else {
            bootRegistry = new AOTClassRegistry(null);
        }
        buildTimeRegistries = new ConcurrentHashMap<>();
        bootPackageToModule = computeBootPackageToModuleMap();
        knownClassNames = EconomicMap.create();
    }

    private static EconomicMap<String, String> computeBootPackageToModuleMap() {
        EconomicMap<String, String> bootPackageToModule = EconomicMap.create();
        JVMCIReflectionUtil.bootLoaderPackages().forEach(p -> bootPackageToModule.put(p.getName(), p.module().getName()));
        return bootPackageToModule;
    }

    public static ClassRegistries currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(ClassRegistries.class, false, true);
    }

    public static ClassRegistries[] layeredSingletons() {
        return MultiLayeredImageSingleton.getAllLayers(ClassRegistries.class);
    }

    public static ClassRegistries runtimeLastLayer() {
        var singletons = layeredSingletons();
        return singletons[singletons.length - 1];
    }

    static String getBootModuleForPackage(String pkg) {
        for (var singleton : layeredSingletons()) {
            var module = singleton.bootPackageToModule.get(pkg);
            if (module != null) {
                return module;
            }
        }
        return null;
    }

    /**
     * Returns the boot loader package location in the format expected by
     * {@code BootLoader.PackageHelper}.
     *
     * @param internalPackageName package name in internal form (e.g. "org/foo/impl")
     */
    public static String getSystemPackageLocation(String internalPackageName) {
        String module = getBootModuleForPackage(internalPackageName.replace('/', '.'));
        return module == null ? null : "jrt:/" + module;
    }

    public static String[] getSystemPackageNames() {
        Set<String> systemPackageNames = new HashSet<>();
        for (var singleton : layeredSingletons()) {
            for (var key : singleton.bootPackageToModule.getKeys()) {
                systemPackageNames.add(key);
            }
        }
        return systemPackageNames.toArray(String[]::new);
    }

    public static Class<?> findBootstrapClass(String name) {
        try {
            ClassNotFoundException classNotFoundException = null;
            for (var singleton : layeredSingletons()) {
                var resolved = singleton.resolve(name, null);
                if (resolved instanceof Class<?> found) {
                    maybeThrowMissingRegistrationError((DynamicHub) resolved, name);
                    return found;
                } else if (resolved instanceof ClassNotFoundException cnfe) {
                    classNotFoundException = cnfe;
                }
            }
            if (classNotFoundException == null) {
                maybeThrowMissingRegistrationError(null, name);
            }
            return null;
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("The boot class loader shouldn't throw ClassNotFoundException", e);
        }
    }

    public static Class<?> findLoadedClass(String name, ClassLoader loader) {
        ByteSequence typeBytes = ByteSequence.createTypeFromName(name);
        Symbol<Type> type = SymbolsSupport.getTypes().lookupValidType(typeBytes);
        ClassNotFoundException classNotFoundException = null;
        for (var singleton : layeredSingletons()) {
            Class<?> result = null;
            if (type != null) {
                result = singleton.getRegistry(loader).findLoadedClass(type);
            }
            if (result == null) {
                result = PredefinedClassesSupport.getLoadedForNameOrNull(name, loader);
            }
            Object checkedResult = singleton.checkResult(DynamicHub.fromClass(result), name);
            if (checkedResult instanceof Class<?> found) {
                maybeThrowMissingRegistrationError((DynamicHub) checkedResult, name);
                return found;
            } else if (checkedResult instanceof ClassNotFoundException cnfe) {
                classNotFoundException = cnfe;
            }
        }
        if (classNotFoundException == null) {
            maybeThrowMissingRegistrationError(null, name);
        }
        return null;
    }

    public static ParsingContext getParsingContext() {
        assert RuntimeClassLoading.isSupported();
        return runtimeLastLayer();
    }

    public static Class<?> forName(String name, ClassLoader loader) throws ClassNotFoundException {
        ClassNotFoundException classNotFoundException = null;
        for (var singleton : layeredSingletons()) {
            Object result = singleton.resolve(name, loader);
            if (result instanceof Class<?> found) {
                maybeThrowMissingRegistrationError((DynamicHub) result, name);
                return found;
            } else if (result instanceof ClassNotFoundException cnfe) {
                classNotFoundException = cnfe;
            }
        }
        if (classNotFoundException == null) {
            maybeThrowMissingRegistrationError(null, name);
            classNotFoundException = new ClassNotFoundException(name);
        }
        throw classNotFoundException;
    }

    /**
     * This resolves the given class name. Expects dot-names.
     * <p>
     * It may or may not throw a {@link ClassNotFoundException} if the class is not found: the boot
     * class loader returns null while user-defined class loaders throw
     * {@link ClassNotFoundException}. This approach avoids unnecessary exceptions during class
     * loader delegation.
     */
    private Object resolve(String name, ClassLoader loader) throws ClassNotFoundException {
        int arrayDimensions = 0;
        while (arrayDimensions < name.length() && name.charAt(arrayDimensions) == '[') {
            arrayDimensions++;
        }
        if (arrayDimensions == name.length() || arrayDimensions > 255) {
            if (loader == null) {
                return null;
            }
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
        Class<?> result = elementalResult;
        if (arrayDimensions > 0 && result != null) {
            result = getArrayClass(elementalResult, arrayDimensions);
        }
        if (result == null) {
            result = PredefinedClassesSupport.getLoadedForNameOrNull(name, loader);
        }
        return checkResult(SubstrateUtil.cast(result, DynamicHub.class), name);
    }

    /*
     * Returns DynamicHub if the class can be accessed, ClassNotFoundException if the type wasn't
     * found but its name was registered (i.e. we shouldn't throw a missing registration error for
     * this query), and null otherwise.
     */
    private Object checkResult(DynamicHub result, String name) {
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceReflectionType(name);
        }
        if (result == null && shouldFollowReflectionConfiguration()) {
            Throwable savedException = getSavedException(name);
            if (savedException != null) {
                if (savedException instanceof Error error) {
                    if (!RuntimeClassLoading.isSupported()) {
                        throw error;
                    } else {
                        return null;
                    }
                } else if (savedException instanceof ClassNotFoundException cnfe) {
                    return cnfe;
                }
                throw VMError.shouldNotReachHere("Unexpected exception type", savedException);
            }
        }
        return result;
    }

    private static void maybeThrowMissingRegistrationError(DynamicHub result, String name) {
        if (throwMissingRegistrationErrors() && shouldFollowReflectionConfiguration() && ClassNameSupport.isValidReflectionName(name) && shouldThrowMissingRegistrationError(result)) {
            MissingReflectionRegistrationUtils.reportClassAccess(name);
        }
    }

    private static boolean shouldThrowMissingRegistrationError(DynamicHub result) {
        if (result == null) {
            return true;
        }
        RuntimeDynamicAccessMetadata dynamicAccess = result.getDynamicAccessMetadata();
        return dynamicAccess == null || !dynamicAccess.satisfied();
    }

    private Throwable getSavedException(String name) {
        var cond = knownClassNames.get(name);
        if (cond == null || cond.getDynamicAccessMetadata() == null || !cond.getDynamicAccessMetadata().satisfied()) {
            return null;
        }
        Throwable exception = cond.getValue();
        if (exception == null) {
            exception = new ClassNotFoundException(name);
        }
        return exception;
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

    private static Class<?> getArrayClass(Class<?> elementalResult, int arrayDimensions) {
        assert elementalResult != void.class : "Must be filtered in the caller";
        assert arrayDimensions > 0 && arrayDimensions <= 255 : "Must be filtered in the caller";
        DynamicHub hub = SubstrateUtil.cast(elementalResult, DynamicHub.class);
        int remainingDims = arrayDimensions;
        while (remainingDims > 1) {
            DynamicHub arrayHub = hub.getOrCreateArrayHub();
            if (arrayHub == null) {
                return null;
            }
            remainingDims--;
            hub = arrayHub;
        }
        // Perform the MissingRegistrationError check for the final element
        hub = hub.arrayType();
        return SubstrateUtil.cast(hub, Class.class);
    }

    public static Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len, ClassDefinitionInfo info) {
        // name can use either dot or slash package separators.
        assert RuntimeClassLoading.isSupported();
        String reflectionName = toReflectionName(name);
        if (throwMissingRegistrationErrors() && shouldFollowReflectionConfiguration() && !isRegisteredClassName(reflectionName)) {
            MissingReflectionRegistrationUtils.reportClassAccess(reflectionName);
            // The defineClass path usually can't throw ClassNotFoundException
            throw sneakyThrow(new ClassNotFoundException(name));
        }
        AbstractRuntimeClassRegistry registry = (AbstractRuntimeClassRegistry) runtimeLastLayer().getRegistry(loader);
        if (name != null) {
            ByteSequence typeBytes = ByteSequence.createTypeFromName(name);
            Symbol<Type> type = SymbolsSupport.getTypes().getOrCreateValidType(typeBytes);
            if (type == null) {
                throw new NoClassDefFoundError(name);
            }
            return registry.defineClass(type, b, off, len, info);
        } else {
            return registry.defineClass(null, b, off, len, info);
        }
    }

    private static String toReflectionName(String name) {
        return name == null ? null : ClassNameSupport.jniNameToReflectionName(name);
    }

    private static boolean isRegisteredClassName(String name) {
        for (var singleton : layeredSingletons()) {
            if (singleton.knownClassNames.containsKey(name)) {
                return true;
            }
        }
        return false;
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

    public AbstractClassRegistry getRegistry(ClassLoader loader) {
        if (loader == null || !respectClassLoader()) {
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
                        initializeLoaderWeakSelf(loader);
                        maybePurgeConstraints();
                    } else {
                        registry = new AOTClassRegistry(loader);
                    }
                    svmLoader.classRegistry = registry;
                }
            }
        }
        return registry;
    }

    private static void initializeLoaderWeakSelf(Object loader) {
        SubstrateUtil.cast(loader, Target_java_lang_ClassLoader.class).weakSelf = new WeakReference<>(loader, collectedLoaders);
    }

    private static void maybePurgeConstraints() {
        if (collectedLoaders.poll() != null) {
            while (collectedLoaders.poll() != null) {
                // Clear the reference queue.
            }
            // Reclaim recorded constraints for collected loaders.
            CremaSupport.singleton().purgeLoadingConstraints();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void addAOTClass(ClassLoader loader, Class<?> cls) {
        Class<?> elementType = cls;
        while (elementType.isArray()) {
            elementType = elementType.getComponentType();
        }
        if (elementType == Class.class) {
            /*
             * Workaround for substitution classes in generic signatures. A proper fix will require
             * rewriting generic signatures of methods in substitution classes without an equivalent
             * original method, which is currently limited to some DynamicHub methods
             */
            ClassRegistries.addAOTClass(loader, DynamicHub.class);
        }
        if (!elementType.isPrimitive()) {
            currentLayer().getBuildTimeRegistry(loader).addAOTType(elementType);
        }
        addKnownClassName(AccessCondition.unconditional(), cls.getName(), null, false);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void addKnownClassName(AccessCondition condition, String typeName, Throwable exception, boolean preserved) {
        var knownClassNamesMap = currentLayer().knownClassNames;
        synchronized (knownClassNamesMap) {
            var cond = knownClassNamesMap.get(typeName);
            if (cond == null) {
                cond = new ConditionalRuntimeValue<>(RuntimeDynamicAccessMetadata.createHosted(condition, preserved), exception);
            } else {
                cond.getDynamicAccessMetadata().addCondition(condition);
                if (!preserved) {
                    cond.getDynamicAccessMetadata().setNotPreserved();
                }
                if (cond.getValueUnconditionally() == null && exception != null) {
                    cond = new ConditionalRuntimeValue<>(cond.getDynamicAccessMetadata(), exception);
                }
            }
            knownClassNamesMap.put(typeName, cond);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private AbstractClassRegistry getBuildTimeRegistry(ClassLoader loader) {
        if (loader == null || !respectClassLoader()) {
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public static class ClassRegistryComputer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            assert receiver != null;
            return ClassRegistries.currentLayer().getBuildTimeRegistry((ClassLoader) receiver);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class WeakSelfComputer implements FieldValueTransformer {
        @Override
        public Object transform(Object receiver, Object originalValue) {
            if (RuntimeClassLoading.isSupported()) {
                assert receiver != null;
                /*
                 * Note: in the image heap, these are effectively strong references. We are still
                 * storing `WeakReference` to unify the logic that uses this field, at the cost of a
                 * couple extra objects.
                 */
                return new WeakReference<>(receiver, collectedLoaders);
            }
            return originalValue;
        }
    }
}
