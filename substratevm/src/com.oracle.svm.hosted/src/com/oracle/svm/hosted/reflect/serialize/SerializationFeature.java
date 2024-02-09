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

import java.io.Externalizable;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.configure.ConfigurationConditionResolver;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.reflect.serialize.SerializationRegistry;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.ConfigurationTypeResolver;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.reflect.NativeImageConditionResolver;
import com.oracle.svm.hosted.reflect.RecordUtils;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.hosted.reflect.proxy.DynamicProxyFeature;
import com.oracle.svm.hosted.reflect.proxy.ProxyRegistry;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.replacements.MethodHandlePlugin;
import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
public class SerializationFeature implements InternalFeature {
    final Set<Class<?>> capturingClasses = ConcurrentHashMap.newKeySet();
    private SerializationBuilder serializationBuilder;
    private int loadedConfigurations;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ReflectionFeature.class, DynamicProxyFeature.class);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        ImageClassLoader imageClassLoader = access.getImageClassLoader();
        ConfigurationConditionResolver<ConfigurationCondition> conditionResolver = new NativeImageConditionResolver(imageClassLoader, ClassInitializationSupport.singleton());
        ConfigurationTypeResolver typeResolver = new ConfigurationTypeResolver("serialization configuration", imageClassLoader);
        SerializationDenyRegistry serializationDenyRegistry = new SerializationDenyRegistry(typeResolver);
        serializationBuilder = new SerializationBuilder(serializationDenyRegistry, access, typeResolver, ImageSingletons.lookup(ProxyRegistry.class));
        ImageSingletons.add(RuntimeSerializationSupport.class, serializationBuilder);
        SerializationConfigurationParser<ConfigurationCondition> denyCollectorParser = new SerializationConfigurationParser<>(conditionResolver, serializationDenyRegistry,
                        ConfigurationFiles.Options.StrictConfiguration.getValue());

        ConfigurationParserUtils.parseAndRegisterConfigurations(denyCollectorParser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationDenyConfigurationFiles, ConfigurationFiles.Options.SerializationDenyConfigurationResources,
                        ConfigurationFile.SERIALIZATION_DENY.getFileName());

        SerializationConfigurationParser<ConfigurationCondition> parser = new SerializationConfigurationParser<>(conditionResolver, serializationBuilder,
                        ConfigurationFiles.Options.StrictConfiguration.getValue());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, imageClassLoader, "serialization",
                        ConfigurationFiles.Options.SerializationConfigurationFiles, ConfigurationFiles.Options.SerializationConfigurationResources,
                        ConfigurationFile.SERIALIZATION.getFileName());

    }

    private static GraphBuilderConfiguration buildLambdaParserConfig() {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        plugins.setClassInitializationPlugin(new NoClassInitializationPlugin());
        plugins.prependNodePlugin(new MethodHandlePlugin(GraalAccess.getOriginalProviders().getConstantReflection().getMethodHandleAccess(), false));
        return GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
    }

    @SuppressWarnings("try")
    private static StructuredGraph createMethodGraph(ResolvedJavaMethod method, GraphBuilderPhase lambdaParserPhase, OptionValues options) {
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getName());
        DebugContext debug = new DebugContext.Builder(options, new GraalDebugHandlersFactory(GraalAccess.getOriginalSnippetReflection())).description(description).build();

        HighTierContext context = new HighTierContext(GraalAccess.getOriginalProviders(), null, OptimisticOptimizations.NONE);
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug)
                        .method(method)
                        .recordInlinedMethods(false)
                        .build();
        try (DebugContext.Scope ignored = debug.scope("ParsingToMaterializeLambdas")) {
            lambdaParserPhase.apply(graph, context);
        } catch (Throwable e) {
            throw debug.handle(e);
        }
        return graph;
    }

    private static Class<?> getLambdaClassFromMemberField(Constant constant) {
        ResolvedJavaType constantType = GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType((JavaConstant) constant);

        if (constantType == null) {
            return null;
        }

        ResolvedJavaField[] fields = constantType.getInstanceFields(true);
        ResolvedJavaField targetField = null;
        for (ResolvedJavaField field : fields) {
            if (field.getName().equals("member")) {
                targetField = field;
                break;
            }
        }

        if (targetField == null) {
            return null;
        }

        JavaConstant fieldValue = GraalAccess.getOriginalProviders().getConstantReflection().readFieldValue(targetField, (JavaConstant) constant);
        Member memberField = GraalAccess.getOriginalProviders().getSnippetReflection().asObject(Member.class, fieldValue);
        return memberField.getDeclaringClass();
    }

    private static Class<?> getLambdaClassFromConstantNode(ConstantNode constantNode) {
        Constant constant = constantNode.getValue();
        Class<?> lambdaClass = getLambdaClassFromMemberField(constant);

        if (lambdaClass == null) {
            return null;
        }

        return LambdaUtils.isLambdaClass(lambdaClass) ? lambdaClass : null;
    }

    private static void registerLambdasFromConstantNodesInGraph(StructuredGraph graph, SerializationBuilder serializationBuilder) {
        NodeIterable<ConstantNode> constantNodes = ConstantNode.getConstantNodes(graph);

        for (ConstantNode cNode : constantNodes) {
            Class<?> lambdaClass = getLambdaClassFromConstantNode(cNode);

            if (lambdaClass != null && Serializable.class.isAssignableFrom(lambdaClass)) {
                RuntimeReflection.register(ReflectionUtil.lookupMethod(lambdaClass, "writeReplace"));
                SerializationBuilder.registerSerializationUIDElements(lambdaClass, false);
                serializationBuilder.serializationSupport.registerSerializationTargetClass(lambdaClass);
            }
        }
    }

    static class LambdaGraphBuilderPhase extends GraphBuilderPhase {
        LambdaGraphBuilderPhase(GraphBuilderConfiguration config) {
            super(config);
        }

        @Override
        public GraphBuilderPhase copyWithConfig(GraphBuilderConfiguration config) {
            return new LambdaGraphBuilderPhase(config);
        }

        static class LambdaBytecodeParser extends BytecodeParser {
            protected LambdaBytecodeParser(Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
                super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
            }
        }

        @Override
        protected Instance createInstance(CoreProviders providers, GraphBuilderConfiguration instanceGBConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            return new Instance(providers, instanceGBConfig, optimisticOpts, initialIntrinsicContext) {
                @Override
                protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
                    return new LambdaBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
                }
            };
        }
    }

    @SuppressWarnings("try")
    private static void registerLambdasFromMethod(ResolvedJavaMethod method, SerializationBuilder serializationBuilder, OptionValues options) {
        GraphBuilderPhase lambdaParserPhase = new LambdaGraphBuilderPhase(buildLambdaParserConfig());
        StructuredGraph graph = createMethodGraph(method, lambdaParserPhase, options);
        registerLambdasFromConstantNodesInGraph(graph, serializationBuilder);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        serializationBuilder.flushConditionalConfiguration(access);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl impl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        OptionValues options = impl.getBigBang().getOptions();
        serializationBuilder.flushConditionalConfiguration(access);

        /*
         * In order to serialize lambda classes we need to register proper methods for reflection.
         * We register all the lambdas from capturing classes written in the serialization
         * configuration file for serialization. In order to find all the lambdas from a class, we
         * parse all the methods of the given class and find all the lambdas in them.
         */
        MetaAccessProvider metaAccess = GraalAccess.getOriginalProviders().getMetaAccess();
        capturingClasses.parallelStream()
                        .map(metaAccess::lookupJavaType)
                        .flatMap(SerializationFeature::allExecutablesDeclaredInClass)
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
    }

    private static Stream<? extends ResolvedJavaMethod> allExecutablesDeclaredInClass(ResolvedJavaType t) {
        return Stream.concat(Stream.concat(
                        Arrays.stream(t.getDeclaredMethods(false)),
                        Arrays.stream(t.getDeclaredConstructors(false))),
                        t.getClassInitializer() == null ? Stream.empty() : Stream.of(t.getClassInitializer()));
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

    private final Constructor<?> stubConstructor;
    private final Field descField;
    private final Method getDataLayoutMethod;

    final SerializationSupport serializationSupport;
    private final SerializationDenyRegistry denyRegistry;
    private final ConfigurationTypeResolver typeResolver;
    private final FeatureImpl.DuringSetupAccessImpl access;
    private final Method disableSerialConstructorChecks;
    private final Method superHasAccessibleConstructor;
    private boolean sealed;
    private final ProxyRegistry proxyRegistry;

    SerializationBuilder(SerializationDenyRegistry serializationDenyRegistry, FeatureImpl.DuringSetupAccessImpl access, ConfigurationTypeResolver typeResolver, ProxyRegistry proxyRegistry) {
        this.access = access;
        Class<?> classDataSlotClazz = access.findClassByName("java.io.ObjectStreamClass$ClassDataSlot");
        this.descField = ReflectionUtil.lookupField(classDataSlotClazz, "desc");
        this.getDataLayoutMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getClassDataLayout");
        this.stubConstructor = newConstructorForSerialization(SerializationSupport.StubForAbstractClass.class, null);
        this.disableSerialConstructorChecks = ReflectionUtil.lookupMethod(true, ReflectionFactory.class, "disableSerialConstructorChecks");
        this.superHasAccessibleConstructor = ReflectionUtil.lookupMethod(ReflectionFactory.class, "superHasAccessibleConstructor", Class.class);

        this.denyRegistry = serializationDenyRegistry;
        this.typeResolver = typeResolver;
        this.proxyRegistry = proxyRegistry;

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
    public void register(ConfigurationCondition condition, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            registerWithTargetConstructorClass(condition, clazz, null);
        }
    }

    @Override
    public void registerLambdaCapturingClass(ConfigurationCondition condition, String lambdaCapturingClassName) {
        abortIfSealed();

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
            SerializationSupport.registerLambdaCapturingClass(lambdaCapturingClassName);
        });
    }

    @Override
    public void registerProxyClass(ConfigurationCondition condition, List<String> implementedInterfaces) {
        registerConditionalConfiguration(condition, (cnd) -> {
            Class<?> proxyClass = proxyRegistry.createProxyClassForSerialization(implementedInterfaces);
            registerWithTargetConstructorClass(ConfigurationCondition.alwaysTrue(), proxyClass, Object.class);
        });
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, String targetClassName, String customTargetConstructorClassName) {
        abortIfSealed();
        Class<?> serializationTargetClass = typeResolver.resolveType(targetClassName);
        /* With invalid streams we have to register the class for lookup */
        ImageSingletons.lookup(RuntimeReflectionSupport.class).registerClassLookup(condition, targetClassName);
        if (serializationTargetClass == null) {
            return;
        }

        if (customTargetConstructorClassName != null) {
            Class<?> customTargetConstructorClass = typeResolver.resolveType(customTargetConstructorClassName);
            if (customTargetConstructorClass == null) {
                return;
            }
            registerWithTargetConstructorClass(condition, serializationTargetClass, customTargetConstructorClass);
        } else {
            registerWithTargetConstructorClass(condition, serializationTargetClass, null);
        }
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, Class<?> serializationTargetClass, Class<?> customTargetConstructorClass) {
        abortIfSealed();
        /*
         * Register class for reflection as it is needed when the class-value itself is serialized.
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
            if (customTargetConstructorClass != null) {
                if (!customTargetConstructorClass.isAssignableFrom(serializationTargetClass)) {
                    LogUtils.warning("The given customTargetConstructorClass %s is not a superclass of the serialization target %s.", customTargetConstructorClass.getName(), serializationTargetClass);
                    return;
                }
                if (ReflectionUtil.lookupConstructor(true, customTargetConstructorClass) == null) {
                    LogUtils.warning("The given customTargetConstructorClass %s does not declare a parameterless constructor.", customTargetConstructorClass.getName());
                    return;
                }
            }
            registerConditionalConfiguration(condition, (cnd) -> {
                Optional.ofNullable(addConstructorAccessor(serializationTargetClass, customTargetConstructorClass))
                                .map(ReflectionUtil::lookupConstructor)
                                .ifPresent(RuntimeReflection::register);

                Class<?> superclass = serializationTargetClass.getSuperclass();
                if (superclass != null) {
                    RuntimeReflection.registerAllDeclaredConstructors(superclass);
                    RuntimeReflection.registerMethodLookup(superclass, "writeReplace");
                    RuntimeReflection.registerMethodLookup(superclass, "readResolve");
                }

                registerForSerialization(serializationTargetClass);
                registerForDeserialization(serializationTargetClass);
            });
        }
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

    private static void registerMethod(Class<?> clazz, String methodName, Class<?>... args) {
        Method method = ReflectionUtil.lookupMethod(true, clazz, methodName, args);
        if (method != null) {
            RuntimeReflection.register(method);
        } else {
            RuntimeReflection.registerMethodLookup(clazz, methodName, args);
        }
    }

    private void registerForSerialization(Class<?> serializationTargetClass) {

        if (Serializable.class.isAssignableFrom(serializationTargetClass)) {
            /*
             * ObjectStreamClass.computeDefaultSUID is always called at runtime to verify
             * serialization class consistency, so need to register all constructors, methods and
             * fields.
             */
            registerSerializationUIDElements(serializationTargetClass, true);

            /*
             * Required by jdk.internal.reflect.ReflectionFactory.newConstructorForSerialization
             */
            Class<?> initCl = serializationTargetClass;
            boolean initClValid = true;
            while (Serializable.class.isAssignableFrom(initCl)) {
                Class<?> prev = initCl;
                RuntimeReflection.registerAllDeclaredConstructors(initCl);
                try {
                    if ((initCl = initCl.getSuperclass()) == null ||
                                    (!(boolean) disableSerialConstructorChecks.invoke(null) &&
                                                    !prev.isArray() &&
                                                    !(Boolean) superHasAccessibleConstructor.invoke(ReflectionFactory.getReflectionFactory(), prev))) {
                        initClValid = false;
                        break;
                    }
                } catch (InvocationTargetException | IllegalAccessException e) {
                    throw VMError.shouldNotReachHere(e);
                }
            }

            if (initClValid) {
                RuntimeReflection.registerAllDeclaredConstructors(initCl);
            }

            Class<?> iter = serializationTargetClass;
            while (iter != null) {
                Arrays.stream(iter.getDeclaredFields()).map(Field::getType).forEach(type -> {
                    RuntimeReflection.registerAllDeclaredMethods(type);
                    RuntimeReflection.registerAllDeclaredFields(type);
                    RuntimeReflection.registerAllDeclaredConstructors(type);
                });
                iter = iter.getSuperclass();
            }
        }

        registerQueriesForInheritableMethod(serializationTargetClass, "writeReplace");
        registerQueriesForInheritableMethod(serializationTargetClass, "readResolve");
        registerMethod(serializationTargetClass, "writeObject", ObjectOutputStream.class);
        registerMethod(serializationTargetClass, "readObjectNoData");
        registerMethod(serializationTargetClass, "readObject", ObjectInputStream.class);
    }

    static void registerSerializationUIDElements(Class<?> serializationTargetClass, boolean fullyRegister) {
        RuntimeReflection.registerAllDeclaredConstructors(serializationTargetClass);
        RuntimeReflection.registerAllDeclaredMethods(serializationTargetClass);
        RuntimeReflection.registerAllDeclaredFields(serializationTargetClass);
        if (fullyRegister) {
            /* This is here a legacy that we can't remove as it is a breaking change */
            RuntimeReflection.register(serializationTargetClass.getDeclaredConstructors());
            RuntimeReflection.register(serializationTargetClass.getDeclaredMethods());
            RuntimeReflection.register(serializationTargetClass.getDeclaredFields());
        }
        RuntimeReflection.registerFieldLookup(serializationTargetClass, "serialPersistentFields");
    }

    public void afterAnalysis() {
        sealed = true;
    }

    private static void registerForDeserialization(Class<?> serializationTargetClass) {
        RuntimeReflection.register(serializationTargetClass);

        if (serializationTargetClass.isRecord()) {
            /* Serialization for records uses the canonical record constructor directly. */
            RuntimeReflection.register(RecordUtils.getCanonicalRecordConstructor(serializationTargetClass));
            /*
             * Serialization for records invokes Class.getRecordComponents(). Registering all record
             * component accessor methods for reflection ensures that the record components are
             * available at run time.
             */
            RuntimeReflection.registerAllRecordComponents(serializationTargetClass);
            RuntimeReflection.register(RecordUtils.getRecordComponentAccessorMethods(serializationTargetClass));
        } else if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            RuntimeReflection.registerConstructorLookup(serializationTargetClass);
        }

        registerMethod(serializationTargetClass, "readObject", ObjectInputStream.class);
        registerMethod(serializationTargetClass, "readResolve");
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
        serializationSupport.registerSerializationTargetClass(serializationTargetClass);
        if (serializationTargetClass.isArray() || Enum.class.isAssignableFrom(serializationTargetClass)) {
            return null;
        }

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
            targetConstructor = stubConstructor;
        } else {
            if (customTargetConstructorClass == serializationTargetClass) {
                /* No custom constructor needed. Simply use existing no-arg constructor. */
                return customTargetConstructorClass;
            }
            Constructor<?> customConstructorToCall = null;
            if (customTargetConstructorClass != null) {
                customConstructorToCall = ReflectionUtil.lookupConstructor(customTargetConstructorClass);
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
