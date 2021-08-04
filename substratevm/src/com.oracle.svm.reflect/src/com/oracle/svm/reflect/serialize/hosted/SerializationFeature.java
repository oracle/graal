/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.reflect.serialize.hosted;

// Checkstyle: allow reflection

import java.io.Externalizable;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.reflect.serialize.SerializationRegistry;
import jdk.vm.ci.meta.MetaUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.jdk.Package_jdk_internal_reflect;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.reflect.hosted.ReflectionFeature;
import com.oracle.svm.reflect.serialize.SerializationSupport;
import com.oracle.svm.util.ReflectionUtil;

import static com.oracle.svm.reflect.serialize.hosted.SerializationFeature.println;

@AutomaticFeature
public class SerializationFeature implements Feature {
    private SerializationBuilder serializationBuilder;
    private int loadedConfigurations;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(ReflectionFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;

        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        SerializationTypeResolver typeResolver = new SerializationTypeResolver(imageClassLoader, NativeImageOptions.AllowIncompleteClasspath.getValue());
        SerializationDenyRegistry serializationDenyRegistry = new SerializationDenyRegistry(typeResolver);
        serializationBuilder = new SerializationBuilder(serializationDenyRegistry, access, typeResolver);
        ImageSingletons.add(RuntimeSerializationSupport.class, serializationBuilder);

        SerializationConfigurationParser denyCollectorParser = new SerializationConfigurationParser(serializationDenyRegistry);
        ConfigurationParserUtils.parseAndRegisterConfigurations(denyCollectorParser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationDenyConfigurationFiles, ConfigurationFiles.Options.SerializationDenyConfigurationResources,
                        ConfigurationFile.SERIALIZATION_DENY.getFileName());

        SerializationConfigurationParser parser = new SerializationConfigurationParser(serializationBuilder);
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFile.SERIALIZATION.getFileName());
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        serializationBuilder.duringAnalysis(access);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        serializationBuilder.afterAnalysis();
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (ImageSingletons.contains(FallbackFeature.class)) {
            FallbackFeature.FallbackImageRequest serializationFallback = ImageSingletons.lookup(FallbackFeature.class).serializationFallback;
            if (serializationFallback != null && loadedConfigurations == 0) {
                throw serializationFallback;
            }
        }
    }

    static void println(String str) {
        // Checkstyle: stop
        System.out.println(str);
        // Checkstyle: resume
    }
}

final class SerializationTypeResolver {

    private final ImageClassLoader classLoader;
    private final boolean allowIncompleteClasspath;

    SerializationTypeResolver(ImageClassLoader classLoader, boolean allowIncompleteClasspath) {
        this.classLoader = classLoader;
        this.allowIncompleteClasspath = allowIncompleteClasspath;
    }

    public Class<?> resolveType(String typeName) {
        String name = typeName;
        if (name.indexOf('[') != -1) {
            /* accept "int[][]", "java.lang.String[]" */
            name = MetaUtil.internalNameToJava(MetaUtil.toInternalName(name), true, true);
        }
        TypeResult<Class<?>> typeResult = classLoader.findClass(name);
        if (!typeResult.isPresent()) {
            handleError("Could not resolve " + name + " for serialization configuration.");
        }
        return typeResult.get();
    }

    private void handleError(String message) {
        if (allowIncompleteClasspath) {
            println("WARNING: " + message);
        } else {
            throw new JSONParserException(message + " To allow unresolvable reflection configuration, use option -H:+AllowIncompleteClasspath");
        }
    }
}

final class SerializationDenyRegistry implements RuntimeSerializationSupport {

    private final Map<Class<?>, Boolean> deniedClasses = new HashMap<>();
    private final SerializationTypeResolver typeResolver;

