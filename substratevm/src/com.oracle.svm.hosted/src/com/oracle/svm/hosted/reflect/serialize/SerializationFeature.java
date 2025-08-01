/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect.serialize;

import static com.oracle.svm.hosted.lambda.LambdaParser.createMethodGraph;
import static com.oracle.svm.hosted.lambda.LambdaParser.getLambdaClassFromConstantNode;

import java.io.Externalizable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.SerializationConfigurationParser;
import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.SubstrateConstructorAccessor;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.reflect.target.ReflectionSubstitutionSupport;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.ConfigurationTypeResolver;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.lambda.LambdaParser;
import com.oracle.svm.hosted.reflect.NativeImageConditionResolver;
import com.oracle.svm.hosted.reflect.RecordUtils;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.hosted.reflect.proxy.DynamicProxyFeature;
import com.oracle.svm.hosted.reflect.proxy.ProxyRegistry;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.internal.access.JavaLangReflectAccess;
import jdk.internal.reflect.ConstructorAccessor;
import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
public class SerializationFeature implements InternalFeature {
    final Set<Class<?>> capturingClasses = ConcurrentHashMap.newKeySet();
    private SerializationBuilder serializationBuilder;
    private SerializationDenyRegistry serializationDenyRegistry;
    private int loadedConfigurations;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ReflectionFeature.class, DynamicProxyFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        FeatureImpl.AfterRegistrationAccessImpl access = (FeatureImpl.AfterRegistrationAccessImpl) a;
        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ConfigurationTypeResolver typeResolver = new ConfigurationTypeResolver("serialization configuration", imageClassLoader);
        serializationDenyRegistry = new SerializationDenyRegistry(typeResolver);
        serializationBuilder = new SerializationBuilder(serializationDenyRegistry, access, typeResolver, ImageSingletons.lookup(ProxyRegistry.class));
        /*
         * The serialization builder registration has to happen after registration so the
         * ReflectionFeature can access it when creating parsers during setup.
         */
        ImageSingletons.add(RuntimeSerializationSupport.class, serializationBuilder);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ConfigurationConditionResolver<ConfigurationCondition> conditionResolver = new NativeImageConditionResolver(imageClassLoader, ClassInitializationSupport.singleton());
        EnumSet<ConfigurationParserOption> parserOptions = ConfigurationFiles.Options.getConfigurationParserOptions();
        SerializationConfigurationParser<ConfigurationCondition> parser = SerializationConfigurationParser.create(true, conditionResolver, serializationBuilder, parserOptions);
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurationsFromCombinedFile(parser, imageClassLoader, "serialization");

        SerializationConfigurationParser<ConfigurationCondition> denyCollectorParser = SerializationConfigurationParser.create(false, conditionResolver, serializationDenyRegistry, parserOptions);
        ConfigurationParserUtils.parseAndRegisterConfigurations(denyCollectorParser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationDenyConfigurationFiles, ConfigurationFiles.Options.SerializationDenyConfigurationResources,
                        ConfigurationFile.SERIALIZATION_DENY.getFileName());

        SerializationConfigurationParser<ConfigurationCondition> legacyParser = SerializationConfigurationParser.create(false, conditionResolver, serializationBuilder, parserOptions);
        loadedConfigurations += ConfigurationParserUtils.parseAndRegisterConfigurations(legacyParser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFile.SERIALIZATION.getFileName());

    }

    private static void registerLambdasFromConstantNodesInGraph(StructuredGraph graph, SerializationBuilder serializationBuilder) {
        NodeIterable<ConstantNode> constantNodes = ConstantNode.getConstantNodes(graph);

        for (ConstantNode cNode : constantNodes) {
            Class<?> lambdaClass = getLambdaClassFromConstantNode(cNode);

            if (lambdaClass != null && Serializable.class.isAssignableFrom(lambdaClass)) {
                RuntimeReflection.register(ReflectionUtil.lookupMethod(lambdaClass, "writeReplace"));
                SerializationBuilder.registerSerializationUIDElements(lambdaClass, false);
                serializationBuilder.serializationSupport.registerSerializationTargetClass(ConfigurationCondition.alwaysTrue(), serializationBuilder.getHostVM().dynamicHub(lambdaClass));
            }
        }
    }

    @SuppressWarnings("try")
    private static void registerLambdasFromMethod(ResolvedJavaMethod method, SerializationBuilder serializationBuilder, OptionValues options) {
        StructuredGraph graph = createMethodGraph(method, options);
        registerLambdasFromConstantNodesInGraph(graph, serializationBuilder);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        serializationBuilder.beforeAnalysis(access);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl impl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        OptionValues options = impl.getBigBang().getOptions();

        /*
         * In order to serialize lambda classes we need to register proper methods for reflection.
         * We register all the lambdas from capturing classes written in the serialization
         * configuration file for serialization. In order to find all the lambdas from a class, we
         * parse all the methods of the given class and find all the lambdas in them.
         */
        MetaAccessProvider metaAccess = GraalAccess.getOriginalProviders().getMetaAccess();
        capturingClasses.parallelStream()
                        .map(metaAccess::lookupJavaType)
                        .flatMap(LambdaParser::allExecutablesDeclaredInClass)
                        .filter(m -> m.getCode() != null)
                        .forEach(m -> registerLambdasFromMethod(m, serializationBuilder, options));

        capturingClasses.clear();
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
        serializationBuilder.serializationSupport.replaceHubKeyWithTypeID();
    }

    public static Object getConstructorAccessor(Constructor<?> constructor) {
        return SerializationBuilder.getConstructorAccessor(constructor);
    }
}

