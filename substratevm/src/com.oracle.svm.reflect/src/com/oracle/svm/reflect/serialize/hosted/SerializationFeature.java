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

import static com.oracle.svm.reflect.serialize.hosted.SerializationFeature.println;

import java.io.Externalizable;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.configure.SerializationConfigurationParser.SerializationParserFunction;
import com.oracle.svm.core.jdk.Package_jdk_internal_reflect;
import com.oracle.svm.core.jdk.serialize.SerializationRegistry;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.reflect.serialize.SerializationSupport;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.SerializationChecksumCalculator;

import jdk.vm.ci.meta.MetaUtil;

@AutomaticFeature
public class SerializationFeature implements Feature {
    private int loadedConfigurations;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        SerializationBuilder serializationBuilder = new SerializationBuilder(access);

        Map<Class<?>, Boolean> deniedClasses = new HashMap<>();
        SerializationConfigurationParser denyCollectorParser = new SerializationConfigurationParser((strTargetSerializationClass, checksums) -> {
            Class<?> serializationTargetClass = resolveClass(strTargetSerializationClass, access);
            if (serializationTargetClass != null) {
                deniedClasses.put(serializationTargetClass, true);
            }
        });
        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ConfigurationParserUtils.parseAndRegisterConfigurations(denyCollectorParser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationDenyConfigurationFiles, ConfigurationFiles.Options.SerializationDenyConfigurationResources,
                        ConfigurationFiles.SERIALIZATION_DENY_NAME);

        SerializationParserFunction serializationAdapter = (strTargetSerializationClass, checksums) -> {
            Class<?> serializationTargetClass = resolveClass(strTargetSerializationClass, access);
            UserError.guarantee(serializationTargetClass != null, "Cannot find serialization target class %s. The missing of this class can't be ignored even if -H:+AllowIncompleteClasspath is set." +
                            " Please make sure it is in the classpath", strTargetSerializationClass);
            if (Serializable.class.isAssignableFrom(serializationTargetClass)) {
                if (deniedClasses.containsKey(serializationTargetClass)) {
                    if (deniedClasses.get(serializationTargetClass)) {
                        deniedClasses.put(serializationTargetClass, false); /* Warn only once */
                        println("Warning: Serialization deny list contains " + serializationTargetClass.getName() + ". Image will not support serialization/deserialization of this class.");
                    }
                } else {
                    Class<?> targetConstructor = serializationBuilder.addConstructorAccessor(serializationTargetClass, checksums);
                    addReflections(serializationTargetClass, targetConstructor);
                }
            }
        };

        SerializationConfigurationParser parser = new SerializationConfigurationParser(serializationAdapter);
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFiles.SERIALIZATION_NAME);
    }

    public static void addReflections(Class<?> serializationTargetClass, Class<?> targetConstructorClass) {
        if (targetConstructorClass != null) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(targetConstructorClass));
        }

        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(serializationTargetClass, (Class<?>[]) null));
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
        int staticFinalMask = Modifier.STATIC | Modifier.FINAL;
        int privateStaticFinalMask = Modifier.PRIVATE | staticFinalMask;

        Set<String> serialPersistentFieldNames = new HashSet<>();
        try {
            /* FIXME serialPersistentFieldNames is write-only. What is the point of this code? */
            Field f = ReflectionUtil.lookupField(serializationTargetClass, "serialPersistentFields");
            if ((f.getModifiers() & privateStaticFinalMask) == privateStaticFinalMask) {
                ObjectStreamField[] serialPersistentFields = (ObjectStreamField[]) f.get(null);
                for (ObjectStreamField serialPersistentField : serialPersistentFields) {
                    serialPersistentFieldNames.add(serialPersistentField.getName());
                }
            }
        } catch (ReflectionUtil.ReflectionUtilError | IllegalAccessException e) {
            // No serialPersistentFields field or failed to get the field value, continue
        }

        for (Field f : serializationTargetClass.getDeclaredFields()) {
            int modifiers = f.getModifiers();
            boolean allowWrite = false;
            boolean allowUnsafeAccess = false;
            if ((modifiers & staticFinalMask) != staticFinalMask) {
                allowWrite = Modifier.isFinal(f.getModifiers());
                allowUnsafeAccess = !Modifier.isStatic(f.getModifiers());
            }
            RuntimeReflection.register(allowWrite, allowUnsafeAccess, f);
        }
    }

    private static Class<?> resolveClass(String typeName, FeatureAccess a) {
        String name = typeName;
        if (name.indexOf('[') != -1) {
            /* accept "int[][]", "java.lang.String[]" */
            name = MetaUtil.internalNameToJava(MetaUtil.toInternalName(name), true, true);
        }
        Class<?> ret = a.findClassByName(name);
        if (ret == null) {
            handleError("Could not resolve " + name + " for serialization configuration.");
        }
        return ret;
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(FallbackFeature.class)) {
            return;
        }
        FallbackFeature.FallbackImageRequest serializationFallback = ImageSingletons.lookup(FallbackFeature.class).serializationFallback;
        if (serializationFallback != null && loadedConfigurations == 0) {
            throw serializationFallback;
        }
    }

    private static void handleError(String message) {
        boolean allowIncompleteClasspath = NativeImageOptions.AllowIncompleteClasspath.getValue();
        if (allowIncompleteClasspath) {
            println("WARNING: " + message);
        } else {
            throw new JSONParserException(message + " To allow unresolvable reflection configuration, use option -H:+AllowIncompleteClasspath");
        }
    }

    static void println(String str) {
        // Checkstyle: stop
        System.out.println(str);
        // Checkstyle: resume
    }
}

