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

import static com.oracle.svm.reflect.serialize.hosted.SerializationFeature.println;
import static com.oracle.svm.reflect.serialize.hosted.SerializationFeature.warn;

import java.io.Externalizable;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.ConfigurationTypeResolver;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.reflect.hosted.ReflectionFeature;
import com.oracle.svm.reflect.serialize.SerializationRegistry;
import com.oracle.svm.reflect.serialize.SerializationSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.reflect.ReflectionFactory;

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
        ConfigurationTypeResolver typeResolver = new ConfigurationTypeResolver("serialization configuration", imageClassLoader, NativeImageOptions.AllowIncompleteClasspath.getValue());
        SerializationDenyRegistry serializationDenyRegistry = new SerializationDenyRegistry(typeResolver);
        serializationBuilder = new SerializationBuilder(serializationDenyRegistry, access, typeResolver);
        ImageSingletons.add(RuntimeSerializationSupport.class, serializationBuilder);

        SerializationConfigurationParser denyCollectorParser = new SerializationConfigurationParser(serializationDenyRegistry, ConfigurationFiles.Options.StrictConfiguration.getValue());
        ConfigurationParserUtils.parseAndRegisterConfigurations(denyCollectorParser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationDenyConfigurationFiles, ConfigurationFiles.Options.SerializationDenyConfigurationResources,
                        ConfigurationFile.SERIALIZATION_DENY.getFileName());

        SerializationConfigurationParser parser = new SerializationConfigurationParser(serializationBuilder, ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFile.SERIALIZATION.getFileName());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        serializationBuilder.flushConditionalConfiguration(access);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        serializationBuilder.flushConditionalConfiguration(access);
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
        System.out.println(str);
    }

    static void warn(String str) {
        // Checkstyle: stop
        System.err.println("Warning:" + str);
        // Checkstyle: resume
    }
}

final class SerializationDenyRegistry implements RuntimeSerializationSupport {

    private final Map<Class<?>, Boolean> deniedClasses = new HashMap<>();
    private final ConfigurationTypeResolver typeResolver;

    SerializationDenyRegistry(ConfigurationTypeResolver typeResolver) {
        this.typeResolver = typeResolver;
    }

    /**
     * No need to deny all associated classes, only the specified class itself is registered as
     * denied.
     */
    @Override
    public void registerIncludingAssociatedClasses(ConfigurationCondition condition, Class<?> clazz) {
        register(condition, clazz);
    }

    @Override
    public void register(ConfigurationCondition condition, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            registerWithTargetConstructorClass(condition, clazz, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, String className, String customTargetConstructorClassName) {
        registerWithTargetConstructorClass(condition, typeResolver.resolveType(className), null);
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, Class<?> clazz, Class<?> customTargetConstructorClazz) {
        if (clazz != null) {
            deniedClasses.put(clazz, true);
        }
    }

    public boolean isAllowed(Class<?> clazz) {
        boolean denied = deniedClasses.containsKey(clazz);
        if (denied && deniedClasses.get(clazz)) {
            deniedClasses.put(clazz, false); /* Warn only once */
            println("Warning: Serialization deny list contains " + clazz.getName() + ". Image will not support serialization/deserialization of this class.");
        }
        return !denied;
    }
}

final class SerializationBuilder extends ConditionalConfigurationRegistry implements RuntimeSerializationSupport {

    private static final Method getConstructorAccessorMethod = ReflectionUtil.lookupMethod(Constructor.class, "getConstructorAccessor");
    private static final Method getExternalizableConstructorMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getExternalizableConstructor", Class.class);

    private final Constructor<?> stubConstructor;
    private final Field descField;
    private final Method getDataLayoutMethod;

    private final SerializationSupport serializationSupport;
    private final SerializationDenyRegistry denyRegistry;
    private final ConfigurationTypeResolver typeResolver;
    private final FeatureImpl.DuringSetupAccessImpl access;
    private boolean sealed;

    SerializationBuilder(SerializationDenyRegistry serializationDenyRegistry, FeatureImpl.DuringSetupAccessImpl access, ConfigurationTypeResolver typeResolver) {
        this.access = access;
        Class<?> classDataSlotClazz = access.findClassByName("java.io.ObjectStreamClass$ClassDataSlot");
        descField = ReflectionUtil.lookupField(classDataSlotClazz, "desc");
        getDataLayoutMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getClassDataLayout");
        stubConstructor = newConstructorForSerialization(SerializationSupport.StubForAbstractClass.class, null);
        this.denyRegistry = serializationDenyRegistry;
        this.typeResolver = typeResolver;

        serializationSupport = new SerializationSupport(stubConstructor);
        ImageSingletons.add(SerializationRegistry.class, serializationSupport);
    }

    private void abortIfSealed() {
        UserError.guarantee(!sealed, "Too late to add classes for serialization. Registration must happen in a Feature before the analysis has finished.");
    }

    @Override
    public void registerIncludingAssociatedClasses(ConfigurationCondition condition, Class<?> clazz) {
        registerIncludingAssociatedClasses(condition, clazz, new HashSet<>());
    }