final class SerializationDenyRegistry implements RuntimeSerializationSupport<ConfigurationCondition> {

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
    public void register(ConfigurationCondition condition, Class<?> clazz) {
        if (clazz != null) {
            deniedClasses.put(clazz, true);
        }
    }

    @Override
    public void register(ConfigurationCondition condition, String className) {
        this.register(condition, typeResolver.resolveType(className));
    }

    @Override
    public void registerLambdaCapturingClass(ConfigurationCondition condition, String lambdaCapturingClassName) {
        Class<?> lambdaCapturingClass = typeResolver.resolveType(lambdaCapturingClassName);
        if (lambdaCapturingClass != null) {
            deniedClasses.put(lambdaCapturingClass, true);
        }
    }

    @Override
    public void registerProxyClass(ConfigurationCondition condition, List<String> implementedInterfaces) {
    }

    public boolean isAllowed(Class<?> clazz) {
        boolean denied = deniedClasses.containsKey(clazz);
        if (denied && deniedClasses.get(clazz)) {
            deniedClasses.put(clazz, false); /* Warn only once */
            LogUtils.warning("Serialization deny list contains %s. Image will not support serialization/deserialization of this class.", clazz.getName());
        }
        return !denied;
    }
}

final class SerializationBuilder extends ConditionalConfigurationRegistry implements RuntimeSerializationSupport<ConfigurationCondition> {

    private static final Method getConstructorAccessorMethod = ReflectionUtil.lookupMethod(Constructor.class, "getConstructorAccessor");
    private static final Method getExternalizableConstructorMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getExternalizableConstructor", Class.class);

    private Constructor<?> stubConstructor;
    private final Field descField;
    private final Method getDataLayoutMethod;

    final SerializationSupport serializationSupport;
    private final SerializationDenyRegistry denyRegistry;
    private final ConfigurationTypeResolver typeResolver;
    private final FeatureImpl.AfterRegistrationAccessImpl access;
    private final Method disableSerialConstructorChecks;
    private final Method superHasAccessibleConstructor;
    private final Method packageEquals;
    private final ProxyRegistry proxyRegistry;
    private List<Runnable> pendingConstructorRegistrations;

    SerializationBuilder(SerializationDenyRegistry serializationDenyRegistry, FeatureImpl.AfterRegistrationAccessImpl access, ConfigurationTypeResolver typeResolver, ProxyRegistry proxyRegistry) {
        this.access = access;
        Class<?> classDataSlotClazz = access.findClassByName("java.io.ObjectStreamClass$ClassDataSlot");
        this.descField = ReflectionUtil.lookupField(classDataSlotClazz, "desc");
        this.getDataLayoutMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getClassDataLayout");
        this.disableSerialConstructorChecks = ReflectionUtil.lookupMethod(true, ReflectionFactory.class, "disableSerialConstructorChecks");
        this.superHasAccessibleConstructor = ReflectionUtil.lookupMethod(ReflectionFactory.class, "superHasAccessibleConstructor", Class.class);
        this.packageEquals = ReflectionUtil.lookupMethod(ReflectionFactory.class, "packageEquals", Class.class, Class.class);
        this.pendingConstructorRegistrations = new ArrayList<>();

        this.denyRegistry = serializationDenyRegistry;
        this.typeResolver = typeResolver;
        this.proxyRegistry = proxyRegistry;

        this.serializationSupport = new SerializationSupport();
        ImageSingletons.add(SerializationSupport.class, serializationSupport);
    }

