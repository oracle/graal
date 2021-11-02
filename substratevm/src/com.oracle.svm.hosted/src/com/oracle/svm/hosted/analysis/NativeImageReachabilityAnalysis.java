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

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.reachability.MethodSummaryProvider;
import com.oracle.graal.reachability.ReachabilityAnalysis;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.word.WordBase;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

public class NativeImageReachabilityAnalysis extends ReachabilityAnalysis implements Inflation {

    private Set<AnalysisField> handledUnknownValueFields = ConcurrentHashMap.newKeySet();
    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
    private final DynamicHubInitializer dynamicHubInitializer;
    private final boolean strengthenGraalGraphs;

    public NativeImageReachabilityAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, AnnotationSubstitutionProcessor annotationSubstitutionProcessor,
                    ForkJoinPool executor,
                    Runnable heartbeatCallback, MethodSummaryProvider methodSummaryProvider) {
        super(options, universe, providers, universe.hostVM(), executor, heartbeatCallback, new SubstrateUnsupportedFeatures(), methodSummaryProvider);
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;
        this.strengthenGraalGraphs = SubstrateOptions.parseOnce();
        this.dynamicHubInitializer = new DynamicHubInitializer(universe, metaAccess, unsupportedFeatures, providers.getConstantReflection());
    }

    @Override
    public boolean strengthenGraalGraphs() {
        return strengthenGraalGraphs;
    }

    @Override
    public AnnotationSubstitutionProcessor getAnnotationSubstitutionProcessor() {
        return annotationSubstitutionProcessor;
    }

    @Override
    protected void checkObjectGraph(ObjectScanner objectScanner) {
        universe.getFields().forEach(this::handleUnknownValueField);
        universe.getTypes().stream().filter(AnalysisType::isReachable).forEach(dynamicHubInitializer::initializeMetaData);

        /* Scan hubs of all types that end up in the native image. */
        universe.getTypes().stream().filter(AnalysisType::isReachable).forEach(type -> scanHub(objectScanner, type));
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        handledUnknownValueFields = null;
    }

    @Override
    public SubstrateReplacements getReplacements() {
        return (SubstrateReplacements) super.getReplacements();
    }

    @Override
    public SVMHost getHostVM() {
        return (SVMHost) hostVM;
    }

    @Override
    public void checkUserLimitations() {
        // todo
    }

    private void scanHub(ObjectScanner objectScanner, AnalysisType type) {
        SVMHost svmHost = (SVMHost) hostVM;
        JavaConstant hubConstant = SubstrateObjectConstant.forObject(svmHost.dynamicHub(type));
        objectScanner.scanConstant(hubConstant, ObjectScanner.ScanReason.HUB);
    }

    private void handleUnknownValueField(AnalysisField field) {
        if (handledUnknownValueFields.contains(field)) {
            return;
        }
        if (!field.isAccessed()) {
            /*
             * Field is not reachable yet, so do no process it. In particular, we must not register
             * types listed in the @UnknownObjectField annotation as allocated when the field is not
             * yet reachable
             */
            return;
        }

        UnknownObjectField unknownObjectField = field.getAnnotation(UnknownObjectField.class);
        UnknownPrimitiveField unknownPrimitiveField = field.getAnnotation(UnknownPrimitiveField.class);
        if (unknownObjectField != null) {
            assert !Modifier.isFinal(field.getModifiers()) : "@UnknownObjectField annotated field " + field.format("%H.%n") + " cannot be final";
            assert field.getJavaKind() == JavaKind.Object;

            field.setCanBeNull(unknownObjectField.canBeNull());

            List<AnalysisType> aAnnotationTypes = extractAnnotationTypes(field, unknownObjectField);

            for (AnalysisType type : aAnnotationTypes) {
                type.registerAsAllocated(null);
            }

            /*
             * Use the annotation types, instead of the declared type, in the UnknownObjectField
             * annotated fields initialization.
             */
            handleUnknownObjectField(field, aAnnotationTypes.toArray(new AnalysisType[0]));

        } else if (unknownPrimitiveField != null) {
            assert !Modifier.isFinal(field.getModifiers()) : "@UnknownPrimitiveField annotated field " + field.format("%H.%n") + " cannot be final";
            /*
             * Register a primitive field as containing unknown values(s), i.e., is usually written
             * only in hosted code.
             */

            field.registerAsWritten(null);
        }

        handledUnknownValueFields.add(field);
    }

    private List<AnalysisType> extractAnnotationTypes(AnalysisField field, UnknownObjectField unknownObjectField) {
        List<Class<?>> annotationTypes = new ArrayList<>(Arrays.asList(unknownObjectField.types()));
        for (String annotationTypeName : unknownObjectField.fullyQualifiedTypes()) {
            try {
                Class<?> annotationType = Class.forName(annotationTypeName);
                annotationTypes.add(annotationType);
            } catch (ClassNotFoundException e) {
                throw shouldNotReachHere("Annotation type not found " + annotationTypeName);
            }
        }

        List<AnalysisType> aAnnotationTypes = new ArrayList<>();
        AnalysisType declaredType = field.getType();

        for (Class<?> annotationType : annotationTypes) {
            AnalysisType aAnnotationType = metaAccess.lookupJavaType(annotationType);

            assert !WordBase.class.isAssignableFrom(annotationType) : "Annotation type must not be a subtype of WordBase: field: " + field + " | declared type: " + declaredType +
                            " | annotation type: " + annotationType;
            assert declaredType.isAssignableFrom(aAnnotationType) : "Annotation type must be a subtype of the declared type: field: " + field + " | declared type: " + declaredType +
                            " | annotation type: " + annotationType;
            assert aAnnotationType.isArray() || (aAnnotationType.isInstanceClass() && !Modifier.isAbstract(aAnnotationType.getModifiers())) : "Annotation type failure: field: " + field +
                            " | annotation type " + aAnnotationType;

            aAnnotationTypes.add(aAnnotationType);
        }
        return aAnnotationTypes;
    }

    /**
     * Register a field as containing unknown object(s), i.e., is usually written only in hosted
     * code. It can have multiple declared types provided via annotation.
     */
    private void handleUnknownObjectField(AnalysisField aField, AnalysisType... declaredTypes) {
        assert aField.getJavaKind() == JavaKind.Object;

        aField.registerAsWritten(null);

        /* Link the field with all declared types. */
        for (AnalysisType fieldDeclaredType : declaredTypes) {
            markTypeReachable(fieldDeclaredType);
        }
    }

}
