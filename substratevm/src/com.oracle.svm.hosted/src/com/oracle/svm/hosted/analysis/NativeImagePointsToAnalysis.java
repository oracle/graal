/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.oracle.graal.pointsto.ClassInclusionPolicy;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.infrastructure.OriginalFieldProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.CustomTypeFieldHandler;
import com.oracle.svm.hosted.code.IncompatibleClassChangeFallbackMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class NativeImagePointsToAnalysis extends PointsToAnalysis implements Inflation {

    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
    private final DynamicHubInitializer dynamicHubInitializer;
    private final CustomTypeFieldHandler customTypeFieldHandler;
    private final CallChecker callChecker;

    @SuppressWarnings("this-escape")
    public NativeImagePointsToAnalysis(OptionValues options, AnalysisUniverse universe,
                    AnalysisMetaAccess metaAccess, SnippetReflectionProvider snippetReflectionProvider,
                    ConstantReflectionProvider constantReflectionProvider, WordTypes wordTypes,
                    AnnotationSubstitutionProcessor annotationSubstitutionProcessor, UnsupportedFeatures unsupportedFeatures,
                    DebugContext debugContext, TimerCollection timerCollection, ClassInclusionPolicy classInclusionPolicy) {
        super(options, universe, universe.hostVM(), metaAccess, snippetReflectionProvider, constantReflectionProvider, wordTypes, unsupportedFeatures, debugContext, timerCollection,
                        classInclusionPolicy);
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;

        dynamicHubInitializer = new DynamicHubInitializer(this);
        customTypeFieldHandler = new PointsToCustomTypeFieldHandler(this, metaAccess);
        callChecker = new CallChecker();
    }

    @Override
    public boolean isCallAllowed(PointsToAnalysis bb, AnalysisMethod caller, AnalysisMethod target, BytecodePosition srcPosition) {
        return callChecker.isCallAllowed(bb, caller, target, srcPosition);
    }

    @Override
    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod methodFlow, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        return HostedConfiguration.instance().createMethodTypeFlowBuilder(bb, methodFlow, flowsGraph, graphKind);
    }

    @Override
    public SVMHost getHostVM() {
        return (SVMHost) hostVM;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        customTypeFieldHandler.cleanupAfterAnalysis();
    }

    @Override
    public void checkUserLimitations() {
        super.checkUserLimitations();
        UserLimitationsChecker.check(this);
    }

    @Override
    public AnnotationSubstitutionProcessor getAnnotationSubstitutionProcessor() {
        return annotationSubstitutionProcessor;
    }

    @Override
    public void onFieldAccessed(AnalysisField field) {
        customTypeFieldHandler.handleField(field);
    }

    @Override
    public void injectFieldTypes(AnalysisField aField, List<AnalysisType> customTypes, boolean canBeNull) {
        customTypeFieldHandler.injectFieldTypes(aField, customTypes, canBeNull);
    }

    @Override
    public void onTypeReachable(AnalysisType type) {
        postTask(d -> {
            type.getInitializeMetaDataTask().ensureDone();
            if (type.isInBaseLayer()) {
                /*
                 * Since the rescanning of the hub is skipped for constants from the base layer to
                 * avoid deadlocks, the hub needs to be rescanned manually after the metadata is
                 * initialized.
                 */
                universe.getImageLayerLoader().rescanHub(type, ((SVMHost) hostVM).dynamicHub(type));
            }
            if (SubstrateOptions.includeAll()) {
                /*
                 * Using getInstanceFields and getStaticFields allows to include the fields from the
                 * substitution class.
                 */
                Stream.concat(Arrays.stream(getOrDefault(type, t -> t.getInstanceFields(true), new AnalysisField[0])),
                                Arrays.stream(getOrDefault(type, AnalysisType::getStaticFields, new AnalysisField[0])))
                                .map(OriginalFieldProvider::getJavaField)
                                .filter(field -> field != null && classInclusionPolicy.isFieldIncluded(field))
                                .forEach(classInclusionPolicy::includeField);
            }
        });
    }

    @Override
    public void initializeMetaData(AnalysisType type) {
        dynamicHubInitializer.initializeMetaData(universe.getHeapScanner(), type);
    }

    public static ResolvedJavaType toWrappedType(ResolvedJavaType type) {
        if (type instanceof AnalysisType) {
            return ((AnalysisType) type).getWrapped();
        } else if (type instanceof HostedType) {
            return ((HostedType) type).getWrapped().getWrapped();
        } else {
            return type;
        }
    }

    @Override
    public boolean trackConcreteAnalysisObjects(AnalysisType type) {
        /*
         * For classes marked as UnknownClass no context sensitive analysis is done, i.e., no
         * concrete objects are tracked.
         *
         * It is assumed that an object of type C can be of any type compatible with C. At the same
         * type fields of type C can be of any type compatible with their declared type.
         */

        return !SVMHost.isUnknownClass(type);
    }

    /** See {@link IncompatibleClassChangeFallbackMethod} for documentation. */
    @Override
    public AnalysisMethod fallbackResolveConcreteMethod(AnalysisType resolvingType, AnalysisMethod method) {
        if (!resolvingType.isAbstract() && !resolvingType.isInterface() && !method.isStatic() && method.getDeclaringClass().isAssignableFrom(resolvingType)) {
            /*
             * We are resolving an instance method for a concrete (non-abstract) class that is a
             * subtype of the method's declared type. So this is a method invocation that can happen
             * at run time, and we need to return a method that throws an exception when being
             * executed.
             */

            if (method.getWrapped() instanceof IncompatibleClassChangeFallbackMethod) {
                /*
                 * We are re-resolving a method that we already processed. Nothing to do, we already
                 * have the appropriate fallback method.
                 */
                return method;
            }
            return getUniverse().lookup(new IncompatibleClassChangeFallbackMethod(resolvingType.getWrapped(), method.getWrapped(), findResolutionError(resolvingType, method.getJavaMethod())));
        }
        return super.fallbackResolveConcreteMethod(resolvingType, method);
    }

    /**
     * Finding the correct exception that needs to be thrown at run time is a bit tricky, since
     * JVMCI does not report that information back when method resolution fails. We need to look
     * down the class hierarchy to see if there would be an appropriate method with a matching
     * signature which is just not accessible.
     *
     * We do all the method lookups (to search for a method with the same signature as searchMethod)
     * using reflection and not JVMCI because the lookup can throw all sorts of errors, and we want
     * to ignore the errors without any possible side effect on AnalysisType and AnalysisMethod.
     */
    private static Class<? extends IncompatibleClassChangeError> findResolutionError(AnalysisType resolvingType, Executable searchMethod) {
        if (searchMethod != null) {
            Class<?>[] searchSignature = searchMethod.getParameterTypes();
            for (Class<?> cur = resolvingType.getJavaClass(); cur != null; cur = cur.getSuperclass()) {
                Method found;
                try {
                    found = cur.getDeclaredMethod(searchMethod.getName(), searchSignature);
                } catch (Throwable ex) {
                    /*
                     * Method does not exist, a linkage error was thrown, or something else random
                     * is wrong with the class files. Ignore this class.
                     */
                    continue;
                }
                if (Modifier.isAbstract(found.getModifiers()) || Modifier.isPrivate(found.getModifiers()) || Modifier.isStatic(found.getModifiers())) {
                    /*
                     * We found a method with a matching signature, but it does not have an
                     * implementation, or it is a private / static method that does not count from
                     * the point of view of method resolution.
                     */
                    return AbstractMethodError.class;
                } else {
                    /*
                     * We found a method with a matching signature, but it must have the wrong
                     * access modifier (otherwise method resolution would have returned it).
                     */
                    return IllegalAccessError.class;
                }
            }
        }
        /* Not matching method found at all. */
        return AbstractMethodError.class;
    }
}
