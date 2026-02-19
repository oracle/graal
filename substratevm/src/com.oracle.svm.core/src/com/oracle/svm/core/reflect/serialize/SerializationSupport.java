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

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.reflect.SubstrateConstructorAccessor;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
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
    private DynamicHub serializedLambdaClass;

    /**
     * The ids are either a {@link DynamicHubKey} or {@code DynamicHub.typeID}.
     */
    public record SerializationLookupKey(Object declaringClassId, Object targetConstructorClassId) {
    }

    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl", availability = AfterCompilation.class) //
    private final EconomicMap<SerializationLookupKey, Object> constructorAccessors;

    /**
     * The constructor accessors need to be rescanned manually because the
     * {@link SerializationSupport#constructorAccessors} map is only available after compilation.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private Consumer<Object> scanObject;

    public SerializationSupport() {
        constructorAccessors = EconomicMap.create();
    }

    public void setStubConstructor(DynamicHub stubConstructorClass) {
        VMError.guarantee(this.stubConstructorClass == null, "Cannot reset stubConstructor");
        this.stubConstructorClass = stubConstructorClass;
    }

    public void setSerializedLambdaClass(DynamicHub serializedLambdaClass) {
        this.serializedLambdaClass = serializedLambdaClass;
    }

    private DynamicHub getSerializedLambdaClass() {
        return serializedLambdaClass;
    }

    public void setScanObject(Consumer<Object> scanObject) {
        this.scanObject = scanObject;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object addConstructorAccessor(DynamicHub declaringClass, DynamicHub targetConstructorClass, Object constructorAccessor) {
        VMError.guarantee(constructorAccessor instanceof SubstrateConstructorAccessor, "Not a SubstrateConstructorAccessor: %s", constructorAccessor);
        SerializationLookupKey key = BuildPhaseProvider.isHostedUniverseBuilt() ? new SerializationLookupKey(declaringClass.getTypeID(), targetConstructorClass.getTypeID())
                        : new SerializationLookupKey(new DynamicHubKey(declaringClass), new DynamicHubKey(targetConstructorClass));
        scanObject.accept(constructorAccessor);
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isGeneratedSerializationClassLoader(ClassLoader classLoader) {
        var constructorAccessorsCursor = constructorAccessors.getEntries();
        while (constructorAccessorsCursor.advance()) {
            if (constructorAccessorsCursor.getValue().getClass().getClassLoader() == classLoader) {
                return true;
            }
        }
        return false;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public String getClassLoaderSerializationLookupKey(ClassLoader classLoader) {
        var constructorAccessorsCursor = constructorAccessors.getEntries();
        while (constructorAccessorsCursor.advance()) {
            if (constructorAccessorsCursor.getValue().getClass().getClassLoader() == classLoader) {
                var key = constructorAccessorsCursor.getKey();
                return key.declaringClassId + " " + key.targetConstructorClassId;
            }
        }
        throw VMError.shouldNotReachHere("No constructor accessor uses the class loader %s", classLoader);
    }

    /**
     * This class is used as key in maps that use {@link Class} as key at runtime in layered images,
     * because the hash code of {@link Class} objects cannot be injected in extension layers and is
     * thus inconsistent across layers. The state of those maps is then incorrect at run time. The
     * {@link DynamicHub} cannot be used directly either as its hash code at run time is the one of
     * the {@link Class} object.
     * <p>
     * Temporary key for maps ideally indexed by their {@link Class} or {@link DynamicHub}. At
     * runtime, these maps should be indexed by {@link DynamicHub#getTypeID}
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public record DynamicHubKey(DynamicHub hub) {
        public int getTypeID() {
            return hub.getTypeID();
        }
    }

    @UnknownObjectField(fullyQualifiedTypes = "org.graalvm.collections.EconomicMapImpl", availability = AfterCompilation.class) //
    private final EconomicMap<Object /* DynamicHubKey or DynamicHub.typeID */, RuntimeDynamicAccessMetadata> classes = EconomicMap.create();
    private final EconomicMap<String, RuntimeDynamicAccessMetadata> lambdaCapturingClasses = EconomicMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerSerializationTargetClass(AccessCondition cnd, DynamicHub hub, boolean preserved) {
        synchronized (classes) {
            var previous = classes.putIfAbsent(BuildPhaseProvider.isHostedUniverseBuilt() ? hub.getTypeID() : new DynamicHubKey(hub), RuntimeDynamicAccessMetadata.createHosted(cnd, preserved));
            if (previous != null) {
                previous.addCondition(cnd);
                if (!preserved) {
                    previous.setNotPreserved();
                }
            }
        }
    }

    public void replaceHubKeyWithTypeID() {
        replaceHubKeyWithTypeID(classes, key -> key instanceof DynamicHubKey, SerializationSupport::getTypeID);
        replaceHubKeyWithTypeID(constructorAccessors, SerializationSupport::shouldReplaceSerializationLookupKey, SerializationSupport::replaceSerializationLookupKey);
    }

    private static <T, U> void replaceHubKeyWithTypeID(EconomicMap<T, U> map, Predicate<T> condition, Function<T, T> converter) {
        EconomicMap<T, U> newEntries = EconomicMap.create();
        var cursor = map.getEntries();
        while (cursor.advance()) {
            T key = cursor.getKey();
            if (condition.test(key)) {
                newEntries.put(converter.apply(key), cursor.getValue());
                cursor.remove();
            }
        }
        map.putAll(newEntries);
    }

    private static boolean shouldReplaceSerializationLookupKey(SerializationLookupKey key) {
        return key.declaringClassId() instanceof DynamicHubKey && key.targetConstructorClassId() instanceof DynamicHubKey;
    }

    private static SerializationLookupKey replaceSerializationLookupKey(SerializationLookupKey key) {
        return new SerializationLookupKey(getTypeID(key.declaringClassId()), getTypeID(key.targetConstructorClassId()));
    }

    private static int getTypeID(Object classId) {
        return ((DynamicHubKey) classId).getTypeID();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerLambdaCapturingClass(AccessCondition cnd, String lambdaCapturingClass) {
        synchronized (lambdaCapturingClasses) {
            var previousConditions = lambdaCapturingClasses.putIfAbsent(lambdaCapturingClass, RuntimeDynamicAccessMetadata.createHosted(cnd, false));
            if (previousConditions != null) {
                previousConditions.addCondition(cnd);
            }
        }
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

        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceSerializationType(declaringClass);
        }
        for (var singleton : layeredSingletons()) {
            DynamicHub declaringHub = SubstrateUtil.cast(declaringClass, DynamicHub.class);
            DynamicHub targetConstructorHub = SubstrateUtil.cast(targetConstructorClass, DynamicHub.class);
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
        return constructorAccessors.get(new SerializationLookupKey(declaringHub.getTypeID(), targetConstructorHub.getTypeID()));
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
        var conditionSet = classes.get(dynamicHub.getTypeID());
        return conditionSet != null && conditionSet.satisfied();
    }

    public static boolean isPreservedForSerialization(DynamicHub dynamicHub) {
        for (SerializationSupport singleton : SerializationSupport.layeredSingletons()) {
            var conditionSet = singleton.classes.get(dynamicHub.getTypeID());
            if (conditionSet != null) {
                return conditionSet.isPreserved();
            }
        }
        return false;
    }
}
