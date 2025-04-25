/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSObject;
import org.graalvm.webimage.api.JSValue;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.webimage.JSObjectAccessFeature;
import com.oracle.svm.hosted.webimage.codegen.node.InterceptJSInvokeNode;
import com.oracle.svm.hosted.webimage.codegen.oop.ClassWithMirrorLowerer;
import com.oracle.svm.hosted.webimage.util.ReflectUtil;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.webimage.api.Nothing;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides support for java calls inside {@link JS}.
 *
 * All methods referenced will be registered as compiled.
 */
@AutomaticallyRegisteredFeature
@Platforms(WebImageJSPlatform.class)
public final class JSBodyFeature implements InternalFeature {
    // The set of methods that are potentially overridden by a JS-annotated method.
    private Set<Method> jsOverridden;

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        FeatureImpl.AfterRegistrationAccessImpl accessImpl = (FeatureImpl.AfterRegistrationAccessImpl) access;
        ImageClassLoader imageClassLoader = accessImpl.getImageClassLoader();
        List<Class<?>> allClasses = imageClassLoader.findSubclasses(Object.class, true);
        jsOverridden = ReflectUtil.findBaseMethodsOfJSAnnotated(allClasses);
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        plugins.appendNodePlugin(new NodePlugin() {
            @Override
            public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                if (AnnotationAccess.isAnnotationPresent(method.getDeclaringClass(), JS.Import.class)) {
                    ((AnalysisType) method.getDeclaringClass()).registerAsInstantiated("JS.Import classes might be allocated in JavaScript. We need to tell the static analysis about that");
                }
                if (canBeJavaScriptCall((AnalysisMethod) method)) {
                    InterceptJSInvokeNode intercept = b.append(new InterceptJSInvokeNode(method, b.bci()));
                    for (final ValueNode arg : args) {
                        intercept.arguments().add(arg);
                    }
                }
                return false;
            }

            private boolean canBeJavaScriptCall(AnalysisMethod method) {
                Executable executable;
                try {
                    executable = method.getJavaMethod();
                } catch (Throwable e) {
                    // Either a substituted method, or a method with a malformed bytecode signature.
                    // This is most likely not a JS-annotated method.
                    return false;
                }
                if (executable instanceof Method) {
                    return jsOverridden.contains(executable);
                }
                // Not a normal method (constructor).
                return false;
            }

            @Override
            public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
                if (ClassWithMirrorLowerer.isJSObjectSubtype(((AnalysisType) field.getDeclaringClass()).getJavaClass())) {
                    genJSObjectFieldAccess(b, object, field, null);
                    return true;
                }
                return false;
            }

