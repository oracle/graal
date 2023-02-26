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

import static com.oracle.svm.hosted.reflect.serialize.SerializationFeature.capturingClasses;
import static com.oracle.svm.hosted.reflect.serialize.SerializationFeature.warn;

import java.io.Externalizable;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.java.LambdaUtils;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.replacements.MethodHandlePlugin;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

import com.oracle.graal.pointsto.phases.NoClassInitializationPlugin;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.SerializationConfigurationParser;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.reflect.serialize.SerializationRegistry;
import com.oracle.svm.core.reflect.serialize.SerializationSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.ConfigurationTypeResolver;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.reflect.ReflectionFeature;
import com.oracle.svm.hosted.reflect.proxy.DynamicProxyFeature;
import com.oracle.svm.hosted.reflect.proxy.ProxyRegistry;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.reflect.ReflectionFactory;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
public class SerializationFeature implements InternalFeature {
    static final HashSet<Class<?>> capturingClasses = new HashSet<>();
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
        ConfigurationTypeResolver typeResolver = new ConfigurationTypeResolver("serialization configuration", imageClassLoader);
        SerializationDenyRegistry serializationDenyRegistry = new SerializationDenyRegistry(typeResolver);
        serializationBuilder = new SerializationBuilder(serializationDenyRegistry, access, typeResolver, ImageSingletons.lookup(ProxyRegistry.class));
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

