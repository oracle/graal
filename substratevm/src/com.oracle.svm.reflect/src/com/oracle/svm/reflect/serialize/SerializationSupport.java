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
package com.oracle.svm.reflect.serialize;

// Checkstyle: allow reflection

import java.io.Externalizable;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.svm.core.jdk.Package_jdk_internal_reflect;
import com.oracle.svm.core.jdk.serialize.SerializationRegistry;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.SerializationChecksumCalculator;

public class SerializationSupport implements SerializationRegistry {

    class ChecksumCalculator extends SerializationChecksumCalculator.JavaCalculator {
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
                Method computeDefaultSUID = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "computeDefaultSUID", java.lang.Class.class);
                return (Long) computeDefaultSUID.invoke(null, clazz);
            } catch (ReflectionUtil.ReflectionUtilError | InvocationTargetException | IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        @Override
        protected boolean isClassAbstract(Class<?> clazz) {
            return Modifier.isAbstract(clazz.getModifiers());
        }
    }

    /**
     * Method MethodAccessorGenerator.generateSerializationConstructor dynamically defines a
     * SerializationConstructorAccessorImpl type class. The class has a newInstance method which
     * news the class specified by generateSerializationConstructor's first
     * parameter--declaringClass and then calls declaringClass' first non-serializable superclass.
     * The bytecode of the generated class like : <code>
     * jdk.internal.reflect.GeneratedSerializationConstructorAccessor2.newInstance(Unknown Source)
     * [bci: 0, intrinsic: false] 
     * 0: new #6 // declaringClass 
     * 3: dup 
     * 4: aload_1 
     * 5: ifnull 24 
     * 8: aload_1 
     * 9: arraylength 
     * 10: sipush 0
     *  ...
     * </code> The declaringClass could be an abstract class. At deserialization time,
     * SerializationConstructorAccessorImpl classes are generated for the target class and all of
     * its serializable super classes. The super classes could be abstract. So it is possible to
     * generate bytecode that new an abstract class. In JDK, the super class' generated newInstance
     * method shall never get invoked, so the "new abstract class" code won't cause any error. But
     * in Substrate VM, the generated class gets compiled at build time and the "new abstract class"
     * code causes compilation error.
     *
     * We introduce this StubForAbstractClass class to replace any abstract classes at method
     * generateSerializationConstructor's declaringClass parameter place. So there won't be "new
     * abstract class" bytecode anymore, and it's also safe for runtime as the corresponding
     * newInstance method is never actually called.
     */
    static class StubForAbstractClass implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private static Object stubAccessor = null;

    /**
     * Using a separated classloader for serialization checksum computation to avoid initializing
     * Classes that should be initialized at run time.
     */
    class SerializationChecksumClassLoader extends URLClassLoader {

        SerializationChecksumClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

    }

    private class CachedEntity {
        private final Object serializationConstructorAccessor;
        private final Long configuredChecksum;

        CachedEntity(Object accessor, Long checksum) {
            this.serializationConstructorAccessor = accessor;
            this.configuredChecksum = checksum;
        }
    }

    // Cached SerializationConstructorAccessors for runtime usage
    private Map<String, CachedEntity> cachedSerializationConstructorAccessors;
    private static final String MULTIPLE_CHECKSUMS = "MULTIPLE_CHECKSUM";
    private static final String CHECKSUM_VERIFY_FAIL = "CHECKSUM_VERIFY_FAIL";
    private SerializationChecksumClassLoader serializationChecksumClassLoader;
    private final ChecksumCalculator checksumCalculator;

    public SerializationSupport(ImageClassLoader imageClassLoader) {
        cachedSerializationConstructorAccessors = new ConcurrentHashMap<>();
        checksumCalculator = new ChecksumCalculator();
        URLClassLoader cl = (URLClassLoader) imageClassLoader.getClassLoader();
        serializationChecksumClassLoader = new SerializationChecksumClassLoader(cl.getURLs(), cl.getParent());
    }

    private static void reportError(FeatureImpl.BeforeAnalysisAccessImpl access, String msgKey, String exceptionsMsg) {
        // Checkstyle: stop
        String option = SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+");
        if (!NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            BigBang bb = access.getBigBang();
            bb.getUnsupportedFeatures().addMessage(msgKey, null,
                            exceptionsMsg + "\n" + "To allow continuing compilation with above unsupported features, set " + option);
        } else {
            System.out.println(exceptionsMsg);
            System.out.println("Compilation will continue because " + option +
                            " was set. But the program may behave unexpectedly at runtime.");
        }
        // Checkstyle: resume
    }

    @Platforms({Platform.HOSTED_ONLY.class})
    public Class<?> addSerializationConstructorAccessorClass(Class<?> serializationTargetClass, List<Long> configuredChecksums, FeatureAccess access) {
        if (serializationTargetClass.isArray() || Enum.class.isAssignableFrom(serializationTargetClass)) {
            return null;
        }
        // Don't generate SerializationConstructorAccessor class for Externalizable case
        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            try {
                Method getExternalizableConstructor = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getExternalizableConstructor", Class.class);
                Constructor<?> c = (Constructor<?>) getExternalizableConstructor.invoke(null, serializationTargetClass);
                return c.getDeclaringClass();
            } catch (Exception e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        /**
         * Using reflection to make sure code is compatible with both JDK 8 and above Reflectively
         * call method ReflectionFactory.newConstructorForSerialization(Class) to get the
         * SerializationConstructorAccessor instance.
         */
        Constructor<?> buildTimeConstructor;
        Class<?> buildTimeConsClass;
        long checksum = 0;
        Object constructorAccessor;
        String targetClassName = serializationTargetClass.getName();
        boolean isAbstract = Modifier.isAbstract(serializationTargetClass.getModifiers());
        Constructor<?> stubConstructor = null;
        try {
            Class<?> reflectionFactoryClass = access.findClassByName(Package_jdk_internal_reflect.getQualifiedName() + ".ReflectionFactory");
            Method getReflectionFactoryMethod = ReflectionUtil.lookupMethod(reflectionFactoryClass, "getReflectionFactory");
            Object reflFactoryInstance = getReflectionFactoryMethod.invoke(null);
            Method newConstructorForSerializationMethod = ReflectionUtil.lookupMethod(reflectionFactoryClass, "newConstructorForSerialization", Class.class);
            buildTimeConstructor = (Constructor<?>) newConstructorForSerializationMethod.invoke(reflFactoryInstance, serializationTargetClass);

            // Calculate GeneratedSerializationConstructor for StubForAbstractClass only once
            if (isAbstract && stubAccessor == null) {
                stubConstructor = (Constructor<?>) newConstructorForSerializationMethod.invoke(reflFactoryInstance, StubForAbstractClass.class);
            }
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }
        if (buildTimeConstructor == null) {
            return null;
        }
        buildTimeConsClass = buildTimeConstructor.getDeclaringClass();

        if (isAbstract && stubAccessor != null) {
            constructorAccessor = stubAccessor;
            targetClassName = StubForAbstractClass.class.getName();
        } else {
            // Prepare build time checksum and verify with configured checksum only for non-abstract
            // classes. Abstract class' checksum is always 0.
            if (!isAbstract) {
                checksum = getChecksum(serializationTargetClass, configuredChecksums, (FeatureImpl.BeforeAnalysisAccessImpl) access, buildTimeConsClass, targetClassName);
            }
            try {
                Method getConstructorAccessor = ReflectionUtil.lookupMethod(Constructor.class, "getConstructorAccessor");
                constructorAccessor = getConstructorAccessor.invoke(isAbstract ? stubConstructor : buildTimeConstructor);
                if (isAbstract) {
                    assert constructorAccessor != null;
                    stubAccessor = constructorAccessor;
                    targetClassName = StubForAbstractClass.class.getName();
                }
            } catch (Exception e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
        // Cache constructorAccessor
        CachedEntity exitingEntity = cachedSerializationConstructorAccessors.putIfAbsent(targetClassName,
                        new CachedEntity(constructorAccessor, checksum));
        if (exitingEntity != null && exitingEntity.configuredChecksum != checksum) {
            StringBuilder sb = new StringBuilder();
            sb.append("Suspicious multiple-classloader usage is detected from serialization configurations:\n");
            sb.append("Serialization target class (name=").append(targetClassName).append(", checksum=").append(checksum).append(")");
            sb.append(" is already registered with checksum ").append(exitingEntity.configuredChecksum);
            reportError((FeatureImpl.BeforeAnalysisAccessImpl) access, MULTIPLE_CHECKSUMS, sb.toString());
        }

        return buildTimeConsClass;
    }

    private long getChecksum(Class<?> serializationTargetClass, List<Long> configuredChecksums, FeatureImpl.BeforeAnalysisAccessImpl access, Class<?> buildTimeConsClass, String targetClassName) {
        // this class is getting from SerializationChecksumClassLoader classloader
        Class<?> checksumCalculationTargetClass;
        try {
            // Checkstyle: stop
            checksumCalculationTargetClass = Class.forName(serializationTargetClass.getName(), false, serializationChecksumClassLoader);
            // Checkstyle resume
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere(e);
        }
        long buildTimeChecksum = checksumCalculator.calculateChecksum(buildTimeConsClass.getName(), serializationTargetClass.getName(), checksumCalculationTargetClass);
        if (!configuredChecksums.isEmpty()) {
            /* If we have checksums, one of them has to match the buildTimeChecksum */
            if (!configuredChecksums.contains(buildTimeChecksum)) {
                StringBuilder sb = new StringBuilder();
                sb.append("\nBuild time serialization class checksum verify failure.")
                                .append(" The classes' hierarchy may have been changed from configuration collecting time to image build time:\n");
                sb.append(targetClassName).append(": configured checksums: ").append(configuredChecksums.stream().map(String::valueOf).collect(Collectors.joining(", "))).append("\n");
                sb.append(targetClassName).append(": build time checksum: ").append(buildTimeChecksum);
                reportError(access, CHECKSUM_VERIFY_FAIL, sb.toString());
            }
        }
        return buildTimeChecksum;
    }

    @Override
    public Object getSerializationConstructorAccessorClass(Class<?> serializationTargetClass, String targetConstructorClass) {
        boolean isAbstract = Modifier.isAbstract(serializationTargetClass.getModifiers());
        Class<?> actualSerializationTargetClass = isAbstract ? StubForAbstractClass.class : serializationTargetClass;
        String serializationTargetClassName = actualSerializationTargetClass.getName();
        CachedEntity ret = cachedSerializationConstructorAccessors.get(serializationTargetClassName);
        if (ret == null) {
            // Not support serializing Lambda yet
            if (serializationTargetClassName.contains("$$Lambda$")) {
                throw VMError.unsupportedFeature("Can't serialize " + serializationTargetClassName + ". Serializing Lambda class is not supported");
            } else {
                throw VMError.unsupportedFeature("SerializationConstructorAccessor class is not found for class :" + serializationTargetClassName +
                                ". Generating SerializationConstructorAccessor classes at runtime is not supported. ");
            }
        } else {
            Object accessor = ret.serializationConstructorAccessor;
            Long configuredChecksum = ret.configuredChecksum;
            long runtimeChecksum;

            if (isAbstract) {
                runtimeChecksum = 0;
            } else {
                runtimeChecksum = checksumCalculator.calculateChecksum(targetConstructorClass,
                                serializationTargetClassName, actualSerializationTargetClass);
            }
            // configuredChecksum could be null if it is not set in the configuration.
            if ((configuredChecksum != null && configuredChecksum.longValue() == runtimeChecksum) || configuredChecksum == null) {
                return accessor;
            } else {
                throw VMError.unimplemented("Serialization target class " + serializationTargetClassName + "'s hierarchy has been changed at run time. Configured checksum is " + configuredChecksum +
                                ", runtime checksum is " + runtimeChecksum);
            }
        }
    }
}