            @Override
            public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
                if (ClassWithMirrorLowerer.isJSObjectSubtype(((AnalysisType) field.getDeclaringClass()).getJavaClass())) {
                    genJSObjectFieldAccess(b, object, field, value);
                    return true;
                }
                return false;
            }

            private void genJSObjectFieldAccess(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode valueForStore) {
                ResolvedJavaType fieldType = field.getType().resolve(null);
                JavaKind fieldKind = fieldType.getJavaKind();
                ConstantNode fieldNameNode = ConstantNode.forConstant(b.getConstantReflection().forString(field.getName()), b.getMetaAccess(), b.getGraph());

                boolean isLoad = valueForStore == null;

                ForeignCallDescriptor bridgeMethod;
                ValueNode[] arguments;
                if (isLoad) {
                    // This is a load access.
                    bridgeMethod = JSObjectAccessFeature.GETTERS.get(fieldKind);
                    arguments = new ValueNode[]{object, fieldNameNode};
                } else {
                    // This is a store access.
                    bridgeMethod = JSObjectAccessFeature.SETTERS.get(fieldKind);
                    arguments = new ValueNode[]{object, fieldNameNode, valueForStore};
                }
                ValueNode access = new ForeignCallNode(bridgeMethod, arguments);
                if (isLoad) {
                    b.addPush(fieldKind, access);
                    // A checkcast is necessary to guard against invalid assignments in JavaScript
                    // code.
                    if (fieldKind.isObject()) {
                        b.pop(fieldKind);
                        Stamp classStamp = StampFactory.forDeclaredType(null, b.getMetaAccess().lookupJavaType(Class.class), true).getTrustedStamp();
                        ConstantNode classConstant = b.add(new ConstantNode(b.getConstantReflection().asObjectHub(fieldType.resolve(null)), classStamp));
                        b.genCheckcastDynamic(access, classConstant);
                    }
                } else {
                    b.add(access);
                }
            }
        });
        plugins.prependInlineInvokePlugin(new InlineInvokePlugin() {
            @Override
            public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
                ResolvedJavaType jsObjectType = b.getMetaAccess().lookupJavaType(JSObject.class);
                ResolvedJavaType declaringClass = method.getDeclaringClass();
                // Constructors of JavaScript classes must never be inlined, because they contain
                // initialization code related to mirror hookup.
                if (method.isConstructor() && jsObjectType.isAssignableFrom(declaringClass)) {
                    return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
                }
                return InlineInvokePlugin.super.shouldInlineInvoke(b, method, args);
            }

            @Override
            public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
                ResolvedJavaType jsObjectType = b.getMetaAccess().lookupJavaType(JSObject.class);
                ResolvedJavaType declaringClass = method.getDeclaringClass();
                // Important: even though the node is not inlined during parsing, later inlining
                // attempts must be prevented too.
                if (method.isConstructor() && jsObjectType.isAssignableFrom(declaringClass)) {
                    invoke.setUseForInlining(false);
                }
            }
        });
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        BigBang bigbang = accessImpl.getBigBang();

        /*
         * If a @JS.Import class is reachable, register it as allocated.
         *
         * The Web Image runtime will create instances of a @JS.Import class in JavaScript/Java
         * conversion.
         */
        accessImpl.registerSubtypeReachabilityHandler((acc, clazz) -> {
            if (clazz.isAnnotationPresent(JS.Import.class)) {
                String reason = "@JS.import class " + clazz + " reachable, registered from " + JSBodyFeature.class;
                AnalysisType cls = accessImpl.getMetaAccess().lookupJavaType(clazz);
                cls.registerAsInstantiated(reason);
            }
        }, JSObject.class);

        for (Class<? extends JSValue> subclass : accessImpl.findSubclasses(JSValue.class)) {
            if (!Modifier.isAbstract(subclass.getModifiers())) {
                // Include classes that correspond to the primitive JS values, and only reference JS
                // values that were exported. The rest of the JS values must be *used* from the Java
                // program in order to be included in the image.
                //
                // JSObject must always be included because everything may be covertly converted to
                // JSObject (in generated code).
                if (JSObject.class == subclass || !JSObject.class.isAssignableFrom(subclass)) {
                    accessImpl.registerAsInHeap(subclass);
                }
            }
            if (subclass.isAnnotationPresent(JS.Export.class)) {
                accessImpl.registerAsInHeap(subclass);
            }
        }
        // Add helper classes.
        bigbang.addRootClass(Nothing.class, true, false);

        bigbang.addRootMethod(ReflectionUtil.lookupMethod(JSValue.class, "as", Class.class), true, "JSValue.as, registered in " + JSBodyFeature.class);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        findJSObjectSubtypes(accessImpl);
    }

    /**
     * Any public method or constructor of a subclass of {@link JSObject} may be called from
     * JavaScript. They need to be registered as roots to make the static analysis aware of that
     * fact. This prevents for example propagation of Java constants through parameters of such
     * methods by the static analysis.
     * <p>
     * Similarly, fields of subclasses of {@link JSObject} may be modified in JavaScript. Therefore,
     * the static analysis needs to be aware of that fact.
     * <p>
     * An exception is @JS.Import classes, whose methods and constructors are intended to be called
     * from Java. Therefore, the reachable methods of @JS.Import classes are expected to be
     * discovered by the reachability analysis.
     */
    private static void findJSObjectSubtypes(FeatureImpl.DuringAnalysisAccessImpl access) {
        boolean requireAnalysisIteration = false;
        for (Class<?> jsObjectClass : access.findSubclasses(JSObject.class)) {
            // The methods of @JS.Import are intended to be called from Java. They can be discovered
            // by the analysis.
            if (jsObjectClass.isAnnotationPresent(JS.Import.class)) {
                continue;
            }

            AnalysisType type = access.getMetaAccess().lookupJavaType(jsObjectClass);
            if (type.isReachable()) {
                for (AnalysisMethod method : type.getDeclaredMethods(false)) {
                    // TODO GR-33956: Only register public methods
                    if (!(method.isDirectRootMethod() || method.isVirtualRootMethod())) {
                        access.registerAsRoot(method, false, "JSObject subtype method, registered in " + JSBodyFeature.class);
                        requireAnalysisIteration = true;
                    }
                }
                for (AnalysisMethod method : type.getDeclaredConstructors(false)) {
                    // TODO GR-33956: Only register public constructors
                    if (!(method.isDirectRootMethod() || method.isVirtualRootMethod())) {
                        access.registerAsRoot(method, true, "JSObject subtype constructor, registered in " + JSBodyFeature.class);
                        requireAnalysisIteration = true;
                    }
                }
                for (ResolvedJavaField javaField : type.getInstanceFields(false)) {
                    AnalysisField field = (AnalysisField) javaField;
                    // TODO GR-33956: Only register public/protected fields
                    requireAnalysisIteration = requireAnalysisIteration | field.registerAsAccessed("used from web-image");
                    requireAnalysisIteration = requireAnalysisIteration | field.registerAsWritten("used from web-image");
                    if (!field.isUnsafeAccessed()) {
                        field.registerAsUnsafeAccessed("used from web-image");
                        requireAnalysisIteration = true;
                    }
                }
            }
        }

        if (requireAnalysisIteration) {
            access.requireAnalysisIteration();
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        jsOverridden = null;
    }
}
