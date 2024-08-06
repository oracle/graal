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
package com.oracle.svm.core.reflect.serialize;

import static com.oracle.svm.core.SubstrateOptions.ThrowMissingRegistrationErrors;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.java.LambdaUtils;

public class SerializationSupport implements SerializationRegistry {

    public static SerializationSupport singleton() {
        return (SerializationSupport) ImageSingletons.lookup(SerializationRegistry.class);
    }

    /**
     * Method MethodAccessorGenerator.generateSerializationConstructor dynamically defines a
     * SerializationConstructorAccessorImpl type class. The class has a newInstance method which
     * news the class specified by generateSerializationConstructor's first parameter declaringClass
     * and then calls declaringClass' first non-serializable superclass. The bytecode of the
     * generated class looks like:
     *
     * <pre>
     * jdk.internal.reflect.GeneratedSerializationConstructorAccessor2.newInstance(Unknown Source)
     * [bci: 0, intrinsic: false]
     * 0: new #6 // declaringClass
     * 3: dup
     * 4: aload_1
     * 5: ifnull 24
     * 8: aload_1
     * 9: arraylength
     * 10: sipush 0
     * ...
     * </pre>
     *
     * The declaringClass could be an abstract class. At deserialization time,
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
    public static final class StubForAbstractClass implements Serializable {
        private static final long serialVersionUID = 1L;

        private StubForAbstractClass() {
        }
    }

    private final Constructor<?> stubConstructor;

    public static final class SerializationLookupKey {
        private final Class<?> declaringClass;
        private final Class<?> targetConstructorClass;

        private SerializationLookupKey(Class<?> declaringClass, Class<?> targetConstructorClass) {
            assert declaringClass != null && targetConstructorClass != null;
            this.declaringClass = declaringClass;
            this.targetConstructorClass = targetConstructorClass;
        }

        public Class<?> getDeclaringClass() {
            return declaringClass;
        }

        public Class<?> getTargetConstructorClass() {
            return targetConstructorClass;
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

    private final EconomicMap<SerializationLookupKey, Object> constructorAccessors;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SerializationSupport(Constructor<?> stubConstructor) {
        constructorAccessors = ImageHeapMap.create();
        this.stubConstructor = stubConstructor;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object addConstructorAccessor(Class<?> declaringClass, Class<?> targetConstructorClass, Object constructorAccessor) {
        SerializationLookupKey key = new SerializationLookupKey(declaringClass, targetConstructorClass);
        return constructorAccessors.putIfAbsent(key, constructorAccessor);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SerializationLookupKey getKeyFromConstructorAccessorClass(Class<?> constructorAccessorClass) {
        MapCursor<SerializationLookupKey, Object> cursor = constructorAccessors.getEntries();
        while (cursor.advance()) {
            if (cursor.getValue().getClass().equals(constructorAccessorClass)) {
                return cursor.getKey();
            }
        }
        return null;
    }

    private final EconomicMap<Class<?>, RuntimeConditionSet> classes = EconomicMap.create();
    private final EconomicMap<String, RuntimeConditionSet> lambdaCapturingClasses = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerSerializationTargetClass(ConfigurationCondition cnd, Class<?> serializationTargetClass) {
        synchronized (classes) {
            var previous = classes.putIfAbsent(serializationTargetClass, RuntimeConditionSet.createHosted(cnd));
            if (previous != null) {
                previous.addCondition(cnd);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerLambdaCapturingClass(ConfigurationCondition cnd, String lambdaCapturingClass) {
        synchronized (lambdaCapturingClasses) {
            var previousConditions = lambdaCapturingClasses.putIfAbsent(lambdaCapturingClass, RuntimeConditionSet.createHosted(cnd));
            if (previousConditions != null) {
                previousConditions.addCondition(cnd);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isLambdaCapturingClassRegistered(String lambdaCapturingClass) {
        return lambdaCapturingClasses.containsKey(lambdaCapturingClass);
    }

    @Override
    public Object getSerializationConstructorAccessor(Class<?> rawDeclaringClass, Class<?> rawTargetConstructorClass) {
        Class<?> declaringClass = rawDeclaringClass;

        if (LambdaUtils.isLambdaClass(declaringClass)) {
            declaringClass = SerializedLambda.class;
        }

        Class<?> targetConstructorClass = Modifier.isAbstract(declaringClass.getModifiers()) ? stubConstructor.getDeclaringClass() : rawTargetConstructorClass;
        Object constructorAccessor = constructorAccessors.get(new SerializationLookupKey(declaringClass, targetConstructorClass));

        if (constructorAccessor != null) {
            return constructorAccessor;
        } else {
            String targetConstructorClassName = targetConstructorClass.getName();
            if (ThrowMissingRegistrationErrors.hasBeenSet()) {
                MissingSerializationRegistrationUtils.missingSerializationRegistration(declaringClass,
                                "type " + declaringClass.getTypeName() + " with target constructor class: " + targetConstructorClassName);
            } else {
                throw VMError.unsupportedFeature("SerializationConstructorAccessor class not found for declaringClass: " + declaringClass.getName() +
                                " (targetConstructorClass: " + targetConstructorClassName + "). Usually adding " + declaringClass.getName() +
                                " to serialization-config.json fixes the problem.");
            }
            return null;
        }
    }

    @Override
    public boolean isRegisteredForSerialization(Class<?> clazz) {
        var conditionSet = classes.get(clazz);
        return conditionSet != null && conditionSet.satisfied();
    }
}