    @Override
    public void registerIncludingAssociatedClasses(ConfigurationCondition condition, Class<?> clazz) {
        abortIfSealed();
        Objects.requireNonNull(clazz, () -> nullErrorMessage("class", "serialization"));
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
            return;
        } else if (access.findSubclasses(clazz).size() > 1) {
            // The classes returned from access.findSubclasses API including the base class itself
            LogUtils.warning("Class %s has subclasses. No classes were registered for object serialization.", targetClassName);
            return;
        }
        try {
            clazz.getDeclaredMethod("writeObject", ObjectOutputStream.class);
            LogUtils.warning("Class %s implements its own writeObject method for object serialization. Any serialization types it uses need to be explicitly registered.", targetClassName);
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
                if (!desc.equals(osc)) {
                    registerIncludingAssociatedClasses(condition, desc.forClass(), alreadyVisited);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere("Cannot register serialization classes due to", e);
        }

        for (ObjectStreamField field : osc.getFields()) {
            registerIncludingAssociatedClasses(condition, field.getType(), alreadyVisited);
        }
    }

    @Override
    public void registerLambdaCapturingClass(ConfigurationCondition condition, String lambdaCapturingClassName) {
        abortIfSealed();
        Objects.requireNonNull(lambdaCapturingClassName, () -> nullErrorMessage("lambda capturing class", "serialization"));
        Class<?> lambdaCapturingClass = typeResolver.resolveType(lambdaCapturingClassName);
        if (lambdaCapturingClass == null || lambdaCapturingClass.isPrimitive() || lambdaCapturingClass.isArray()) {
            return;
        }

        if (ReflectionUtil.lookupMethod(true, lambdaCapturingClass, "$deserializeLambda$", SerializedLambda.class) == null) {
            LogUtils.warning("Could not register %s for lambda serialization as it does not capture any serializable lambda.", lambdaCapturingClass);
            return;
        }

        registerConditionalConfiguration(condition, (cnd) -> {
            ImageSingletons.lookup(SerializationFeature.class).capturingClasses.add(lambdaCapturingClass);
            RuntimeReflection.register(lambdaCapturingClass);
            RuntimeReflection.register(ReflectionUtil.lookupMethod(lambdaCapturingClass, "$deserializeLambda$", SerializedLambda.class));
            SerializationSupport.currentLayer().registerLambdaCapturingClass(cnd, lambdaCapturingClassName);
        });
    }

    @Override
    public void registerProxyClass(ConfigurationCondition condition, List<String> implementedInterfaces) {
        abortIfSealed();
        registerConditionalConfiguration(condition, (cnd) -> {
            Class<?> proxyClass = proxyRegistry.createProxyClassForSerialization(implementedInterfaces);
            register(cnd, proxyClass);
        });
    }

    @Override
    public void register(ConfigurationCondition condition, String targetClassName) {
        abortIfSealed();
        Class<?> serializationTargetClass = typeResolver.resolveType(targetClassName);
        /* With invalid streams we have to register the class for lookup */
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerClassLookup(condition, targetClassName);
        if (serializationTargetClass == null) {
            return;
        }
        register(condition, serializationTargetClass);
    }

    @Override
    public void register(ConfigurationCondition condition, Class<?> serializationTargetClass) {
        abortIfSealed();
        Objects.requireNonNull(serializationTargetClass, () -> nullErrorMessage("class", "serialization"));
        registerConditionalConfiguration(condition, (cnd) -> {
            /*
             * Register class for reflection as it is needed when the class-value itself is
             * serialized.
             */
            ImageSingletons.lookup(RuntimeReflectionSupport.class).register(condition, serializationTargetClass);

            if (!Serializable.class.isAssignableFrom(serializationTargetClass)) {
                return;
            }
            /*
             * Making this class reachable as it will end up in the image heap without the analysis
             * knowing.
             */
            RuntimeReflection.register(java.io.ObjectOutputStream.class);

            if (denyRegistry.isAllowed(serializationTargetClass)) {
                addOrQueueConstructorAccessors(cnd, serializationTargetClass, getHostVM().dynamicHub(serializationTargetClass));

                Class<?> superclass = serializationTargetClass.getSuperclass();
                if (superclass != null) {
                    ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllDeclaredConstructorsQuery(ConfigurationCondition.alwaysTrue(), true, superclass);
                    ImageSingletons.lookup(RuntimeReflectionSupport.class).registerMethodLookup(ConfigurationCondition.alwaysTrue(), superclass, "writeReplace");
                    ImageSingletons.lookup(RuntimeReflectionSupport.class).registerMethodLookup(ConfigurationCondition.alwaysTrue(), superclass, "readResolve");
                }

                registerForSerialization(cnd, serializationTargetClass);
                registerForDeserialization(cnd, serializationTargetClass);

            }
        });
    }

