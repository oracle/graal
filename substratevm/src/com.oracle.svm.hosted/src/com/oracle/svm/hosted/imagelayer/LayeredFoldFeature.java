/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.imagelayer;

import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.heap.ImageHeapRelocatableConstant;
import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.imagelayer.LayeredFoldResolver;
import com.oracle.svm.core.imagelayer.LayeredFoldSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestAnnotationAccess;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Adds layer-aware resolution to {@link Fold}. An explicit resolver can compute a {@link Fold} in
 * the initial or application layer. Values computed before the application layer are persisted in
 * the cross-layer constant registry. Application-layer values are represented in earlier layers
 * by future heap constants and finalized when the application layer is built.
 *
 * A {@link FoldInvocation} identifies one Fold call across layers. Its method and object-argument
 * IDs are stable across layers; primitive arguments are encoded by kind and raw bits.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = LayeredFoldFeature.LayeredCallbacks.class)
public final class LayeredFoldFeature implements InternalFeature, LayeredFoldSupport {
    private static final String KEY_PREFIX = "layered-fold:";
    private static final String NULL_KEY_SUFFIX = ":null";
    private static final String PENDING_FOLDS = "pendingFolds";
    private static final String PENDING_FOLD_ARGUMENTS = "pendingFoldArguments";
    private static final Object NULL_MARKER = new Object();

    /** Caches values within one build and prevents concurrent parsing from resolving a {@link Fold} twice. */
    private final ConcurrentMap<String, JavaConstant> resolvedConstants = new ConcurrentHashMap<>();

    /** Application-layer invocations discovered while building an earlier layer. */
    private final Set<FoldInvocation> pendingApplicationFolds = ConcurrentHashMap.newKeySet();

    private record FoldInvocation(int methodId, List<String> arguments) {
        String key() {
            return KEY_PREFIX + methodId + ':' + String.join(",", arguments);
        }
    }