    private void registerIncludingAssociatedClasses(ConfigurationCondition condition, Class<?> clazz, Set<Class<?>> alreadyVisited) {
        if (alreadyVisited.contains(clazz)) {
            return;
        }
        alreadyVisited.add(clazz);
        String targetClassName = clazz.getName();
        // If the serialization target is primitive, it needs to get boxed, because the target is
        // always an Object.
        if (clazz.isPrimitive()) {
            Class<?> boxedType = JavaKind.fromJavaClass(clazz).toBoxedJavaClass();
            registerIncludingAssociatedClasses(condition, boxedType, alreadyVisited);
            return;
        } else if (!Serializable.class.isAssignableFrom(clazz)) {
            warn("Class " + targetClassName + " does not implement java.io.Serializable and was not registered for object serialization.\n");
            return;
        } else if (access.findSubclasses(clazz).size() > 1) {
            // The classes returned from access.findSubclasses API including the base class itself
            warn("Class " + targetClassName +
                            " has subclasses. No classes were registered for object serialization.\n");
            return;
        }
        try {
            clazz.getDeclaredMethod("writeObject", ObjectOutputStream.class);
            warn("Class " + targetClassName +
                            " implements its own writeObject method for object serialization. Any serialization types it uses need to be explicitly registered.\n");
            return;
        } catch (NoSuchMethodException e) {
            // Expected case. Do nothing
        }
        register(condition, clazz);

        if (clazz.isArray()) {
            registerIncludingAssociatedClasses(condition, clazz.getComponentType(), alreadyVisited);
            return;
        }
        ObjectStreamClass osc = ObjectStreamClass.lookup(clazz);
        try {
            for (Object o : (Object[]) getDataLayoutMethod.invoke(osc)) {
                ObjectStreamClass desc = (ObjectStreamClass) descField.get(o);
                if (!desc.equals(osc) && !desc.equals(clazz)) {
                    registerIncludingAssociatedClasses(condition, desc.forClass(), alreadyVisited);
                }
            }
        } catch (ReflectiveOperationException e) {
            VMError.shouldNotReachHere("Cannot register serialization classes due to", e);
        }

        for (ObjectStreamField field : osc.getFields()) {
            registerIncludingAssociatedClasses(condition, field.getType(), alreadyVisited);
        }
    }

    @Override
    public void register(ConfigurationCondition condition, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            registerWithTargetConstructorClass(condition, clazz, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, String targetClassName, String customTargetConstructorClassName) {
        abortIfSealed();

        Class<?> conditionClass = typeResolver.resolveType(condition.getTypeName());
        if (conditionClass == null) {
            return;
        }

        Class<?> serializationTargetClass = typeResolver.resolveType(targetClassName);
        UserError.guarantee(serializationTargetClass != null, "Cannot find serialization target class %s. The missing of this class can't be ignored even if --allow-incomplete-classpath is set." +
                        " Please make sure it is in the classpath", targetClassName);

        if (customTargetConstructorClassName != null) {
            Class<?> customTargetConstructorClass = typeResolver.resolveType(customTargetConstructorClassName);
            UserError.guarantee(customTargetConstructorClass != null,
                            "Cannot find targetConstructorClass %s. The missing of this class can't be ignored even if --allow-incomplete-classpath is set." +
                                            " Please make sure it is in the classpath",
                            customTargetConstructorClass);
            registerWithTargetConstructorClass(condition, serializationTargetClass, customTargetConstructorClass);
        } else {
            registerWithTargetConstructorClass(condition, serializationTargetClass, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, Class<?> serializationTargetClass, Class<?> customTargetConstructorClass) {
        abortIfSealed();
        if (!Serializable.class.isAssignableFrom(serializationTargetClass)) {
            println("Warning: Could not register " + serializationTargetClass.getName() + " for serialization as it does not implement Serializable.");
        } else if (denyRegistry.isAllowed(serializationTargetClass)) {
            if (customTargetConstructorClass != null) {
                UserError.guarantee(customTargetConstructorClass.isAssignableFrom(serializationTargetClass),
                                "The given targetConstructorClass %s is not a subclass of the serialization target class %s.",
                                customTargetConstructorClass, serializationTargetClass);
            }
            registerConditionalConfiguration(condition, () -> {
                Class<?> targetConstructor = addConstructorAccessor(serializationTargetClass, customTargetConstructorClass);
                addReflections(serializationTargetClass, targetConstructor);
            });
        }
    }

    public void afterAnalysis() {
        sealed = true;
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

    private static Constructor<?> newConstructorForSerialization(Class<?> serializationTargetClass, Constructor<?> customConstructorToCall) {
        if (customConstructorToCall == null) {
            return ReflectionFactory.getReflectionFactory().newConstructorForSerialization(serializationTargetClass);
        } else {
            return ReflectionFactory.getReflectionFactory().newConstructorForSerialization(serializationTargetClass, customConstructorToCall);
        }
    }

    private static Object getConstructorAccessor(Constructor<?> constructor) {
        try {
            return getConstructorAccessorMethod.invoke(constructor);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static Constructor<?> getExternalizableConstructor(Class<?> serializationTargetClass) {
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