    private void addOrQueueConstructorAccessors(ConfigurationCondition cnd, Class<?> serializationTargetClass, DynamicHub hub) {
        if (pendingConstructorRegistrations != null) {
            // cannot yet create constructor accessor -> add to pending
            pendingConstructorRegistrations.add(() -> registerConstructorAccessors(cnd, serializationTargetClass, hub));
        } else {
            // can already run the registration
            registerConstructorAccessors(cnd, serializationTargetClass, hub);
        }
    }

    private void registerConstructorAccessors(ConfigurationCondition cnd, Class<?> serializationTargetClass, DynamicHub hub) {
        serializationSupport.registerSerializationTargetClass(cnd, hub);
        registerConstructorAccessor(cnd, serializationTargetClass, null);
        for (Class<?> superclass = serializationTargetClass; superclass != null; superclass = superclass.getSuperclass()) {
            registerConstructorAccessor(cnd, serializationTargetClass, superclass);
        }
    }

    private void registerConstructorAccessor(ConfigurationCondition cnd, Class<?> serializationTargetClass, Class<?> targetConstructorClass) {
        Optional.ofNullable(addConstructorAccessor(serializationTargetClass, targetConstructorClass))
                        .map(ReflectionUtil::lookupConstructor)
                        .ifPresent(methods -> ImageSingletons.lookup(RuntimeReflectionSupport.class).register(cnd, false, methods));
    }

    void beforeAnalysis(Feature.BeforeAnalysisAccess beforeAnalysisAccess) {
        setAnalysisAccess(beforeAnalysisAccess);
        stubConstructor = newConstructorForSerialization(SerializationSupport.StubForAbstractClass.class, null);
        pendingConstructorRegistrations.forEach(Runnable::run);
        pendingConstructorRegistrations = null;
        serializationSupport.setStubConstructor(stubConstructor);
    }

    private static void registerQueriesForInheritableMethod(Class<?> clazz, String methodName, Class<?>... args) {
        Class<?> iter = clazz;
        while (iter != null) {
            RuntimeReflection.registerMethodLookup(iter, methodName, args);
            Method method = ReflectionUtil.lookupMethod(true, clazz, methodName, args);
            if (method != null) {
                RuntimeReflection.register(method);
                break;
            }
            iter = iter.getSuperclass();
        }
    }

    private static void registerMethod(ConfigurationCondition cnd, Class<?> clazz, String methodName, Class<?>... args) {
        Method method = ReflectionUtil.lookupMethod(true, clazz, methodName, args);
        if (method != null) {
            ImageSingletons.lookup(RuntimeReflectionSupport.class).register(cnd, false, method);
        } else {
            RuntimeReflection.registerMethodLookup(clazz, methodName, args);
        }
    }

    private void registerForSerialization(ConfigurationCondition cnd, Class<?> serializationTargetClass) {

        if (Serializable.class.isAssignableFrom(serializationTargetClass)) {
            /*
             * ObjectStreamClass.computeDefaultSUID is always called at runtime to verify
             * serialization class consistency, so need to register all constructors, methods and
             * fields.
             */
            registerSerializationUIDElements(serializationTargetClass, true); // if MRE

            /*
             * Required by jdk.internal.reflect.ReflectionFactory.newConstructorForSerialization
             */
            Class<?> initCl = serializationTargetClass;
            boolean initClValid = true;
            while (Serializable.class.isAssignableFrom(initCl)) {
                Class<?> prev = initCl;
                RuntimeReflection.registerAllDeclaredConstructors(initCl);
                if ((initCl = initCl.getSuperclass()) == null || (!disableSerialConstructorChecks() &&
                                !prev.isArray() && !superHasAccessibleConstructor(prev))) {
                    initClValid = false;
                    break;
                }
            }

            if (initClValid) {
                RuntimeReflection.registerAllDeclaredConstructors(initCl);
            }

            Class<?> iter = serializationTargetClass;
            while (iter != null) {
                RuntimeReflection.registerAllDeclaredFields(iter);
                try {
                    Arrays.stream(iter.getDeclaredFields())
                                    .map(Field::getType).forEach(type -> {
                                        RuntimeReflection.registerAllDeclaredMethods(type);
                                        RuntimeReflection.registerAllDeclaredFields(type);
                                        RuntimeReflection.registerAllDeclaredConstructors(type);
                                    });
                } catch (LinkageError l) {
                    /* Handled with registration above */
                }
                iter = iter.getSuperclass();
            }
        }

        registerQueriesForInheritableMethod(serializationTargetClass, "writeReplace");
        registerQueriesForInheritableMethod(serializationTargetClass, "readResolve");
        registerMethod(cnd, serializationTargetClass, "writeObject", ObjectOutputStream.class);
        registerMethod(cnd, serializationTargetClass, "readObjectNoData");
        registerMethod(cnd, serializationTargetClass, "readObject", ObjectInputStream.class);
    }

