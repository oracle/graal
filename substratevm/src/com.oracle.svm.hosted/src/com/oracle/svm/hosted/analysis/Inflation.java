/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;

import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.graal.pointsto.util.AnalysisError.TypeNotFoundError;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.hub.AnnotatedSuperInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.GenericInfo;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageClassLoader;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class Inflation extends BigBang {
    private Set<AnalysisField> handledUnknownValueFields;
    private Map<GenericInterfacesEncodingKey, Type[]> genericInterfacesMap;
    private Map<AnnotatedInterfacesEncodingKey, AnnotatedType[]> annotatedInterfacesMap;
    private Map<InterfacesEncodingKey, DynamicHub[]> interfacesEncodings;

    private final Pattern illegalCalleesPattern;
    private final Pattern targetCallersPattern;

    public Inflation(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executor) {
        super(options, universe, providers, hostVM, executor, new SubstrateUnsupportedFeatures());

        String[] targetCallers = new String[]{"com\\.oracle\\.graal\\.", "org\\.graalvm[^\\.polyglot\\.nativeapi]"};
        targetCallersPattern = buildPrefixMatchPattern(targetCallers);

        String[] illegalCallees = new String[]{"java\\.util\\.stream", "java\\.util\\.Collection\\.stream", "java\\.util\\.Arrays\\.stream"};
        illegalCalleesPattern = buildPrefixMatchPattern(illegalCallees);

        handledUnknownValueFields = new HashSet<>();
        genericInterfacesMap = new HashMap<>();
        annotatedInterfacesMap = new HashMap<>();
        interfacesEncodings = new HashMap<>();
    }

    public SVMAnalysisPolicy svmAnalysisPolicy() {
        return (SVMAnalysisPolicy) super.analysisPolicy();
    }

    @Override
    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(BigBang bb, MethodTypeFlow methodFlow) {
        return new SVMMethodTypeFlowBuilder(bb, methodFlow);
    }

    @Override
    public boolean addRoot(JavaConstant constant, Object root) {
        SubstrateObjectConstant sConstant = (SubstrateObjectConstant) constant;
        return sConstant.setRoot(root);
    }

    @Override
    public Object getRoot(JavaConstant constant) {
        SubstrateObjectConstant sConstant = (SubstrateObjectConstant) constant;
        return sConstant.getRoot();
    }

    @Override
    protected void checkObjectGraph(ObjectScanner objectScanner) {
        universe.getFields().forEach(this::handleUnknownValueField);
        universe.getTypes().forEach(this::checkType);

        /* Scan hubs of all types that end up in the native image. */
        universe.getTypes().stream().filter(type -> type.isInstantiated() || type.isInTypeCheck() || type.isPrimitive()).forEach(type -> scanHub(objectScanner, type));
    }

    private void checkType(AnalysisType type) {
        SVMHost svmHost = (SVMHost) hostVM;

        if (type.getJavaKind() == JavaKind.Object) {
            if (type.isArray() && (type.isInstantiated() || type.isInTypeCheck())) {
                svmHost.dynamicHub(type).getComponentHub().setArrayHub(svmHost.dynamicHub(type));
            }

            try {
                AnalysisType enclosingType = type.getEnclosingType();
                if (enclosingType != null) {
                    svmHost.dynamicHub(type).setEnclosingClass(svmHost.dynamicHub(enclosingType));
                }
            } catch (UnsupportedFeatureException ex) {
                getUnsupportedFeatures().addMessage(type.toJavaName(true), null, ex.getMessage(), null, ex);
            }

            fillGenericInfo(type);
            fillInterfaces(type);

            /*
             * Support for Java annotations.
             */
            svmHost.dynamicHub(type).setAnnotationsEncoding(encodeAnnotations(metaAccess, type.getAnnotations(), svmHost.dynamicHub(type).getAnnotationsEncoding()));

            /*
             * Support for Java enumerations.
             */
            if (type.getSuperclass() != null && type.getSuperclass().equals(metaAccess.lookupJavaType(Enum.class)) && svmHost.dynamicHub(type).getEnumConstantsShared() == null) {
                /*
                 * We want to retrieve the enum constant array that is maintained as a private
                 * static field in the enumeration class. We do not want a copy because that would
                 * mean we have the array twice in the native image: as the static field, and in the
                 * enumConstant field of DynamicHub. The only way to get the original value is via a
                 * reflective field access, and we even have to guess the field name.
                 */
                AnalysisField found = null;
                for (AnalysisField f : type.getStaticFields()) {
                    if (f.getName().endsWith("$VALUES")) {
                        if (found != null) {
                            /*
                             * Enumeration has more than one static field with enumeration values.
                             * Bailout and use Class.getEnumConstants() to get the value instead.
                             */
                            found = null;
                            break;
                        }
                        found = f;
                    }
                }
                Enum<?>[] enumConstants;
                if (found == null) {
                    /*
                     * We could not find a unique $VALUES field, so we use the value returned by
                     * Class.getEnumConstants(). This is not ideal since Class.getEnumConstants()
                     * returns a copy of the array, so we will have two arrays with the same content
                     * in the image heap, but it is better than failing image generation.
                     */
                    enumConstants = (Enum<?>[]) type.getJavaClass().getEnumConstants();
                } else {
                    enumConstants = (Enum[]) SubstrateObjectConstant.asObject(getConstantReflectionProvider().readFieldValue(found, null));
                    assert enumConstants != null;
                }
                svmHost.dynamicHub(type).setEnumConstants(enumConstants);
            }
        }
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        handledUnknownValueFields = null;
        genericInterfacesMap = null;
        annotatedInterfacesMap = null;
        interfacesEncodings = null;
    }

    @Override
    public boolean isValidClassLoader(Object valueObj) {
        return valueObj.getClass().getClassLoader() == null || // boot class loader
                        !(valueObj.getClass().getClassLoader() instanceof NativeImageClassLoader) || valueObj.getClass().getClassLoader() == Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void checkUserLimitations() {
        int maxReachableTypes = MaxReachableTypes.getValue();
        if (maxReachableTypes >= 0) {
            CallTreePrinter callTreePrinter = new CallTreePrinter(this);
            callTreePrinter.buildCallTree();
            int numberOfTypes = callTreePrinter.classesSet(false).size();
            if (numberOfTypes > maxReachableTypes) {
                throw UserError.abort("Reachable " + numberOfTypes + " types but only " + maxReachableTypes + " allowed (because the " + MaxReachableTypes.getName() +
                                " option is set). To see all reachable types use " + PrintAnalysisCallTree.getName() + "; to change the maximum number of allowed types use " +
                                MaxReachableTypes.getName() +
                                ".");
            }
        }
    }

    class GenericInterfacesEncodingKey {
        final Type[] interfaces;

        GenericInterfacesEncodingKey(Type[] aInterfaces) {
            this.interfaces = aInterfaces;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof GenericInterfacesEncodingKey && Arrays.equals(interfaces, ((GenericInterfacesEncodingKey) obj).interfaces);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(interfaces);
        }
    }

    class AnnotatedInterfacesEncodingKey {
        final AnnotatedType[] interfaces;

        AnnotatedInterfacesEncodingKey(AnnotatedType[] aInterfaces) {
            this.interfaces = aInterfaces;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AnnotatedInterfacesEncodingKey && Arrays.equals(interfaces, ((AnnotatedInterfacesEncodingKey) obj).interfaces);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(interfaces);
        }
    }

    private void fillGenericInfo(AnalysisType type) {
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);

        Class<?> javaClass = type.getJavaClass();

        TypeVariable<?>[] typeParameters = javaClass.getTypeParameters();
        Type[] genericInterfaces = Arrays.stream(javaClass.getGenericInterfaces()).filter(this::filterGenericInterfaces).toArray(Type[]::new);
        Type[] cachedGenericInterfaces = genericInterfacesMap.computeIfAbsent(new GenericInterfacesEncodingKey(genericInterfaces), k -> genericInterfaces);
        Type genericSuperClass = javaClass.getGenericSuperclass();
        hub.setGenericInfo(GenericInfo.factory(typeParameters, cachedGenericInterfaces, genericSuperClass));

        AnnotatedType annotatedSuperclass = javaClass.getAnnotatedSuperclass();
        AnnotatedType[] annotatedInterfaces = Arrays.stream(javaClass.getAnnotatedInterfaces())
                        .filter(ai -> filterGenericInterfaces(ai.getType())).toArray(AnnotatedType[]::new);
        AnnotatedType[] cachedAnnotatedInterfaces = annotatedInterfacesMap.computeIfAbsent(
                        new AnnotatedInterfacesEncodingKey(annotatedInterfaces), k -> annotatedInterfaces);
        hub.setAnnotatedSuperInfo(AnnotatedSuperInfo.factory(annotatedSuperclass, cachedAnnotatedInterfaces));
    }

    private boolean filterGenericInterfaces(Type t) {
        if (t instanceof Class) {
            Optional<? extends ResolvedJavaType> resolved = metaAccess.optionalLookupJavaType((Class<?>) t);
            return resolved.isPresent() && universe.hostVM().platformSupported(resolved.get());
        }

        return true;
    }

    class InterfacesEncodingKey {
        final AnalysisType[] aInterfaces;

        InterfacesEncodingKey(AnalysisType[] aInterfaces) {
            this.aInterfaces = aInterfaces;
        }

        DynamicHub[] createHubs() {
            SVMHost svmHost = (SVMHost) hostVM;
            DynamicHub[] hubs = new DynamicHub[aInterfaces.length];
            for (int i = 0; i < hubs.length; i++) {
                hubs[i] = svmHost.dynamicHub(aInterfaces[i]);
            }
            return hubs;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InterfacesEncodingKey && Arrays.equals(aInterfaces, ((InterfacesEncodingKey) obj).aInterfaces);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(aInterfaces);
        }
    }

    /**
     * Fill array returned by Class.getInterfaces().
     */
    private void fillInterfaces(AnalysisType type) {
        SVMHost svmHost = (SVMHost) hostVM;
        DynamicHub hub = svmHost.dynamicHub(type);

        AnalysisType[] aInterfaces = type.getInterfaces();
        if (aInterfaces.length == 0) {
            hub.setInterfacesEncoding(null);
        } else if (aInterfaces.length == 1) {
            hub.setInterfacesEncoding(svmHost.dynamicHub(aInterfaces[0]));
        } else {
            /*
             * Many interfaces arrays are the same, e.g., all arrays implement the same two
             * interfaces. We want to avoid duplicate arrays with the same content in the native
             * image heap.
             */
            hub.setInterfacesEncoding(interfacesEncodings.computeIfAbsent(new InterfacesEncodingKey(aInterfaces), k -> k.createHubs()));
        }
    }

    private void scanHub(ObjectScanner objectScanner, AnalysisType type) {
        SVMHost svmHost = (SVMHost) hostVM;
        JavaConstant hubConstant = SubstrateObjectConstant.forObject(svmHost.dynamicHub(type));
        objectScanner.scanConstant(hubConstant, "Hub");
    }

    private void handleUnknownValueField(AnalysisField field) {
        if (handledUnknownValueFields.contains(field)) {
            return;
        }

        UnknownObjectField unknownObjectField = field.getAnnotation(UnknownObjectField.class);
        UnknownPrimitiveField unknownPrimitiveField = field.getAnnotation(UnknownPrimitiveField.class);
        if (unknownObjectField != null) {
            assert !Modifier.isFinal(field.getModifiers()) : "@UnknownObjectField annotated field " + field.format("%H.%n") + " cannot be final";
            assert field.getJavaKind() == JavaKind.Object;

            field.setCanBeNull(unknownObjectField.canBeNull());

            List<AnalysisType> aAnnotationTypes = extractAnnotationTypes(field, unknownObjectField);

            /*
             * Only if an UnknownValue field is really accessed, we register all the field's
             * sub-types as allocated.
             */
            if (field.isAccessed()) {
                for (AnalysisType type : aAnnotationTypes) {
                    type.registerAsAllocated(null);
                }
            }

            /*
             * Use the annotation types, instead of the declared type, in the UnknownObjectField
             * annotated fields initialization.
             */
            handleUnknownObjectField(field, aAnnotationTypes.toArray(new AnalysisType[aAnnotationTypes.size()]));

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

    public static Object encodeAnnotations(AnalysisMetaAccess metaAccess, Annotation[] allAnnotations, Object oldEncoding) {
        Object newEncoding;
        if (allAnnotations.length == 0) {
            newEncoding = null;
        } else {
            List<Annotation> usedAnnotations = new ArrayList<>();
            for (Annotation annotation : allAnnotations) {
                try {
                    AnalysisType annotationClass = metaAccess.lookupJavaType(annotation.getClass());
                    if (isAnnotationUsed(annotationClass)) {
                        usedAnnotations.add(annotation);
                    }
                } catch (TypeNotFoundError e) {
                    /*
                     * Silently ignore the annotation if its type was not discovered by the static
                     * analysis.
                     */
                }
            }
            if (usedAnnotations.size() == 0) {
                newEncoding = null;
            } else if (usedAnnotations.size() == 1) {
                newEncoding = usedAnnotations.get(0);
            } else {
                newEncoding = usedAnnotations.toArray(new Annotation[usedAnnotations.size()]);
            }
        }

        /*
         * Return newEncoding only if the value is different from oldEncoding. Without this guard,
         * for tests that do runtime compilation, the field appears as being continuously updated
         * during BigBang.checkObjectGraph.
         */
        if (oldEncoding == newEncoding || (oldEncoding instanceof Annotation[] && newEncoding instanceof Annotation[] && Arrays.equals((Annotation[]) oldEncoding, (Annotation[]) newEncoding))) {
            return oldEncoding;
        } else {
            return newEncoding;
        }
    }

    /**
     * We only want annotations in the native image heap that are "used" at run time. In our case,
     * "used" means that the annotation interface is used at least in a type check. This leaves one
     * case where Substrate VM behaves differently than a normal Java VM: When you just query the
     * number of annotations on a class, then we might return a lower number.
     */
    private static boolean isAnnotationUsed(AnalysisType annotationType) {
        if (annotationType.isInstantiated() || annotationType.isInTypeCheck()) {
            return true;
        }
        assert annotationType.getInterfaces().length == 1 : annotationType;

        AnalysisType annotationInterfaceType = annotationType.getInterfaces()[0];
        return annotationInterfaceType.isInstantiated() || annotationInterfaceType.isInTypeCheck();
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

        if (SVMHost.isUnknownClass(type)) {
            return false;
        }

        if (type.isArray() && SVMHost.isUnknownClass(type.getComponentType())) {
            // TODO are arrays of unknown value types also unknown?
            throw JVMCIError.unimplemented();
        }

        return true;
    }

    /**
     * Builds a pattern that checks if the tested string starts with any of the target prefixes,
     * like so: {@code str1(.*)|str2(.*)|str3(.*)}.
     */
    private static Pattern buildPrefixMatchPattern(String[] targetPrefixes) {
        String patternStr = "";
        for (int i = 0; i < targetPrefixes.length; i++) {
            String prefix = targetPrefixes[i];
            patternStr += prefix;
            patternStr += "(.*)";
            if (i < targetPrefixes.length - 1) {
                patternStr += "|";
            }
        }
        return Pattern.compile(patternStr);
    }

    @Override
    public boolean isCallAllowed(BigBang bb, AnalysisMethod caller, AnalysisMethod callee, NodeSourcePosition srcPosition) {
        String calleeName = callee.format("%H.%n");
        if (illegalCalleesPattern.matcher(calleeName).find()) {
            String callerName = caller.format("%H.%n");
            if (targetCallersPattern.matcher(callerName).find()) {
                String message = "Illegal: Graal/Truffle use of Stream API: " + calleeName;
                int bci = srcPosition.getBCI();
                String trace = caller.asStackTraceElement(bci).toString();
                bb.getUnsupportedFeatures().addMessage(callerName, caller, message, trace);
                return false;
            }
        }
        return true;
    }

}
