/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationFiles;
import com.oracle.svm.core.configure.ReflectionConfigurationParser;
import com.oracle.svm.core.graal.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.reflect.ReflectionAccessorHolder;
import com.oracle.svm.core.reflect.SubstrateAccessor;
import com.oracle.svm.core.reflect.SubstrateConstructorAccessor;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FallbackFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.config.ConfigurationParserUtils;
import com.oracle.svm.hosted.meta.InternalRuntimeReflectionSupport;
import com.oracle.svm.hosted.snippets.ReflectionPlugins;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticFeature
public class ReflectionFeature implements InternalFeature {

    private AnnotationSubstitutionProcessor annotationSubstitutions;

    private ReflectionDataBuilder reflectionData;
    private ImageClassLoader loader;
    private AnalysisUniverse aUniverse;
    private int loadedConfigurations;

    final Map<Executable, SubstrateAccessor> accessors = new ConcurrentHashMap<>();
    private final Map<SignatureKey, MethodPointer> expandSignatureMethods = new ConcurrentHashMap<>();

    private static final Method invokePrototype = ReflectionUtil.lookupMethod(ReflectionAccessorHolder.class, "invokePrototype",
                    Object.class, Object[].class, CFunctionPointer.class);
    private static final Method methodHandleInvokeErrorMethod = ReflectionUtil.lookupMethod(ReflectionAccessorHolder.class, "methodHandleInvokeError",
                    Object.class, Object[].class, CFunctionPointer.class);
    private static final Method newInstanceErrorMethod = ReflectionUtil.lookupMethod(ReflectionAccessorHolder.class, "newInstanceError",
                    Object.class, Object[].class, CFunctionPointer.class);

    FeatureImpl.BeforeAnalysisAccessImpl analysisAccess;

    SubstrateAccessor getOrCreateAccessor(Executable member) {
        SubstrateAccessor existing = accessors.get(member);
        if (existing != null) {
            return existing;
        }

        if (analysisAccess == null) {
            throw VMError.shouldNotReachHere("New Method or Constructor found as reachable after static analysis: " + member);
        }
        return accessors.computeIfAbsent(member, this::createAccessor);
    }

    /**
     * Creates the accessor instances for {@link SubstrateMethodAccessor invoking a method } or
     * {@link SubstrateConstructorAccessor allocating a new instance} using reflection. The accessor
     * instances use function pointer calls to invocation stubs. The invocation stubs unpack the
     * Object[] array arguments and invoke the actual method.
     * 
     * The stubs are methods with manually created Graal IR:
     * {@link ReflectionExpandSignatureMethod}. Since they are only invoked via function pointers
     * and never at a normal call site, they need to be registered for compilation manually. From
     * the point of view of the static analysis, they are root methods.
     *
     * The stubs then perform another function pointer call to the actual target method. This is
     * either a direct target stored in the accessor, or a virtual target loaded from the vtable.
     * 
     * {@link ConcurrentHashMap#computeIfAbsent} guarantees that this method is called only once per
     * member, so no further synchronization is necessary.
     */
    private SubstrateAccessor createAccessor(Executable member) {
        MethodPointer expandSignature;
        MethodPointer directTarget = null;
        DynamicHub initializeBeforeInvoke = null;
        if (member instanceof Method) {
            int vtableOffset = SubstrateMethodAccessor.STATICALLY_BOUND;
            Class<?> receiverType = null;

            if (member.getDeclaringClass() == MethodHandle.class && (member.getName().equals("invoke") || member.getName().equals("invokeExact"))) {
                /* Method handles must not be invoked via reflection. */
                expandSignature = register(analysisAccess.getMetaAccess().lookupJavaMethod(methodHandleInvokeErrorMethod));
            } else {
                expandSignature = createExpandSignatureMethod(member);
                AnalysisMethod targetMethod = analysisAccess.getMetaAccess().lookupJavaMethod(member);
                /*
                 * The SubstrateMethodAccessor is also used for the implementation of MethodHandle
                 * that are created to do an invokespecial. So non-abstract instance methods have
                 * both a directTarget and a vtableOffset.
                 */
                if (!targetMethod.isAbstract()) {
                    directTarget = register(targetMethod);
                }
                if (!targetMethod.canBeStaticallyBound()) {
                    vtableOffset = SubstrateMethodAccessor.OFFSET_NOT_YET_COMPUTED;
                    analysisAccess.registerAsRoot(targetMethod, false);
                }
                VMError.guarantee(directTarget != null || vtableOffset != SubstrateMethodAccessor.STATICALLY_BOUND, "Must have either a directTarget or a vtableOffset");
                if (!targetMethod.isStatic()) {
                    receiverType = member.getDeclaringClass();
                }
                if (targetMethod.isStatic() && !targetMethod.getDeclaringClass().isInitialized()) {
                    initializeBeforeInvoke = analysisAccess.getHostVM().dynamicHub(targetMethod.getDeclaringClass());
                }
            }
            return new SubstrateMethodAccessor(member, receiverType, expandSignature, directTarget, vtableOffset, initializeBeforeInvoke);

        } else {
            Class<?> holder = member.getDeclaringClass();
            if (Modifier.isAbstract(holder.getModifiers()) || holder.isInterface() || holder.isPrimitive() || holder.isArray()) {
                /*
                 * Invoking the constructor of an abstract class always throws an
                 * InstantiationException. It should not be possible to get a Constructor object for
                 * an interface, array, or primitive type, but we are defensive and throw the
                 * exception in that case too.
                 */
                expandSignature = register(analysisAccess.getMetaAccess().lookupJavaMethod(newInstanceErrorMethod));
            } else {
                expandSignature = createExpandSignatureMethod(member);
                AnalysisMethod constructor = analysisAccess.getMetaAccess().lookupJavaMethod(member);
                AnalysisMethod factoryMethod = (AnalysisMethod) FactoryMethodSupport.singleton().lookup(analysisAccess.getMetaAccess(), constructor, false);
                directTarget = register(factoryMethod);
                if (!constructor.getDeclaringClass().isInitialized()) {
                    initializeBeforeInvoke = analysisAccess.getHostVM().dynamicHub(constructor.getDeclaringClass());
                }
            }
            return new SubstrateConstructorAccessor(member, expandSignature, directTarget, initializeBeforeInvoke);
        }
    }

