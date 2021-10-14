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

import static com.oracle.graal.pointsto.reports.AnalysisReportsOptions.PrintAnalysisCallTree;
import static com.oracle.svm.hosted.NativeImageOptions.MaxReachableTypes;
import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

import org.graalvm.compiler.core.common.SuppressSVMWarnings;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class NativeImagePointsToAnalysis extends PointsToAnalysis implements Inflation {
    private Set<AnalysisField> handledUnknownValueFields;

    private final Pattern illegalCalleesPattern;
    private final Pattern targetCallersPattern;
    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;
    private final DynamicHubInitializer dynamicHubInitializer;

    public NativeImagePointsToAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, AnnotationSubstitutionProcessor annotationSubstitutionProcessor,
                    ForkJoinPool executor, Runnable heartbeatCallback, UnsupportedFeatures unsupportedFeatures) {
        super(options, universe, providers, universe.hostVM(), executor, heartbeatCallback, new SubstrateUnsupportedFeatures(), SubstrateOptions.parseOnce());
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;

        String[] targetCallers = new String[]{"com\\.oracle\\.graal\\.", "org\\.graalvm[^\\.polyglot\\.nativeapi]"};
        targetCallersPattern = buildPrefixMatchPattern(targetCallers);

        String[] illegalCallees = new String[]{"java\\.util\\.stream", "java\\.util\\.Collection\\.stream", "java\\.util\\.Arrays\\.stream"};
        illegalCalleesPattern = buildPrefixMatchPattern(illegalCallees);

        handledUnknownValueFields = new HashSet<>();
        dynamicHubInitializer = new DynamicHubInitializer(universe, metaAccess, unsupportedFeatures, providers.getConstantReflection());
    }

    @Override
    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, MethodTypeFlow methodFlow) {
        return HostedConfiguration.instance().createMethodTypeFlowBuilder(bb, methodFlow);
    }

    @Override
    protected void checkObjectGraph(ObjectScanner objectScanner) {
        universe.getFields().forEach(this::handleUnknownValueField);
        universe.getTypes().stream().filter(AnalysisType::isReachable).forEach(dynamicHubInitializer::initializeMetaData);

        /* Scan hubs of all types that end up in the native image. */
        universe.getTypes().stream().filter(AnalysisType::isReachable).forEach(type -> scanHub(objectScanner, type));
    }

    @Override
    public SVMHost getHostVM() {
        return (SVMHost) hostVM;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        handledUnknownValueFields = null;
    }

    @Override
    public void checkUserLimitations() {
        int maxReachableTypes = MaxReachableTypes.getValue();
        if (maxReachableTypes >= 0) {
            CallTreePrinter callTreePrinter = new CallTreePrinter(this);
            callTreePrinter.buildCallTree();
            int numberOfTypes = callTreePrinter.classesSet(false).size();
            if (numberOfTypes > maxReachableTypes) {
                throw UserError.abort("Reachable %d types but only %d allowed (because the %s option is set). To see all reachable types use %s; to change the maximum number of allowed types use %s.",
                                numberOfTypes, maxReachableTypes, MaxReachableTypes.getName(), PrintAnalysisCallTree.getName(), MaxReachableTypes.getName());
            }
        }
    }

    @Override
    public AnnotationSubstitutionProcessor getAnnotationSubstitutionProcessor() {
        return annotationSubstitutionProcessor;
    }

    private void scanHub(ObjectScanner objectScanner, AnalysisType type) {
        SVMHost svmHost = (SVMHost) hostVM;
        JavaConstant hubConstant = SubstrateObjectConstant.forObject(svmHost.dynamicHub(type));
        objectScanner.scanConstant(hubConstant, ScanReason.HUB);
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
            assert aAnnotationType.isArray() || (aAnnotationType.isInstanceClass() && !Modifier.isAbstract(aAnnotationType.getModifiers())) : "Annotation type cannot be abstract: field: " + field +
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
            TypeFlow<?> fieldDeclaredTypeFlow = fieldDeclaredType.getTypeFlow(this, true);
            if (aField.isStatic()) {
                fieldDeclaredTypeFlow.addUse(this, aField.getStaticFieldFlow());
            } else {
                fieldDeclaredTypeFlow.addUse(this, aField.getInitialInstanceFieldFlow());
                if (fieldDeclaredType.isArray()) {
                    AnalysisType fieldComponentType = fieldDeclaredType.getComponentType();
                    aField.getInitialInstanceFieldFlow().addUse(this, aField.getInstanceFieldFlow());
                    // TODO is there a better way to signal that the elements type flow of a unknown
                    // object field is not empty?
                    if (!fieldComponentType.isPrimitive()) {
                        /*
                         * Write the component type abstract object into the field array elements
                         * type flow, i.e., the array elements type flow of the abstract object of
                         * the field declared type.
                         *
                         * This is required so that the index loads from this array return all the
                         * possible objects that can be stored in the array.
                         */
                        TypeFlow<?> elementsFlow = fieldDeclaredType.getContextInsensitiveAnalysisObject().getArrayElementsFlow(this, true);
                        fieldComponentType.getTypeFlow(this, false).addUse(this, elementsFlow);

                        /*
                         * In the current implementation it is not necessary to do it it recursively
                         * for multidimensional arrays since we don't model individual array
                         * elements, so from the point of view of the static analysis the field's
                         * array elements value is non null (in the case of a n-dimensional array
                         * that value is another array, n-1 dimensional).
                         */
                    }
                }
            }
        }
    }

    public static ResolvedJavaType toWrappedType(ResolvedJavaType type) {
        if (type instanceof AnalysisType) {
            return ((AnalysisType) type).getWrappedWithoutResolve();
        } else if (type instanceof HostedType) {
            return ((HostedType) type).getWrapped().getWrappedWithoutResolve();
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

    /**
     * Builds a pattern that checks if the tested string starts with any of the target prefixes,
     * like so: {@code ^(str1(.*)|str2(.*)|str3(.*))}.
     */
    private static Pattern buildPrefixMatchPattern(String[] targetPrefixes) {
        StringBuilder patternStr = new StringBuilder("^(");
        for (int i = 0; i < targetPrefixes.length; i++) {
            String prefix = targetPrefixes[i];
            patternStr.append(prefix);
            patternStr.append("(.*)");
            if (i < targetPrefixes.length - 1) {
                patternStr.append("|");
            }
        }
        patternStr.append(')');
        return Pattern.compile(patternStr.toString());
    }

    @Override
    public boolean isCallAllowed(PointsToAnalysis bb, AnalysisMethod caller, AnalysisMethod callee, NodeSourcePosition srcPosition) {
        String calleeName = callee.getQualifiedName();
        if (illegalCalleesPattern.matcher(calleeName).find()) {
            String callerName = caller.getQualifiedName();
            if (targetCallersPattern.matcher(callerName).find()) {
                SuppressSVMWarnings suppress = caller.getAnnotation(SuppressSVMWarnings.class);
                AnalysisType callerType = caller.getDeclaringClass();
                while (suppress == null && callerType != null) {
                    suppress = callerType.getAnnotation(SuppressSVMWarnings.class);
                    callerType = callerType.getEnclosingType();
                }
                if (suppress != null) {
                    String[] reasons = suppress.value();
                    for (String r : reasons) {
                        if (r.equals("AllowUseOfStreamAPI")) {
                            return true;
                        }
                    }
                }
                String message = "Illegal: Graal/Truffle use of Stream API: " + calleeName;
                int bci = srcPosition.getBCI();
                String trace = caller.asStackTraceElement(bci).toString();
                bb.getUnsupportedFeatures().addMessage(callerName, caller, message, trace);
                return false;
            }
        }
        return true;
    }

    @Override
    public SubstrateReplacements getReplacements() {
        return (SubstrateReplacements) super.getReplacements();
    }
}