    @SuppressWarnings("unused")
    static void registerSerializationUIDElements(Class<?> serializationTargetClass, boolean fullyRegister) {
        RuntimeReflection.registerAllDeclaredConstructors(serializationTargetClass);
        RuntimeReflection.registerAllDeclaredMethods(serializationTargetClass);
        RuntimeReflection.registerAllDeclaredFields(serializationTargetClass);
        if (fullyRegister) {
            try {
                /* This is here a legacy that we can't remove as it is a breaking change */
                RuntimeReflection.register(serializationTargetClass.getDeclaredConstructors());
                RuntimeReflection.register(serializationTargetClass.getDeclaredMethods());
                RuntimeReflection.register(serializationTargetClass.getDeclaredFields());
            } catch (LinkageError e) {
                /* Handled by registrations above */
            }
        }
        RuntimeReflection.registerFieldLookup(serializationTargetClass, "serialPersistentFields");
    }

    public void afterAnalysis() {
        sealed();
    }

    private static void registerForDeserialization(ConfigurationCondition cnd, Class<?> serializationTargetClass) {
        ImageSingletons.lookup(RuntimeReflectionSupport.class).register(cnd, serializationTargetClass);

        if (serializationTargetClass.isRecord()) {
            /*
             * Serialization for records invokes Class.getRecordComponents(). Registering all record
             * component accessor methods for reflection ensures that the record components are
             * available at run time.
             */
            ImageSingletons.lookup(RuntimeReflectionSupport.class).registerAllRecordComponentsQuery(cnd, serializationTargetClass);
            try {
                /* Serialization for records uses the canonical record constructor directly. */
                Executable[] methods = new Executable[]{RecordUtils.getCanonicalRecordConstructor(serializationTargetClass)};
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(cnd, false, methods);
                Executable[] methods1 = RecordUtils.getRecordComponentAccessorMethods(serializationTargetClass);
                ImageSingletons.lookup(RuntimeReflectionSupport.class).register(cnd, false, methods1);
            } catch (LinkageError le) {
                /*
                 * Handled by the record component registration above.
                 */
            }
        } else if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            RuntimeReflection.registerConstructorLookup(serializationTargetClass);
        }