final class SerializationBuilder {

    /**
     * Using a separated classloader for serialization checksum computation to avoid initializing
     * Classes that should be initialized at run time.
     */
    private static final class SerializationChecksumClassLoader extends URLClassLoader {
        private SerializationChecksumClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }
    }

    private static final class ChecksumCalculator extends SerializationChecksumCalculator.JavaCalculator {
        private final Method computeDefaultSUID;

        private ChecksumCalculator() {
            computeDefaultSUID = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "computeDefaultSUID", Class.class);
        }

        @Override
        protected String getClassName(Class<?> clazz) {
            return clazz.getName();
        }

        @Override
        protected Class<?> getSuperClass(Class<?> clazz) {
            return clazz.getSuperclass();
        }

        @Override
        protected Long calculateFromComputeDefaultSUID(Class<?> clazz) {
            try {
                return (Long) computeDefaultSUID.invoke(null, clazz);
            } catch (ReflectiveOperationException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        @Override
        protected boolean isClassAbstract(Class<?> clazz) {
            return Modifier.isAbstract(clazz.getModifiers());
        }
    }

    private final SerializationChecksumClassLoader serializationChecksumClassLoader;
    private final ChecksumCalculator checksumCalculator;

    private final FeatureImpl.BeforeAnalysisAccessImpl access;
    private final Object reflectionFactory;
    private final Method newConstructorForSerializationMethod;
    private final Method getConstructorAccessorMethod;
    private final Method getExternalizableConstructorMethod;
    private final Constructor<?> stubConstructor;

    private final SerializationSupport serializationSupport;

    SerializationBuilder(FeatureImpl.BeforeAnalysisAccessImpl access) {
        try {
            Class<?> reflectionFactoryClass = access.findClassByName(Package_jdk_internal_reflect.getQualifiedName() + ".ReflectionFactory");
            Method getReflectionFactoryMethod = ReflectionUtil.lookupMethod(reflectionFactoryClass, "getReflectionFactory");
            reflectionFactory = getReflectionFactoryMethod.invoke(null);
            newConstructorForSerializationMethod = ReflectionUtil.lookupMethod(reflectionFactoryClass, "newConstructorForSerialization", Class.class);
            getConstructorAccessorMethod = ReflectionUtil.lookupMethod(Constructor.class, "getConstructorAccessor");
            getExternalizableConstructorMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getExternalizableConstructor", Class.class);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
        stubConstructor = newConstructorForSerialization(SerializationSupport.StubForAbstractClass.class);
        this.access = access;

        URLClassLoader cl = (URLClassLoader) access.getImageClassLoader().getClassLoader();
        serializationChecksumClassLoader = new SerializationChecksumClassLoader(cl.getURLs(), cl.getParent());
        checksumCalculator = new ChecksumCalculator();

        serializationSupport = new SerializationSupport();
        ImageSingletons.add(SerializationRegistry.class, serializationSupport);
    }

    private Constructor<?> newConstructorForSerialization(Class<?> serializationTargetClass) {
        try {
            return (Constructor<?>) newConstructorForSerializationMethod.invoke(reflectionFactory, serializationTargetClass);
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

    Class<?> addConstructorAccessor(Class<?> serializationTargetClass, List<String> configuredChecksums) {
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
            targetConstructor = newConstructorForSerialization(serializationTargetClass);
            targetConstructorClass = targetConstructor.getDeclaringClass();
            verifyBuildTimeChecksum(serializationTargetClass, targetConstructorClass, configuredChecksums);
        }
        serializationSupport.addConstructorAccessor(serializationTargetClass, targetConstructorClass, getConstructorAccessor(targetConstructor));
        return targetConstructorClass;
    }

    private void verifyBuildTimeChecksum(Class<?> serializationTargetClass, Class<?> targetConstructorClass, List<String> configuredChecksums) {
        if (configuredChecksums.isEmpty()) {
            return;
        }
        try {
            String targetClassName = serializationTargetClass.getName();
            // Checkstyle: stop
            Class<?> checksumCalculationTargetClass = Class.forName(targetClassName, false, serializationChecksumClassLoader);
            // Checkstyle: resume
            String buildTimeChecksum = checksumCalculator.calculateChecksum(targetConstructorClass.getName(), targetClassName, checksumCalculationTargetClass);
            /* If we have checksums, one of them has to match the buildTimeChecksum */
            if (!configuredChecksums.contains(buildTimeChecksum)) {
                String msg = "\nBuild time serialization class checksum verify failure." +
                                " The classes' hierarchy may have been changed from configuration collecting time to image build time:\n" +
                                targetClassName + ": configured checksums: " + String.join(", ", configuredChecksums) + "\n" +
                                targetClassName + ": build time checksum: " + buildTimeChecksum;
                reportChecksumError(msg);
            }
        } catch (NoSuchAlgorithmException | ClassNotFoundException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private void reportChecksumError(String exceptionsMsg) {
        String option = SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+");
        if (!NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            access.getBigBang().getUnsupportedFeatures()
                            .addMessage("CHECKSUM_VERIFY_FAIL", null,
                                            exceptionsMsg + "\n" + "To allow continuing compilation with above unsupported features, set " + option);
        } else {
            println(exceptionsMsg);
            println("Compilation will continue because " + option + " was set. But the program may behave unexpectedly at runtime.");
        }
    }
}