    public static LayeredFoldFeature singleton() {
        return ImageSingletons.lookup(LayeredFoldFeature.class);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LayeredFoldFeature.class, this);
        ImageSingletons.add(LayeredFoldSupport.class, this);
    }

    public void preparePendingApplicationFolds() {
        CrossLayerConstantRegistryFeature registry = CrossLayerConstantRegistryFeature.singleton();
        for (FoldInvocation invocation : pendingApplicationFolds.stream().sorted(Comparator.comparing(FoldInvocation::key)).toList()) {
            JavaConstant constant = registry.getConstant(invocation.key());
            if (constant instanceof ImageHeapRelocatableConstant relocatable) {
                ImageHeapRelocatableConstantSupport.singleton().registerLoadableConstant(relocatable);
            }
            if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
                /* All beforeAnalysis callbacks have initialized Fold-visible hosted state. */
                resolvePendingApplicationFold(registry, invocation);
            }
        }
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            pendingApplicationFolds.clear();
        }
    }

    @Override
    public ValueNode resolve(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode[] arguments, Supplier<JavaConstant> computation) {
        AnalysisMethod analysisMethod = asAnalysisMethod(targetMethod);
        Fold fold = GuestAnnotationAccess.getAnnotation(targetMethod, Fold.class);
        JavaConstant[] constantArguments = Arrays.stream(arguments).map(ValueNode::asJavaConstant).toArray(JavaConstant[]::new);
        LayeredFoldResolver.LayeredResolutionContext context = new LayeredResolutionContextImpl(b, analysisMethod, constantArguments, null);
        JavaConstant result = invokeResolver(analysisMethod, fold, context, computation);
        JavaKind returnKind = analysisMethod.getSignature().getReturnType().getStorageKind();
        if (result instanceof ImageHeapRelocatableConstant relocatable) {
            /*
             * A future object can be loaded directly. There is no relocatable primitive constant,
             * so future primitives are transported in one-element arrays and loaded from index 0.
             * Use the return storage kind because Word types have Java kind Object but primitive
             * storage kinds.
             */
            if (returnKind == JavaKind.Object) {
                /* The future result may be null or a covariant subtype of its declared array type. */
                ResolvedJavaType declaringClass = targetMethod.getDeclaringClass();
                ResolvedJavaType returnType = targetMethod.getSignature().getReturnType(declaringClass).resolve(declaringClass);
                return ImageHeapRelocatableConstantSupport.singleton().createLoad(b, relocatable, new ObjectStamp(returnType, false, false, false, returnType.isArray()));
            }
            ValueNode load = ImageHeapRelocatableConstantSupport.singleton().createLoad(b, relocatable);
            return b.add(LoadIndexedNode.create(b.getAssumptions(), load, ConstantNode.forInt(0), null, returnKind, b.getMetaAccess(), b.getConstantReflection()));
        }

        JavaConstant resolvedConstant = result;
        if (returnKind != JavaKind.Object && result.getJavaKind() == JavaKind.Object) {
            /* A primitive persisted in an earlier layer is read back as its backing array. */
            resolvedConstant = b.getConstantReflection().readArrayElement(result, 0);
        }

        return ConstantNode.forConstant(resolvedConstant, b.getMetaAccess(), b.getGraph());
    }

    private static Object asHostedObject(GraphBuilderContext b, JavaConstant constant, String key) {
        /*
         * Results obtained while parsing may be analysis image-heap constants. Snippet reflection
         * performs the corresponding hosted-object conversion for the graph builder.
         */
        Object object = b.getSnippetReflection().asObject(Object.class, constant);
        if (object == null) {
            throw UserError.abort("Layered Fold %s produced an object that is not available in the hosted VM", key);
        }
        return object;
    }

    private static AnalysisType getFutureType(AnalysisMethod method) {
        AnalysisType returnType = method.getSignature().getReturnType();
        JavaKind returnKind = returnType.getStorageKind();
        if (returnKind == JavaKind.Object) {
            /*
             * A future heap constant needs a sufficiently precise type. Arrays can be represented
             * directly by the future-constant machinery; other object return types must be final.
             */
            if (!returnType.isArray() && !returnType.isFinalFlagSet()) {
                throw UserError.abort("Application-layer Fold result type must be exact: %s", returnType.toJavaName());
            }
            return returnType;
        }
        /* Primitive results use a one-element array as their relocatable heap object. */
        return method.getUniverse().lookup(GuestAccess.get().lookupType(returnKind.toJavaClass())).getArrayClass();
    }

    @SuppressWarnings("unchecked")
    private static JavaConstant invokeResolver(AnalysisMethod method, Fold fold, LayeredFoldResolver.LayeredResolutionContext context, Supplier<JavaConstant> computation) {
        var resolver = (Fold.Resolver<? super LayeredFoldResolver.LayeredResolutionContext>) ReflectionUtil.newInstance(fold.resolver());
        JavaConstant result = resolver.resolve(context, computation);
        if (result == null) {
            throw UserError.abort("Resolver for layered Fold %s returned null instead of a JavaConstant", method.format("%H.%n(%p)"));
        }

        AnalysisType returnType = method.getSignature().getReturnType();
        JavaKind returnKind = returnType.getStorageKind();
        boolean valid;
        if (returnKind == JavaKind.Object) {
            AnalysisType resultType = method.getUniverse().getBigbang().getMetaAccess().lookupJavaType(result);
            valid = result.isNull() || resultType != null && returnType.isAssignableFrom(resultType);
        } else {
            /* A persisted or future primitive is transported in its one-element array. */
            valid = result.getJavaKind().getStackKind() == returnKind.getStackKind() ||
                            result instanceof TypedConstant typed && typed.getType().equals(getFutureType(method));
        }
        if (!valid) {
            Object actualType = result instanceof TypedConstant typed ? typed.getType().toJavaName() : result.getJavaKind();
            throw UserError.abort("Resolver for layered Fold %s returned %s, which is incompatible with %s", method.format("%H.%n(%p)"), actualType, returnType.toJavaName());
        }
        return result;
    }

    private final class LayeredResolutionContextImpl implements LayeredFoldResolver.LayeredResolutionContext {
        private final GraphBuilderContext b;
        private final AnalysisMethod method;
        private final CrossLayerConstantRegistryFeature registry;
        private final JavaConstant[] arguments;
        /** Replay must reuse the persisted IDs rather than re-encode the decoded hosted objects. */
        private final FoldInvocation replayInvocation;

        private LayeredResolutionContextImpl(GraphBuilderContext b, AnalysisMethod method, JavaConstant[] arguments, FoldInvocation replayInvocation) {
            this.b = b;
            this.method = method;
            this.registry = CrossLayerConstantRegistryFeature.singleton();
            this.arguments = arguments;
            this.replayInvocation = replayInvocation;
        }

        @Override
        public JavaConstant resolveInInitialLayer(Supplier<JavaConstant> computation) {
            FoldInvocation invocation = replayInvocation != null ? replayInvocation : createInvocation(method, arguments);
            JavaConstant existing = lookupResult(invocation);
            if (existing != null) {
                return existing;
            }
            /* A later layer is not allowed to recompute a value whose policy names the initial layer. */
            if (!ImageLayerBuildingSupport.buildingInitialLayer()) {
                throw UserError.abort("Layered Fold %s must be resolved in the initial layer, but no value was recorded", method.format("%H.%n(%p)"));
            }
            return resolveNow(computation, invocation);
        }

        @Override
        public JavaConstant resolveInApplicationLayer(Supplier<JavaConstant> computation) {
            FoldInvocation invocation = replayInvocation != null ? replayInvocation : createInvocation(method, arguments);
            String invocationKey = invocation.key();
            if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
                return resolvedConstants.computeIfAbsent(invocationKey, _ -> computation.get());
            }
            JavaConstant existing = lookupResult(invocation);
            if (existing != null) {
                return existing;
            }
            return resolvedConstants.computeIfAbsent(invocationKey, _ -> {
                /*
                 * Compile the earlier layer against a typed placeholder. The invocation is
                 * persisted so that the application layer can compute and finalize its value.
                 */
                pendingApplicationFolds.add(invocation);
                return registry.registerFutureHeapConstant(invocationKey, getFutureType(method));
            });
        }

        private JavaConstant resolveNow(Supplier<JavaConstant> computation, FoldInvocation invocation) {
            String invocationKey = invocation.key();
            return resolvedConstants.computeIfAbsent(invocationKey, _ -> {
                JavaConstant result = computation.get();
                /*
                 * The cross-layer registry transports heap objects. Null therefore uses a
                 * separate marker, and a primitive uses a one-element primitive array. The
                 * array is only cross-layer storage; compiled code sees its element as the
                 * Fold result.
                 */
                JavaKind returnKind = method.getSignature().getReturnType().getStorageKind();
                if (returnKind == JavaKind.Object) {
                    if (result.isNull()) {
                        registry.registerHeapConstant(invocationKey + NULL_KEY_SUFFIX, NULL_MARKER);
                    } else {
                        registry.registerHeapConstant(invocationKey, asHostedObject(b, result, invocationKey));
                    }
                } else {
                    registry.registerHeapConstant(invocationKey, createPrimitiveArray(returnKind, result));
                }
                return result;
            });
        }

        private JavaConstant lookupResult(FoldInvocation invocation) {
            String invocationKey = invocation.key();
            /* Null has no registry value of its own, so its marker must be checked separately. */
            if (registry.constantExists(invocationKey + NULL_KEY_SUFFIX)) {
                return JavaConstant.NULL_POINTER;
            }
            JavaConstant resolved = resolvedConstants.get(invocationKey);
            if (resolved != null) {
                return resolved;
            }
            return registry.constantExists(invocationKey) ? registry.getConstant(invocationKey) : null;
        }
    }

    private static FoldInvocation createInvocation(AnalysisMethod method, JavaConstant[] arguments) {
        /* The persisted method ID is only meaningful when the method is tracked across layers. */
        method.registerAsTrackedAcrossLayers("Layered Fold invocation");
        Executable javaMethod = method.getJavaMethod();
        Parameter[] parameters = javaMethod.getParameters();
        int firstArgument = method.hasReceiver() ? 1 : 0;
        List<String> encodedArguments = new ArrayList<>(arguments.length);
        for (int i = 0; i < arguments.length; i++) {
            Parameter parameter = i < firstArgument ? null : parameters[i - firstArgument];
            encodedArguments.add(encodeArgument(method, parameter, arguments[i], i));
        }
        return new FoldInvocation(method.getId(), List.copyOf(encodedArguments));
    }

    private static String encodeArgument(AnalysisMethod method, Parameter parameter, JavaConstant constant, int index) {
        if (parameter != null && parameter.isAnnotationPresent(Fold.InjectedParameter.class)) {
            return "X";
        }
        if (constant.isNull()) {
            return "N";
        }
        if (parameter != null && parameter.getType() == ResolvedJavaType.class) {
            ResolvedJavaType type = GuestAccess.get().getProviders().getConstantReflection().asJavaType(constant);
            if (type == null) {
                type = GuestAccess.get().getSnippetReflection().asObject(ResolvedJavaType.class, constant);
            }
            AnalysisType analysisType = method.getUniverse().lookup(type);
            analysisType.registerAsTrackedAcrossLayers("Layered Fold argument");
            return "T" + analysisType.getId();
        }
        /* Preserve raw primitive bits so the invocation key also distinguishes all FP bit patterns. */
        if (constant instanceof PrimitiveConstant primitive) {
            return encodePrimitive(primitive);
        }
        if (!(constant instanceof ImageHeapConstant heapConstant)) {
            throw UserError.abort("Object argument %d of layered Fold %s is not an image heap constant", index, method.format("%H.%n(%p)"));
        }
        /*
         * Object identity is represented by the image-heap constant ID. Persisting the constant
         * lets a later layer resolve that stable ID back to the relinked hosted object. It must not
         * make the argument an image-heap root because doing so can change analysis reachability.
         */
        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            HostedImageLayerBuildingSupport.singleton().getWriter().persistConstant(heapConstant);
        }
        return "O" + ImageHeapConstant.getConstantID(heapConstant);
    }

    private static String encodePrimitive(PrimitiveConstant primitive) {
        return primitive.getJavaKind().getTypeChar() + Long.toUnsignedString(primitive.getRawValue(), 16);
    }

    private static PrimitiveConstant decodePrimitive(String encoded) {
        return JavaConstant.forPrimitive(encoded.charAt(0), Long.parseUnsignedLong(encoded.substring(1), 16));
    }

    private void resolvePendingApplicationFold(CrossLayerConstantRegistryFeature registry, FoldInvocation invocation) {
        /* Reconstruct the invocation from IDs persisted by the earlier layer. */
        SVMImageLayerLoader loader = HostedImageLayerBuildingSupport.singleton().getLoader();
        AnalysisMethod method = loader.getAnalysisMethodForBaseLayerId(invocation.methodId());
        List<String> encodedArguments = invocation.arguments();
        JavaConstant[] constants = new JavaConstant[encodedArguments.size()];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = decodeArgument(loader, method, encodedArguments.get(i), i);
        }
        JavaConstant result;
        try {
            LayeredFoldResolver.LayeredResolutionContext context = new LayeredResolutionContextImpl(null, method, constants, invocation);
            result = invokeResolver(method, GuestAnnotationAccess.getAnnotation(method, Fold.class), context, () -> invoke(method, constants));
        } catch (Throwable t) {
            throw UserError.abort(t, "Failed to resolve layered Fold %s in the application layer", method.format("%H.%n(%p)"));
        }
        JavaKind returnKind = method.getSignature().getReturnType().getStorageKind();
        Object object;
        if (returnKind == JavaKind.Object) {
            /*
             * This resolution runs through GuestAccess outside graph parsing, so there is no
             * GraphBuilderContext whose snippet reflection could perform the conversion.
             */
            object = result.isNull() ? null : GuestAccess.get().asHostObject(Object.class, result);
        } else {
            object = createPrimitiveArray(returnKind, result);
        }
        /* Keep the real constant for uses parsed in this build, then bind earlier-layer placeholders. */
        resolvedConstants.put(invocation.key(), result);
        registry.finalizeFutureHeapConstant(invocation.key(), object);
    }

    private static JavaConstant invoke(AnalysisMethod method, JavaConstant[] arguments) {
        JavaConstant receiver = method.hasReceiver() ? arguments[0] : null;
        JavaConstant[] methodArguments = method.hasReceiver() ? Arrays.copyOfRange(arguments, 1, arguments.length) : arguments;
        return GuestAccess.get().invoke(OriginalMethodProvider.getOriginalMethod(method), receiver, methodArguments);
    }

    private static Object getInjectedArgument(Class<?> type) {
        Providers providers = GuestAccess.get().getProviders();
        Object[] candidates = {providers.getMetaAccess(), providers.getConstantReflection(), providers.getSnippetReflection(), providers.getStampProvider(), providers.getForeignCalls(),
                        providers.getWordTypes(), providers.getCodeCache(), providers.getLowerer(), providers.getReplacements()};
        for (Object candidate : candidates) {
            if (type.isInstance(candidate)) {
                return candidate;
            }
        }
        return providers.getReplacements().getInjectedArgument(type);
    }

    private static JavaConstant asGuestArgument(Class<?> type, Object object) {
        ResolvedJavaType guestType = GuestAccess.get().lookupType(type);
        return guestType.isInterface() ? GuestAccess.get().createHostProxy(object, guestType) : GuestAccess.get().getSnippetReflection().forObject(object);
    }

    private static Object createPrimitiveArray(JavaKind kind, JavaConstant value) {
        /*
         * A heap object gives the cross-layer registry an identity that can be relocated. A load
         * from element zero turns it back into the primitive value in the compiled graph.
         */
        return switch (kind) {
            case Boolean -> new boolean[]{value.asInt() != 0};
            case Byte -> new byte[]{(byte) value.asInt()};
            case Short -> new short[]{(short) value.asInt()};
            case Char -> new char[]{(char) value.asInt()};
            case Int -> new int[]{value.asInt()};
            case Long -> new long[]{value.asLong()};
            case Float -> new float[]{value.asFloat()};
            case Double -> new double[]{value.asDouble()};
            default -> throw UserError.abort("Unsupported layered Fold return kind: %s", kind);
        };
    }

    private static JavaConstant decodeArgument(SVMImageLayerLoader loader, AnalysisMethod method, String encoded, int argumentIndex) {
        if (encoded.charAt(0) == 'X') {
            /* Injected arguments are recreated from the application-layer compiler providers. */
            int parameterIndex = argumentIndex - (method.hasReceiver() ? 1 : 0);
            Class<?> parameterClass = OriginalClassProvider.getJavaClass(method.getSignature().getParameterType(parameterIndex));
            Object injectedArgument;
            try {
                injectedArgument = getInjectedArgument(parameterClass);
            } catch (GraalError error) {
                throw UserError.abort(error, "Cannot replay application-layer Fold %s: unsupported injected parameter %s", method.format("%H.%n(%p)"), parameterClass);
            }
            return asGuestArgument(parameterClass, injectedArgument);
        }
        if (encoded.charAt(0) == 'T') {
            AnalysisType type = loader.getAnalysisTypeForBaseLayerId(Integer.parseInt(encoded.substring(1)));
            return asGuestArgument(ResolvedJavaType.class, type);
        }
        if (encoded.charAt(0) == 'O') {
            ImageHeapConstant heapConstant = loader.getOrCreateConstant(Integer.parseInt(encoded.substring(1)));
            /* Resolver execution needs the relinked hosted object, not merely its image-heap ID. */
            if (!heapConstant.isBackedByHostedObject()) {
                throw UserError.abort("Object argument %s of layered Fold %s was not relinked to a hosted object", ImageHeapConstant.getConstantID(heapConstant), method.format("%H.%n(%p)"));
            }
            return heapConstant.getHostedObject();
        }
        if (encoded.charAt(0) == 'N') {
            return JavaConstant.NULL_POINTER;
        }
        PrimitiveConstant primitive = decodePrimitive(encoded);
        int parameterIndex = argumentIndex - (method.hasReceiver() ? 1 : 0);
        /* Graph arguments use stack kinds; reflective invocation requires the declared kind. */
        return JavaConstant.forPrimitive(method.getSignature().getParameterKind(parameterIndex), primitive.getRawValue());
    }

    private static AnalysisMethod asAnalysisMethod(ResolvedJavaMethod method) {
        if (method instanceof AnalysisMethod analysisMethod) {
            return analysisMethod;
        }
        if (method instanceof HostedMethod hostedMethod) {
            return hostedMethod.wrapped;
        }
        throw UserError.abort("Unsupported method representation for layered Fold: %s", method);
    }

    static final class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
            return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<LayeredFoldFeature>() {
                @Override
                public LayeredPersistFlags doPersist(ImageSingletonWriter writer, LayeredFoldFeature singleton) {
                    List<FoldInvocation> invocations = singleton.pendingApplicationFolds.stream().sorted(Comparator.comparing(FoldInvocation::key)).toList();
                    /*
                     * Store (method ID, argument count) pairs and one flat argument list. Argument
                     * counts make reconstruction unambiguous without introducing an escaping
                     * convention for the encoded arguments.
                     */
                    List<Integer> foldData = new ArrayList<>(invocations.size() * 2);
                    invocations.forEach(invocation -> {
                        foldData.add(invocation.methodId());
                        foldData.add(invocation.arguments().size());
                    });
                    writer.writeIntList(PENDING_FOLDS, foldData);
                    writer.writeStringList(PENDING_FOLD_ARGUMENTS, invocations.stream().flatMap(invocation -> invocation.arguments().stream()).toList());
                    return LayeredPersistFlags.CALLBACK_ON_REGISTRATION;
                }

                @Override
                public void onSingletonRegistration(ImageSingletonLoader loader, LayeredFoldFeature singleton) {
                    Iterator<Integer> foldData = loader.readIntList(PENDING_FOLDS).iterator();
                    Iterator<String> arguments = loader.readStringList(PENDING_FOLD_ARGUMENTS).iterator();
                    while (foldData.hasNext()) {
                        int methodId = foldData.next();
                        int argumentCount = foldData.next();
                        List<String> invocationArguments = new ArrayList<>(argumentCount);
                        for (int i = 0; i < argumentCount; i++) {
                            invocationArguments.add(arguments.next());
                        }
                        singleton.pendingApplicationFolds.add(new FoldInvocation(methodId, List.copyOf(invocationArguments)));
                    }
                }
            });
        }
    }
}