    SerializationDenyRegistry(SerializationTypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    @Override
    public void register(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            registerWithTargetConstructorClass(clazz, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(String className, String customTargetConstructorClassName) {
        registerWithTargetConstructorClass(typeResolver.resolveType(className), null);
    }

    @Override
    public void registerWithTargetConstructorClass(Class<?> clazz, Class<?> customTargetConstructorClazz) {
        if (clazz != null) {
            deniedClasses.put(clazz, true);
        }
    }

    public boolean isAllowed(Class<?> clazz) {
        boolean denied = deniedClasses.containsKey(clazz);
        if (denied && deniedClasses.get(clazz)) {
            deniedClasses.put(clazz, false); /* Warn only once */
            println("WARNING: Serialization deny list contains " + clazz.getName() + ". Image will not support serialization/deserialization of this class.");
        }
        return !denied;
    }
}

final class SerializationBuilder implements RuntimeSerializationSupport {

    private static final class ClassEntry {
        private final Class<?> clazz;
        private final Class<?> customTargetConstructorClazz;

        private ClassEntry(Class<?> clazz, Class<?> customTargetConstructorClazz) {
            this.clazz = clazz;
            this.customTargetConstructorClazz = customTargetConstructorClazz;
        }
    }

    private final Object reflectionFactory;
    private final Method newConstructorForSerializationMethod1;
    private final Method newConstructorForSerializationMethod2;
    private final Method getConstructorAccessorMethod;
    private final Method getExternalizableConstructorMethod;
    private final Constructor<?> stubConstructor;

    private final SerializationSupport serializationSupport;
    private final SerializationDenyRegistry denyRegistry;
    private final SerializationTypeResolver typeResolver;
    private final Set<ClassEntry> newClasses;

    private boolean sealed;

    SerializationBuilder(SerializationDenyRegistry serializationDenyRegistry, FeatureImpl.DuringSetupAccessImpl access, SerializationTypeResolver typeResolver) {
        try {
            Class<?> reflectionFactoryClass = access.findClassByName(Package_jdk_internal_reflect.getQualifiedName() + ".ReflectionFactory");
            Method getReflectionFactoryMethod = ReflectionUtil.lookupMethod(reflectionFactoryClass, "getReflectionFactory");
            reflectionFactory = getReflectionFactoryMethod.invoke(null);
            newConstructorForSerializationMethod1 = ReflectionUtil.lookupMethod(reflectionFactoryClass, "newConstructorForSerialization", Class.class);
            newConstructorForSerializationMethod2 = ReflectionUtil.lookupMethod(reflectionFactoryClass, "newConstructorForSerialization", Class.class, Constructor.class);
            getConstructorAccessorMethod = ReflectionUtil.lookupMethod(Constructor.class, "getConstructorAccessor");
            getExternalizableConstructorMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getExternalizableConstructor", Class.class);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
        stubConstructor = newConstructorForSerialization(SerializationSupport.StubForAbstractClass.class, null);
        this.denyRegistry = serializationDenyRegistry;
        this.typeResolver = typeResolver;
        newClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

        serializationSupport = new SerializationSupport(stubConstructor);
        ImageSingletons.add(SerializationRegistry.class, serializationSupport);
    }

    private void abortIfSealed() {
        UserError.guarantee(!sealed, "Too late to add classes for serialization. Registration must happen in a Feature before the analysis has finished.");
    }

    @Override
    public void register(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            registerWithTargetConstructorClass(clazz, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(String targetClassName, String customTargetConstructorClassName) {
        abortIfSealed();
        Class<?> serializationTargetClass = typeResolver.resolveType(targetClassName);
        UserError.guarantee(serializationTargetClass != null, "Cannot find serialization target class %s. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set." +
                        " Please make sure it is in the classpath", targetClassName);
        if (customTargetConstructorClassName != null) {
            Class<?> customTargetConstructorClass = typeResolver.resolveType(customTargetConstructorClassName);
            UserError.guarantee(customTargetConstructorClass != null,
                            "Cannot find targetConstructorClass %s. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set." +
                                            " Please make sure it is in the classpath",
                            customTargetConstructorClass);
            registerWithTargetConstructorClass(serializationTargetClass, customTargetConstructorClass);
        } else {
            registerWithTargetConstructorClass(serializationTargetClass, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(Class<?> serializationTargetClass, Class<?> customTargetConstructorClass) {
        abortIfSealed();
        if (!Serializable.class.isAssignableFrom(serializationTargetClass)) {
            println("WARNING: Could not register " + serializationTargetClass.getName() + " for serialization as it does not implement Serializable.");
        } else if (denyRegistry.isAllowed(serializationTargetClass)) {
            if (customTargetConstructorClass != null) {
                UserError.guarantee(customTargetConstructorClass.isAssignableFrom(serializationTargetClass),
                                "The given targetConstructorClass %s is not a subclass of the serialization target class %s.",
                                customTargetConstructorClass, serializationTargetClass);
            }
            ClassEntry entry = new ClassEntry(serializationTargetClass, customTargetConstructorClass);
            newClasses.add(entry);
        }
    }

    public void duringAnalysis(Feature.DuringAnalysisAccess a) {
        if (newClasses.isEmpty()) {
            return;
        }
        for (ClassEntry entry : newClasses) {
            Class<?> targetConstructor = addConstructorAccessor(entry.clazz, entry.customTargetConstructorClazz);
            addReflections(entry.clazz, targetConstructor);
        }
        newClasses.clear();

        a.requireAnalysisIteration();
    }

    public void afterAnalysis() {
        sealed = true;
        if (!newClasses.isEmpty()) {
            abortIfSealed();
        }
    }

    private static void addReflections(Class<?> serializationTargetClass, Class<?> targetConstructorClass) {
        if (targetConstructorClass != null) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(targetConstructorClass));
        }

        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(serializationTargetClass, (Class<?>[]) null));
        }

        RecordSupport recordSupport = RecordSupport.singleton();
        if (recordSupport.isRecord(serializationTargetClass)) {
            /* Serialization for records uses the canonical record constructor directly. */
            RuntimeReflection.register(recordSupport.getCanonicalRecordConstructor(serializationTargetClass));
            /*
             * Serialization for records invokes Class.getRecordComponents(). Registering all record
             * component accessor methods for reflection ensures that the record components are
             * available at run time.
             */
            RuntimeReflection.register(recordSupport.getRecordComponentAccessorMethods(serializationTargetClass));
        }

        RuntimeReflection.register(serializationTargetClass);
        /*
         * ObjectStreamClass.computeDefaultSUID is always called at runtime to verify serialization
         * class consistency, so need to register all constructors, methods and fields/
         */
        RuntimeReflection.register(serializationTargetClass.getDeclaredConstructors());
        registerMethods(serializationTargetClass);
        registerFields(serializationTargetClass);
    }

    private static void registerMethods(Class<?> serializationTargetClass) {
        RuntimeReflection.register(serializationTargetClass.getDeclaredMethods());
        // computeDefaultSUID will be reflectively called at runtime to verify class consistency
        Method computeDefaultSUID = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "computeDefaultSUID", Class.class);
        RuntimeReflection.register(computeDefaultSUID);
    }

    private static void registerFields(Class<?> serializationTargetClass) {
        RuntimeReflection.register(serializationTargetClass.getDeclaredFields());
    }

    private Constructor<?> newConstructorForSerialization(Class<?> serializationTargetClass, Constructor<?> customConstructorToCall) {
        try {
            if (customConstructorToCall == null) {
                return (Constructor<?>) newConstructorForSerializationMethod1.invoke(reflectionFactory, serializationTargetClass);
            } else {
                return (Constructor<?>) newConstructorForSerializationMethod2.invoke(reflectionFactory, serializationTargetClass, customConstructorToCall);
            }
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object getConstructorAccessor(Constructor<?> constructor) {
        try {
            return getConstructorAccessorMethod.invoke(constructor);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Constructor<?> getExternalizableConstructor(Class<?> serializationTargetClass) {
        try {
            return (Constructor<?>) getExternalizableConstructorMethod.invoke(null, serializationTargetClass);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    Class<?> addConstructorAccessor(Class<?> serializationTargetClass, Class<?> customTargetConstructorClass) {
        if (serializationTargetClass.isArray() || Enum.class.isAssignableFrom(serializationTargetClass)) {
            return null;
        }

        // Don't generate SerializationConstructorAccessor class for Externalizable case
        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            try {
                Constructor<?> externalizableConstructor = getExternalizableConstructor(serializationTargetClass);
                return externalizableConstructor.getDeclaringClass();
            } catch (Exception e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        /*
         * Using reflection to make sure code is compatible with both JDK 8 and above. Reflectively
         * call method ReflectionFactory.newConstructorForSerialization(Class) to get the
         * SerializationConstructorAccessor instance.
         */
        Constructor<?> targetConstructor;
        Class<?> targetConstructorClass;
        if (Modifier.isAbstract(serializationTargetClass.getModifiers())) {
            targetConstructor = stubConstructor;
            targetConstructorClass = targetConstructor.getDeclaringClass();
        } else {
            if (customTargetConstructorClass == serializationTargetClass) {
                /* No custom constructor needed. Simply use existing no-arg constructor. */
                return customTargetConstructorClass;
            }
            Constructor<?> customConstructorToCall = null;
            if (customTargetConstructorClass != null) {
                try {
                    customConstructorToCall = customTargetConstructorClass.getDeclaredConstructor();
                } catch (NoSuchMethodException ex) {
                    UserError.abort("The given targetConstructorClass %s does not declare a parameterless constructor.",
                                    customTargetConstructorClass.getTypeName());
                }
            }
            targetConstructor = newConstructorForSerialization(serializationTargetClass, customConstructorToCall);
            targetConstructorClass = targetConstructor.getDeclaringClass();
        }
        serializationSupport.addConstructorAccessor(serializationTargetClass, targetConstructorClass, getConstructorAccessor(targetConstructor));
        return targetConstructorClass;
    }
}