        registerMethod(cnd, serializationTargetClass, "readObject", ObjectInputStream.class);
        registerMethod(cnd, serializationTargetClass, "readResolve");
    }

    private Constructor<?> newConstructorForSerialization(Class<?> serializationTargetClass, Constructor<?> customConstructorToCall) {
        Constructor<?> constructorToCall;
        if (customConstructorToCall == null) {
            constructorToCall = getConstructorForSerialization(serializationTargetClass);
        } else {
            constructorToCall = customConstructorToCall;
        }
        if (constructorToCall == null) {
            return null;
        }
        ConstructorAccessor acc = getConstructorAccessor(serializationTargetClass, constructorToCall);
        JavaLangReflectAccess langReflectAccess = ReflectionUtil.readField(ReflectionFactory.class, "langReflectAccess", ReflectionFactory.getReflectionFactory());
        Method newConstructorWithAccessor = ReflectionUtil.lookupMethod(JavaLangReflectAccess.class, "newConstructorWithAccessor", Constructor.class, ConstructorAccessor.class);
        return ReflectionUtil.invokeMethod(newConstructorWithAccessor, langReflectAccess, constructorToCall, acc);
    }

    private static ConstructorAccessor getConstructorAccessor(Class<?> serializationTargetClass, Constructor<?> constructorToCall) {
        return (SubstrateConstructorAccessor) ReflectionSubstitutionSupport.singleton().getOrCreateConstructorAccessor(serializationTargetClass, constructorToCall);
    }

    /**
     * Returns a constructor that allocates an instance of cl and that then initializes the instance
     * by calling the no-arg constructor of its first non-serializable superclass. This is specified
     * in the Serialization Specification, section 3.1, in step 11 of the deserialization process.
     * If cl is not serializable, returns cl's no-arg constructor. If no accessible constructor is
     * found, or if the class hierarchy is somehow malformed (e.g., a serializable class has no
     * superclass), null is returned.
     *
     * @param cl the class for which a constructor is to be found
     * @return the generated constructor, or null if none is available
     */
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+22/src/java.base/share/classes/jdk/internal/reflect/ReflectionFactory.java#L311-L332")
    private Constructor<?> getConstructorForSerialization(Class<?> cl) {
        Class<?> initCl = cl;
        while (Serializable.class.isAssignableFrom(initCl)) {
            Class<?> prev = initCl;
            if ((initCl = initCl.getSuperclass()) == null || (!disableSerialConstructorChecks() &&
                            !superHasAccessibleConstructor(prev))) {
                return null;
            }
        }
        Constructor<?> constructorToCall;
        try {
            constructorToCall = initCl.getDeclaredConstructor();
            int mods = constructorToCall.getModifiers();
            if ((mods & Modifier.PRIVATE) != 0 ||
                            ((mods & (Modifier.PUBLIC | Modifier.PROTECTED)) == 0 &&
                                            !packageEquals(cl, initCl))) {
                return null;
            }
        } catch (NoSuchMethodException ex) {
            return null;
        }
        return constructorToCall;
    }

    private boolean superHasAccessibleConstructor(Class<?> prev) {
        try {
            return ReflectionUtil.invokeMethod(superHasAccessibleConstructor, ReflectionFactory.getReflectionFactory(), prev);
        } catch (LinkageError le) {
            return false;
        }
    }

    private boolean disableSerialConstructorChecks() {
        if (disableSerialConstructorChecks == null) {
            return false;
        }
        return ReflectionUtil.invokeMethod(disableSerialConstructorChecks, null);
    }

    private boolean packageEquals(Class<?> cl1, Class<?> cl2) {
        return ReflectionUtil.invokeMethod(packageEquals, null, cl1, cl2);
    }

    static Object getConstructorAccessor(Constructor<?> constructor) {
        return ReflectionUtil.invokeMethod(getConstructorAccessorMethod, constructor);
    }

    private static Constructor<?> getExternalizableConstructor(Class<?> serializationTargetClass) {
        return ReflectionUtil.invokeMethod(getExternalizableConstructorMethod, null, serializationTargetClass);
    }

    private Class<?> addConstructorAccessor(Class<?> serializationTargetClass, Class<?> customTargetConstructorClass) {
        // Don't generate SerializationConstructorAccessor class for Externalizable case
        if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            try {
                Constructor<?> externalizableConstructor = getExternalizableConstructor(serializationTargetClass);

                if (externalizableConstructor == null) {
                    externalizableConstructor = getExternalizableConstructor(Object.class);
                }

                return externalizableConstructor.getDeclaringClass();
            } catch (Exception e) {
                throw VMError.shouldNotReachHere(e);
            }
        }

        Constructor<?> targetConstructor;
        if (Modifier.isAbstract(serializationTargetClass.getModifiers())) {
            VMError.guarantee(stubConstructor != null, "stubConstructor is null, calling this too early");
            targetConstructor = stubConstructor;
        } else {
            Constructor<?> customConstructorToCall = null;
            if (customTargetConstructorClass != null) {
                customConstructorToCall = ReflectionUtil.lookupConstructor(true, customTargetConstructorClass);
                if (customConstructorToCall == null) {
                    /* No suitable constructor, no need to register */
                    return null;
                }
                if (customTargetConstructorClass == serializationTargetClass) {
                    /* No custom constructor needed. Simply use existing no-arg constructor. */
                    return customTargetConstructorClass;
                }
            }
            targetConstructor = newConstructorForSerialization(serializationTargetClass, customConstructorToCall);

            if (targetConstructor == null) {
                targetConstructor = newConstructorForSerialization(Object.class, customConstructorToCall);
            }
        }

        Class<?> targetConstructorClass = targetConstructor.getDeclaringClass();
        serializationSupport.addConstructorAccessor(serializationTargetClass, targetConstructorClass, getConstructorAccessor(targetConstructor));
        return targetConstructorClass;
    }
}