    private MethodPointer createExpandSignatureMethod(Executable member) {
        return expandSignatureMethods.computeIfAbsent(new SignatureKey(member), signatureKey -> {
            ResolvedJavaMethod prototype = analysisAccess.getMetaAccess().lookupJavaMethod(invokePrototype).getWrapped();
            return register(new ReflectionExpandSignatureMethod(signatureKey.uniqueShortName(), prototype, signatureKey.isStatic, signatureKey.argTypes, signatureKey.returnKind));
        });
    }

    private MethodPointer register(ResolvedJavaMethod method) {
        AnalysisMethod aMethod = method instanceof AnalysisMethod ? (AnalysisMethod) method : analysisAccess.getUniverse().lookup(method);
        analysisAccess.registerAsRoot(aMethod, true);
        return new MethodPointer(aMethod);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.OPEN, null, false, "java.base", "jdk.internal.reflect");

        reflectionData = new ReflectionDataBuilder();
        ImageSingletons.add(RuntimeReflectionSupport.class, reflectionData);
        ImageSingletons.add(InternalRuntimeReflectionSupport.class, reflectionData);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        aUniverse = access.getUniverse();

        ReflectionConfigurationParser<ConditionalElement<Class<?>>> parser = ConfigurationParserUtils.create(reflectionData, access.getImageClassLoader());
        loadedConfigurations = ConfigurationParserUtils.parseAndRegisterConfigurations(parser, access.getImageClassLoader(), "reflection",
                        ConfigurationFiles.Options.ReflectionConfigurationFiles, ConfigurationFiles.Options.ReflectionConfigurationResources,
                        ConfigurationFile.REFLECTION.getFileName());

        loader = access.getImageClassLoader();
        annotationSubstitutions = ((Inflation) access.getBigBang()).getAnnotationSubstitutionProcessor();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        analysisAccess = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        /* duplicated to reduce the number of analysis iterations */
        reflectionData.flushConditionalConfiguration(access);
        /* Make sure array classes don't need to be registered for reflection. */
        RuntimeReflection.register(Object[].class.getMethods());
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        reflectionData.flushConditionalConfiguration(access);
        reflectionData.duringAnalysis(access);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        analysisAccess = null;
        reflectionData.afterAnalysis();
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        if (!ImageSingletons.contains(FallbackFeature.class)) {
            return;
        }
        FallbackFeature.FallbackImageRequest reflectionFallback = ImageSingletons.lookup(FallbackFeature.class).reflectionFallback;
        if (reflectionFallback != null && loadedConfigurations == 0) {
            throw reflectionFallback;
        }
    }

    @Override
    public void registerInvocationPlugins(Providers providers, SnippetReflectionProvider snippetReflection, Plugins plugins, ParsingReason reason) {
        ReflectionPlugins.registerInvocationPlugins(loader, snippetReflection, annotationSubstitutions,
                        plugins.getClassInitializationPlugin(), plugins.getInvocationPlugins(), aUniverse, reason);
    }
}

final class SignatureKey {
    final boolean isStatic;
    final Class<?>[] argTypes;
    final JavaKind returnKind;

    SignatureKey(Executable member) {
        isStatic = member instanceof Constructor || Modifier.isStatic(member.getModifiers());
        argTypes = member.getParameterTypes();
        if (member instanceof Method) {
            returnKind = JavaKind.fromJavaClass(((Method) member).getReturnType());
        } else {
            /* Constructor = factory method that returns the newly allocated object. */
            returnKind = JavaKind.Object;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != SignatureKey.class) {
            return false;
        }
        SignatureKey other = (SignatureKey) obj;
        return isStatic == other.isStatic &&
                        Arrays.equals(argTypes, other.argTypes) &&
                        Objects.equals(returnKind, other.returnKind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isStatic, Arrays.hashCode(argTypes), returnKind);
    }

    @Override
    public String toString() {
        StringBuilder fullName = new StringBuilder();
        fullName.append(isStatic);
        fullName.append("(");
        for (Class<?> c : argTypes) {
            fullName.append(c.getName()).append(",");
        }
        fullName.append(')');
        fullName.append(returnKind);
        return fullName.toString();
    }

    String uniqueShortName() {
        return SubstrateUtil.digest(toString());
    }
}