    private static GraphBuilderConfiguration buildLambdaParserConfig() {
        GraphBuilderConfiguration.Plugins plugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        plugins.setClassInitializationPlugin(new NoClassInitializationPlugin());
        plugins.prependNodePlugin(new MethodHandlePlugin(GraalAccess.getOriginalProviders().getConstantReflection().getMethodHandleAccess(), false));
        return GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true);
    }

    @SuppressWarnings("try")
    private static StructuredGraph createMethodGraph(ResolvedJavaMethod method, GraphBuilderPhase lambdaParserPhase, DebugContext debug) {
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

        return lambdaClass.getName().contains(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING) ? lambdaClass : null;
    }

    private static void registerLambdasFromConstantNodesInGraph(StructuredGraph graph) {
        NodeIterable<ConstantNode> constantNodes = ConstantNode.getConstantNodes(graph);

        for (ConstantNode cNode : constantNodes) {
            Class<?> lambdaClass = getLambdaClassFromConstantNode(cNode);

            if (lambdaClass != null && Serializable.class.isAssignableFrom(lambdaClass)) {
                try {
                    Method serializeLambdaMethod = lambdaClass.getDeclaredMethod("writeReplace");
                    RuntimeReflection.register(serializeLambdaMethod);
                } catch (NoSuchMethodException e) {
                    throw VMError.shouldNotReachHere("Serializable lambda class must contain the writeReplace method.");
                }
            }
        }
    }

    @SuppressWarnings("try")
    private static void registerLambdasFromMethod(ResolvedJavaMethod method, DebugContext debug) {
        GraphBuilderPhase lambdaParserPhase = new GraphBuilderPhase(buildLambdaParserConfig());
        StructuredGraph graph = createMethodGraph(method, lambdaParserPhase, debug);
        registerLambdasFromConstantNodesInGraph(graph);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl impl = (FeatureImpl.BeforeAnalysisAccessImpl) access;

        /*
         * In order to serialize lambda classes we need to register proper methods for reflection.
         * Since lambda names are not stable, we do not know which lambdas should be serialized. We
         * simply register all the lambdas from capturing classes written in the serialization
         * configuration file for serialization. In order to find all the lambdas from a class, we
         * parse all the methods of the given class and find all the lambdas in them.
         */
        for (Class<?> clazz : capturingClasses) {
            ResolvedJavaType clazzType = GraalAccess.getOriginalProviders().getMetaAccess().lookupJavaType(clazz);
            List<ResolvedJavaMethod> allMethods = new ArrayList<>(Arrays.asList(clazzType.getDeclaredMethods()));
            allMethods.addAll(Arrays.asList(clazzType.getDeclaredConstructors()));

            for (ResolvedJavaMethod method : allMethods) {
                if (method.getCode() != null) {
                    registerLambdasFromMethod(method, impl.getDebugContext());
                }
            }
        }

        serializationBuilder.flushConditionalConfiguration(access);
        /* Ensure SharedSecrets.javaObjectInputStreamAccess is initialized before scanning. */
        ((BeforeAnalysisAccessImpl) access).ensureInitialized("java.io.ObjectInputStream");
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

    static void warn(String str) {
        System.err.println("Warning: " + str);
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
            warn("Serialization deny list contains " + clazz.getName() + ". Image will not support serialization/deserialization of this class.");
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
    private final ProxyRegistry proxyRegistry;

    SerializationBuilder(SerializationDenyRegistry serializationDenyRegistry, FeatureImpl.DuringSetupAccessImpl access, ConfigurationTypeResolver typeResolver, ProxyRegistry proxyRegistry) {
        this.access = access;
        Class<?> classDataSlotClazz = access.findClassByName("java.io.ObjectStreamClass$ClassDataSlot");
        descField = ReflectionUtil.lookupField(classDataSlotClazz, "desc");
        getDataLayoutMethod = ReflectionUtil.lookupMethod(ObjectStreamClass.class, "getClassDataLayout");
        stubConstructor = newConstructorForSerialization(SerializationSupport.StubForAbstractClass.class, null);
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
                if (!desc.equals(osc)) {
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
    public void registerLambdaCapturingClass(ConfigurationCondition condition, String lambdaCapturingClassName) {
        abortIfSealed();

        Class<?> conditionClass = typeResolver.resolveConditionType(condition.getTypeName());
        if (conditionClass == null) {
            return;
        }

        Class<?> serializationTargetClass = typeResolver.resolveType(lambdaCapturingClassName);
        if (serializationTargetClass == null) {
            return;
        }

        if (ReflectionUtil.lookupMethod(true, serializationTargetClass, "$deserializeLambda$", SerializedLambda.class) == null) {
            warn("Could not register " + serializationTargetClass + " for lambda serialization as it does not capture any serializable lambda.");
            return;
        }

        registerConditionalConfiguration(condition, () -> {
            capturingClasses.add(serializationTargetClass);
            RuntimeReflection.register(serializationTargetClass);
            RuntimeReflection.register(ReflectionUtil.lookupMethod(serializationTargetClass, "$deserializeLambda$", SerializedLambda.class));
        });
    }

    @Override
    public void registerProxyClass(ConfigurationCondition condition, List<String> implementedInterfaces) {
        Class<?> proxyClass = proxyRegistry.createProxyClassForSerialization(new ConditionalElement<>(condition, implementedInterfaces));
        registerWithTargetConstructorClass(ConfigurationCondition.alwaysTrue(), proxyClass, Object.class);
    }

    @Override
    public void registerWithTargetConstructorClass(ConfigurationCondition condition, String targetClassName, String customTargetConstructorClassName) {
        abortIfSealed();

        Class<?> conditionClass = typeResolver.resolveConditionType(condition.getTypeName());
        if (conditionClass == null) {
            return;
        }

        Class<?> serializationTargetClass = typeResolver.resolveType(targetClassName);
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

        Class<?> conditionClass = typeResolver.resolveConditionType(condition.getTypeName());
        if (conditionClass == null) {
            return;
        }

        if (!Serializable.class.isAssignableFrom(serializationTargetClass)) {
            warn("Could not register " + serializationTargetClass.getName() + " for serialization as it does not implement Serializable.");
            return;
        }

        if (denyRegistry.isAllowed(serializationTargetClass)) {
            if (customTargetConstructorClass != null) {
                if (!customTargetConstructorClass.isAssignableFrom(serializationTargetClass)) {
                    warn("The given customTargetConstructorClass " + customTargetConstructorClass.getName() +
                                    " is not a superclass of the serialization target " + serializationTargetClass + ".");
                    return;
                }
                if (ReflectionUtil.lookupConstructor(true, customTargetConstructorClass) == null) {
                    warn("The given customTargetConstructorClass " + customTargetConstructorClass.getName() +
                                    " does not declare a parameterless constructor.");
                    return;
                }
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
        } else if (Externalizable.class.isAssignableFrom(serializationTargetClass)) {
            RuntimeReflection.register(ReflectionUtil.lookupConstructor(serializationTargetClass, (Class<?>[]) null));
        }

        RuntimeReflection.register(serializationTargetClass);
        /*
         * ObjectStreamClass.computeDefaultSUID is always called at runtime to verify serialization
         * class consistency, so need to register all constructors, methods and fields.
         */
        RuntimeReflection.register(serializationTargetClass.getDeclaredConstructors());
        RuntimeReflection.register(serializationTargetClass.getDeclaredMethods());
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
