/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.SubstrateConstructorAccessor;
import com.oracle.svm.core.util.DeferredKeyMap;
import com.oracle.svm.core.util.DynamicHubKey;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.java.LambdaUtils;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public class SerializationSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static SerializationSupport currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(SerializationSupport.class, false, true);
    }

    public static SerializationSupport[] layeredSingletons() {
        return MultiLayeredImageSingleton.getAllLayers(SerializationSupport.class);
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

    private DynamicHub stubConstructorClass;
    @Platforms(Platform.HOSTED_ONLY.class) //
    private DynamicHub serializedLambdaClass;

    public record HostedSerializationLookupKey(DynamicHubKey declaringClass, DynamicHubKey targetConstructorClass) {
    }

    public record SerializationLookupKey(int declaringClassId, int targetConstructorClassId) {
    }

    private final DeferredKeyMap<HostedSerializationLookupKey, SerializationLookupKey, Object> constructorAccessors;

    /**
     * The constructor accessors need to be rescanned manually because the
     * {@link SerializationSupport#constructorAccessors} map is only available after compilation.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private Consumer<Object> objectRescanner;

    public SerializationSupport() {
        constructorAccessors = new DeferredKeyMap<>(SerializationSupport::replaceSerializationLookupKey);
        classes = new DeferredKeyMap<>(DynamicHubKey::getTypeID);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setStubConstructor(DynamicHub stubConstructorClass) {
        VMError.guarantee(this.stubConstructorClass == null, "Cannot set stubConstructor again");
        this.stubConstructorClass = stubConstructorClass;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setSerializedLambdaClass(DynamicHub serializedLambdaClass) {
        VMError.guarantee(this.serializedLambdaClass == null, "Cannot set serializedLambdaClass again");
        this.serializedLambdaClass = serializedLambdaClass;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private DynamicHub getSerializedLambdaClass() {
        return serializedLambdaClass;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setObjectRescanner(Consumer<Object> objectRescanner) {
        VMError.guarantee(this.objectRescanner == null, "Cannot set objectRescanner again");
        this.objectRescanner = objectRescanner;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object addConstructorAccessor(DynamicHub declaringClass, DynamicHub targetConstructorClass, Object constructorAccessor) {
        VMError.guarantee(constructorAccessor instanceof SubstrateConstructorAccessor, "Not a SubstrateConstructorAccessor: %s", constructorAccessor);
        VMError.guarantee(!BuildPhaseProvider.isHostedUniverseBuilt(), "Called too early");
        HostedSerializationLookupKey key = new HostedSerializationLookupKey(new DynamicHubKey(declaringClass), new DynamicHubKey(targetConstructorClass));
        objectRescanner.accept(constructorAccessor);
        synchronized (constructorAccessors) {
            return constructorAccessors.putHostedIfAbsent(key, constructorAccessor);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public SerializationLookupKey getKeyFromConstructorAccessorClass(Class<?> constructorAccessorClass) {
        MapCursor<SerializationLookupKey, Object> cursor = constructorAccessors.getRuntimeEntries();
        while (cursor.advance()) {
            if (cursor.getValue().getClass().equals(constructorAccessorClass)) {
                return cursor.getKey();
            }
        }
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isGeneratedSerializationClassLoader(ClassLoader classLoader) {
        var constructorAccessorsCursor = constructorAccessors.getHostedEntries();
        while (constructorAccessorsCursor.advance()) {
            if (constructorAccessorsCursor.getValue().getClass().getClassLoader() == classLoader) {
                return true;
            }
        }
        return false;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public String getClassLoaderSerializationLookupKey(ClassLoader classLoader) {
        var constructorAccessorsCursor = constructorAccessors.getHostedEntries();
        while (constructorAccessorsCursor.advance()) {
            if (constructorAccessorsCursor.getValue().getClass().getClassLoader() == classLoader) {
                var key = constructorAccessorsCursor.getKey();
                return key.declaringClass() + " " + key.targetConstructorClass();
            }
        }
        throw VMError.shouldNotReachHere("No constructor accessor uses the class loader %s", classLoader);
    }

    /**
     * Maps that are conceptually indexed by {@link Class} need different keys before and after type
     * IDs are assigned. {@link DeferredKeyMap} keeps the hosted {@link DynamicHub} keys out of the
     * image heap and automatically replaces them with their stable type IDs before compilation.
     */
    private final DeferredKeyMap<DynamicHubKey, Integer, RuntimeDynamicAccessMetadata> classes;
    private final EconomicMap<String, RuntimeDynamicAccessMetadata> lambdaCapturingClasses = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerSerializationTargetClass(AccessCondition cnd, DynamicHub hub, boolean preserved) {
        VMError.guarantee(!BuildPhaseProvider.isHostedUniverseBuilt());
        synchronized (classes) {
            DynamicHubKey key = new DynamicHubKey(hub);
            RuntimeDynamicAccessMetadata current = classes.getHosted(key);
            boolean newPreserved = preserved || current != null && current.isPreserved();
            classes.putHosted(key, RuntimeDynamicAccessMetadata.addCondition(current, cnd, true).withPreserved(newPreserved));
        }
    }

    public void replaceHubKeyWithTypeID() {
        VMError.guarantee(!classes.isSealed() && !constructorAccessors.isSealed(), "The maps should only be replaced once");
        classes.seal();
        constructorAccessors.seal();
    }

    private static SerializationLookupKey replaceSerializationLookupKey(HostedSerializationLookupKey key) {
        return new SerializationLookupKey(key.declaringClass().getTypeID(), key.targetConstructorClass().getTypeID());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerLambdaCapturingClass(AccessCondition cnd, String lambdaCapturingClass) {
        synchronized (lambdaCapturingClasses) {
            lambdaCapturingClasses.put(lambdaCapturingClass, RuntimeDynamicAccessMetadata.addCondition(lambdaCapturingClasses.get(lambdaCapturingClass), cnd, false));
        }
        PredefinedClassesSupport.registerSerializableLambdasForCapturingClass(lambdaCapturingClass);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isLambdaCapturingClassRegistered(String lambdaCapturingClass) {
        return lambdaCapturingClasses.containsKey(lambdaCapturingClass);
    }

    public static Object getRuntimeSerializationConstructorAccessor(Class<?> serializationTargetClass, Class<?> targetConstructorClass) {
        SubstrateUtil.guaranteeRuntimeOnly();
        Class<?> declaringClass = serializationTargetClass;

        if (LambdaUtils.isLambdaClass(declaringClass)) {
            declaringClass = SerializedLambda.class;
        }

        DynamicHub declaringHub = SubstrateUtil.cast(declaringClass, DynamicHub.class);
        DynamicHub targetConstructorHub = SubstrateUtil.cast(targetConstructorClass, DynamicHub.class);
        if (MetadataTracer.enabled() && shouldTraceSerialization(declaringHub)) {
            MetadataTracer tracer = MetadataTracer.singleton();
            tracer.traceSerializationType(declaringClass);
            if (targetConstructorClass != declaringClass) {
                // Replay also needs the constructor declaring class.
                tracer.traceReflectionType(targetConstructorClass);
            }
        }
        for (var singleton : layeredSingletons()) {
            Object constructorAccessor = singleton.getSerializationConstructorAccessor0(declaringHub, targetConstructorHub, declaringClass.getModifiers());
            if (constructorAccessor != null) {
                return constructorAccessor;
            }
        }

        String targetConstructorClassName = targetConstructorClass.getName();
        MissingSerializationRegistrationUtils.reportSerialization(declaringClass,
                        "type '" + declaringClass.getTypeName() + "' with target constructor class '" + targetConstructorClassName + "'");
        return null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static Object getHostedSerializationConstructorAccessor(DynamicHub serializationTargetClass, DynamicHub targetConstructorClass) {
        SerializationSupport serializationSupport = currentLayer();
        DynamicHub declaringClass = serializationTargetClass;

        if (LambdaUtils.isLambdaClass(declaringClass.getHostedJavaClass())) {
            declaringClass = serializationSupport.getSerializedLambdaClass();
        }

        VMError.guarantee(BuildPhaseProvider.isHostedUniverseBuilt(), "Called too early, hosted universe was not built yet.");
        Object constructorAccessor = serializationSupport.getSerializationConstructorAccessor0(declaringClass, targetConstructorClass, declaringClass.getModifiers());
        if (constructorAccessor != null) {
            return constructorAccessor;
        }

        String targetConstructorClassName = targetConstructorClass.getName();
        MissingSerializationRegistrationUtils.reportSerialization(declaringClass.getHostedJavaClass(),
                        "type '" + declaringClass.getTypeName() + "' with target constructor class '" + targetConstructorClassName + "'");
        return null;
    }

    public Object getSerializationConstructorAccessor0(DynamicHub declaringHub, DynamicHub rawTargetConstructorHub, int modifiers) {
        VMError.guarantee(stubConstructorClass != null, "Called too early, no stub constructor yet.");
        DynamicHub targetConstructorHub = Modifier.isAbstract(modifiers) ? stubConstructorClass : rawTargetConstructorHub;
        return constructorAccessors.getRuntime(new SerializationLookupKey(declaringHub.getTypeID(), targetConstructorHub.getTypeID()));
    }

    public static boolean isRegisteredForSerialization(DynamicHub hub) {
        for (SerializationSupport singleton : SerializationSupport.layeredSingletons()) {
            if (singleton.isRegisteredForSerialization0(hub)) {
                return true;
            }
        }
        return false;
    }

    public boolean isRegisteredForSerialization0(DynamicHub dynamicHub) {
        SubstrateUtil.guaranteeRuntimeOnly();
        var conditionSet = classes.getRuntime(dynamicHub.getTypeID());
        return conditionSet != null && conditionSet.satisfied();
    }

    public static boolean shouldTraceSerialization(DynamicHub dynamicHub) {
        boolean metadataFound = false;
        for (SerializationSupport singleton : SerializationSupport.layeredSingletons()) {
            var conditionSet = singleton.classes.getRuntime(dynamicHub.getTypeID());
            if (conditionSet != null) {
                metadataFound = true;
                if (conditionSet.isPreserved()) {
                    return true;
                }
            }
        }
        return !metadataFound;
    }

    public static boolean isPreservedForSerialization(DynamicHub dynamicHub) {
        SubstrateUtil.guaranteeRuntimeOnly();
        for (SerializationSupport singleton : SerializationSupport.layeredSingletons()) {
            var conditionSet = singleton.classes.getRuntime(dynamicHub.getTypeID());
            if (conditionSet != null) {
                return conditionSet.isPreserved();
            }
        }
        return false;
    }
}
