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
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.Package_jdk_internal_reflect;
import com.oracle.svm.core.jdk.serialize.SerializationRegistry;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.SerializationChecksumCalculator;

public class SerializationSupport implements SerializationRegistry {

    static class ChecksumCalculator extends SerializationChecksumCalculator.JavaCalculator {
        private final Method computeDefaultSUID;

        ChecksumCalculator() {
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

    /**
     * Using a separated classloader for serialization checksum computation to avoid initializing
     * Classes that should be initialized at run time.
     */
    static class SerializationChecksumClassLoader extends URLClassLoader {

        SerializationChecksumClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

    }

    private static final class SerializationLookupKey {
        final Class<?> declaringClass;
        final Class<?> targetConstructorClass;

        private SerializationLookupKey(Class<?> declaringClass, Class<?> targetConstructorClass) {
            assert declaringClass != null && targetConstructorClass != null;
            this.declaringClass = declaringClass;
            this.targetConstructorClass = targetConstructorClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SerializationLookupKey that = (SerializationLookupKey) o;
            return declaringClass.equals(that.declaringClass) && targetConstructorClass.equals(that.targetConstructorClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(declaringClass, targetConstructorClass);
        }
    }

    // Cached SerializationConstructorAccessors for runtime usage
    private final Map<SerializationLookupKey, Object> cachedSerializationConstructorAccessors;

    private final SerializationChecksumClassLoader serializationChecksumClassLoader;
    private final ChecksumCalculator checksumCalculator;

    private final FeatureImpl.BeforeAnalysisAccessImpl access;
    private final Object reflectionFactory;
    private final Method newConstructorForSerializationMethod;
    private final Method getConstructorAccessorMethod;
    private final Method getExternalizableConstructorMethod;
    private final Constructor<?> stubConstructor;

    public SerializationSupport(FeatureImpl.BeforeAnalysisAccessImpl access) {
        cachedSerializationConstructorAccessors = new ConcurrentHashMap<>();
        checksumCalculator = new ChecksumCalculator();
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
        stubConstructor = newConstructorForSerialization(StubForAbstractClass.class);
        this.access = access;

        URLClassLoader cl = (URLClassLoader) access.getImageClassLoader().getClassLoader();
        serializationChecksumClassLoader = new SerializationChecksumClassLoader(cl.getURLs(), cl.getParent());
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
        } catch (Exception e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Constructor<?> getExternalizableConstructor(Class<?> serializationTargetClass) {
        try {
            return (Constructor<?>) getExternalizableConstructorMethod.invoke(null, serializationTargetClass);
        } catch (Exception e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Platforms({Platform.HOSTED_ONLY.class})
    public Class<?> addSerializationConstructorAccessorClass(Class<?> serializationTargetClass, List<String> configuredChecksums) {
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
        boolean abstractSerializationTarget = Modifier.isAbstract(serializationTargetClass.getModifiers());
        if (abstractSerializationTarget) {
            targetConstructor = stubConstructor;
        } else {
            targetConstructor = newConstructorForSerialization(serializationTargetClass);
        }
        if (targetConstructor == null) {
            return null;
        }
        Class<?> targetConstructorClass = targetConstructor.getDeclaringClass();
        Object constructorAccessor = getConstructorAccessor(targetConstructor);

        if (!configuredChecksums.isEmpty() && !abstractSerializationTarget) {
            try {
                String targetClassName = serializationTargetClass.getName();
                // Checkstyle: stop
                Class<?> checksumCalculationTargetClass = Class.forName(targetClassName, false, serializationChecksumClassLoader);
                // Checkstyle resume
                String buildTimeChecksum = checksumCalculator.calculateChecksum(targetConstructorClass.getName(), targetClassName, checksumCalculationTargetClass);
                /* If we have checksums, one of them has to match the buildTimeChecksum */
                if (!configuredChecksums.contains(buildTimeChecksum)) {
                    String sb = "\nBuild time serialization class checksum verify failure." +
                                    " The classes' hierarchy may have been changed from configuration collecting time to image build time:\n" +
                                    targetClassName + ": configured checksums: " + String.join(", ", configuredChecksums) + "\n" +
                                    targetClassName + ": build time checksum: " + buildTimeChecksum;
                    reportChecksumError(sb);
                }
            } catch (NoSuchAlgorithmException | ClassNotFoundException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        // Cache constructorAccessor
        SerializationLookupKey key = new SerializationLookupKey(serializationTargetClass, targetConstructorClass);
        cachedSerializationConstructorAccessors.putIfAbsent(key, constructorAccessor);
        return targetConstructorClass;
    }

    private void reportChecksumError(String exceptionsMsg) {
        // Checkstyle: stop
        String option = SubstrateOptionsParser.commandArgument(NativeImageOptions.ReportUnsupportedElementsAtRuntime, "+");
        if (!NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
            access.getBigBang().getUnsupportedFeatures()
                            .addMessage("CHECKSUM_VERIFY_FAIL", null,
                                            exceptionsMsg + "\n" + "To allow continuing compilation with above unsupported features, set " + option);
        } else {
            System.out.println(exceptionsMsg);
            System.out.println("Compilation will continue because " + option +
                            " was set. But the program may behave unexpectedly at runtime.");
        }
        // Checkstyle: resume
    }

    @Override
    public Object getSerializationConstructorAccessorClass(Class<?> declaringClass, Class<?> origTargetConstructorClass) {
        Class<?> targetConstructorClass = Modifier.isAbstract(declaringClass.getModifiers()) ? stubConstructor.getDeclaringClass() : origTargetConstructorClass;
        Object constructorAccessor = cachedSerializationConstructorAccessors.get(new SerializationLookupKey(declaringClass, targetConstructorClass));
        if (constructorAccessor != null) {
            return constructorAccessor;
        } else {
            // Not support serializing Lambda yet
            String targetConstructorClassName = origTargetConstructorClass.getName();
            if (targetConstructorClassName.contains("$$Lambda$")) {
                throw VMError.unsupportedFeature("Can't serialize " + targetConstructorClassName + ". Serializing Lambda class is not supported");
            } else {
                throw VMError.unsupportedFeature("SerializationConstructorAccessor class is not found for declaringClass:" + declaringClass.getName() +
                                ", targetConstructorClass: " + origTargetConstructorClass.getName() + ". Generating SerializationConstructorAccessor classes at runtime is not supported. ");
            }
        }
    }
}
