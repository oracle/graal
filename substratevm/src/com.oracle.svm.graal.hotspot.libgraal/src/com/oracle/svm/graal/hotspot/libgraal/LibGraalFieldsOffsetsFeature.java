/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import jdk.graal.compiler.hotspot.libgraal.BuildTime;
import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

/**
 * Graal uses unsafe memory accesses to access {@code Node}s and {@code LIRInstruction}s. The
 * offsets for these accesses are maintained in {@code Fields}, which are accessible from
 * meta-classes such as {@code NodeClass} and {@code LIRInstructionClass}. We do not want to replace
 * the whole meta-classes. Instead, we just replace the {@code long[]} arrays that hold the actual
 * offsets.
 */
public final class LibGraalFieldsOffsetsFeature implements InternalFeature {

    private final MethodHandles.Lookup mhl = MethodHandles.lookup();
    private LibGraalFeature libGraalFeature;

    private Class<?> fieldsClass;
    private Class<?> edgesClass;
    private Class<?> edgesTypeClass;
    private MethodHandle fieldsClassGetOffsetsMethod;
    private MethodHandle fieldsClassGetCountMethod;
    private MethodHandle fieldsClassGetDeclaringClassMethod;
    private MethodHandle fieldsClassGetNameMethod;

    private MethodHandle edgesClassTypeMethod;
    private MethodHandle edgesClassGetDirectCountMethod;

    private Class<?> nodeClass;
    private Class<?> nodeClassClass;
    private Class<?> inputEdgesClass;
    private Class<?> successorEdgesClass;
    private MethodHandle nodeClassClassGetMethod;
    private MethodHandle nodeClassClassGetInputEdgesMethod;
    private MethodHandle nodeClassClassGetSuccessorEdgesMethod;
    private MethodHandle nodeClassClassGetShortNameMethod;
    private MethodHandle nodeClassClassComputeIterationMaskMethod;

    private Class<?> fieldIntrospectionClass;
    private MethodHandle fieldIntrospectionClassGetDataMethod;
    private MethodHandle fieldIntrospectionClassGetAllFieldsMethod;
    private MethodHandle fieldIntrospectionClassGetClazzMethod;
    private Class<?> lirInstructionClass;
    private Class<?> lirInstructionClassClass;
    private MethodHandle lirInstructionClassClassGetMethod;

    private static class FieldsOffsetsReplacement {
        protected final Object fields;
        protected boolean newValuesAvailable;
        protected long[] newOffsets;
        protected long newIterationInitMask;

        protected FieldsOffsetsReplacement(Object fields) {
            this.fields = fields;
        }
    }

    static class FieldsOffsetsReplacements {
        private final Map<long[], FieldsOffsetsReplacement> replacements = new IdentityHashMap<>();
        private boolean sealed;
    }

    private static Map<long[], FieldsOffsetsReplacement> getReplacements() {
        return ImageSingletons.lookup(FieldsOffsetsReplacements.class).replacements;
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        libGraalFeature = ImageSingletons.lookup(LibGraalFeature.class);

        fieldsClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.core.common.Fields");
        edgesClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.graph.Edges");
        edgesTypeClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.graph.Edges$Type");
        nodeClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.graph.Node");
        nodeClassClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.graph.NodeClass");
        lirInstructionClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.lir.LIRInstruction");
        lirInstructionClassClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.lir.LIRInstructionClass");
        fieldIntrospectionClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.core.common.FieldIntrospection");
        inputEdgesClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.graph.InputEdges");
        successorEdgesClass = libGraalFeature.loadClassOrFail("jdk.graal.compiler.graph.SuccessorEdges");

        try {
            fieldsClassGetOffsetsMethod = mhl.findVirtual(fieldsClass, "getOffsets", MethodType.methodType(long[].class));
            fieldsClassGetCountMethod = mhl.findVirtual(fieldsClass, "getCount", MethodType.methodType(int.class));
            fieldsClassGetDeclaringClassMethod = mhl.findVirtual(fieldsClass, "getDeclaringClass", MethodType.methodType(Class.class, int.class));
            fieldsClassGetNameMethod = mhl.findVirtual(fieldsClass, "getName", MethodType.methodType(String.class, int.class));

            edgesClassTypeMethod = mhl.findVirtual(edgesClass, "type", MethodType.methodType(edgesTypeClass));
            edgesClassGetDirectCountMethod = mhl.findVirtual(edgesClass, "getDirectCount", MethodType.methodType(int.class));

            nodeClassClassGetMethod = mhl.findStatic(nodeClassClass, "get", MethodType.methodType(nodeClassClass, Class.class));
            nodeClassClassGetInputEdgesMethod = mhl.findVirtual(nodeClassClass, "getInputEdges", MethodType.methodType(inputEdgesClass));
            nodeClassClassGetSuccessorEdgesMethod = mhl.findVirtual(nodeClassClass, "getSuccessorEdges", MethodType.methodType(successorEdgesClass));
            nodeClassClassGetShortNameMethod = mhl.findVirtual(nodeClassClass, "shortName", MethodType.methodType(String.class));
            nodeClassClassGetShortNameMethod = mhl.findVirtual(nodeClassClass, "shortName", MethodType.methodType(String.class));
            nodeClassClassComputeIterationMaskMethod = mhl.findStatic(nodeClassClass, "computeIterationMask", MethodType.methodType(long.class, edgesTypeClass, int.class, long[].class));

            lirInstructionClassClassGetMethod = mhl.findStatic(lirInstructionClassClass, "get", MethodType.methodType(lirInstructionClassClass, Class.class));

            fieldIntrospectionClassGetDataMethod = mhl.findVirtual(fieldIntrospectionClass, "getData", MethodType.methodType(fieldsClass));
            fieldIntrospectionClassGetAllFieldsMethod = mhl.findVirtual(fieldIntrospectionClass, "getAllFields", MethodType.methodType(fieldsClass.arrayType()));
            fieldIntrospectionClassGetClazzMethod = mhl.findVirtual(fieldIntrospectionClass, "getClazz", MethodType.methodType(Class.class));

        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }

        ModuleSupport.accessModuleByClass(ModuleSupport.Access.EXPORT, LibGraalFieldsOffsetsFeature.class, InternalFeature.class);
        ImageSingletons.add(FieldsOffsetsReplacements.class, new FieldsOffsetsReplacements());
        access.registerObjectReplacer(this::replaceFieldsOffsets);
        access.registerClassReachabilityListener(this::classReachabilityListener);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        MethodHandle getInputEdgesOffsets;
        MethodHandle getSuccessorEdgesOffsets;
        var buildTimeClass = libGraalFeature.loadClassOrFail(BuildTime.class);
        try {
            MethodType offsetAccessorSignature = MethodType.methodType(long[].class, Object.class);
            getInputEdgesOffsets = mhl.findStatic(buildTimeClass, "getInputEdgesOffsets", offsetAccessorSignature);
            getSuccessorEdgesOffsets = mhl.findStatic(buildTimeClass, "getSuccessorEdgesOffsets", offsetAccessorSignature);
        } catch (ReflectiveOperationException e) {
            throw VMError.shouldNotReachHere(e);
        }

        access.registerFieldValueTransformer(ReflectionUtil.lookupField(nodeClassClass, "inputsIteration"),
                        new IterationMaskRecomputation(getInputEdgesOffsets));
        access.registerFieldValueTransformer(ReflectionUtil.lookupField(nodeClassClass, "successorIteration"),
                        new IterationMaskRecomputation(getSuccessorEdgesOffsets));
    }

    private static class IterationMaskRecomputation implements FieldValueTransformerWithAvailability {

        private final MethodHandle offsetsFromReceiver;

        IterationMaskRecomputation(MethodHandle offsetsFromReceiver) {
            this.offsetsFromReceiver = offsetsFromReceiver;
        }

        @Override
        public boolean isAvailable() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            FieldsOffsetsReplacement replacement;
            try {
                long[] offsetsFromEdges = (long[]) offsetsFromReceiver.invoke(receiver);
                replacement = LibGraalFieldsOffsetsFeature.getReplacements().get(offsetsFromEdges);
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(e);
            }
            assert replacement.newValuesAvailable : "Cannot access iteration mask before field offsets are assigned";
            return replacement.newIterationInitMask;
        }
    }

    private Object replaceFieldsOffsets(Object source) {
        if (fieldsClass.isInstance(source)) {
            /*
             * All instances of Fields must have been registered before, otherwise we miss the
             * substitution of its offsets array.
             */
            assert !ImageSingletons.lookup(FieldsOffsetsReplacements.class).sealed || getReplacements().containsKey(getOffsetsFromFields(source)) : source;

        } else if (source instanceof long[]) {
            FieldsOffsetsReplacement replacement = getReplacements().get(source);
            if (replacement != null) {
                assert source == getOffsetsFromFields(replacement.fields);

                /*
                 * We can only compute the new offsets after static analysis, i.e., after the object
                 * layout is done and run-time field offsets are available. Until then, we return
                 * the hosted offsets so that we have a return value. The actual offsets do not
                 * matter at this point.
                 */
                if (replacement.newValuesAvailable) {
                    return replacement.newOffsets;
                }
            }
        }
        return source;
    }

    /* Invoked once for every class that is reachable in the native image. */
    private void classReachabilityListener(DuringAnalysisAccess a, Class<?> newlyReachableClass) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        if (BuildPhaseProvider.isAnalysisFinished()) {
            throw VMError.shouldNotReachHere("New class reachable after analysis: " + newlyReachableClass);
        }

        if (!newlyReachableClass.equals(nodeClass) && nodeClass.isAssignableFrom(newlyReachableClass)) {
            registerClass(newlyReachableClass, LibGraalCompilerSupport.get().nodeClasses, this::getNodeClassFromNode, false, access);
        } else if (!newlyReachableClass.equals(lirInstructionClass) && lirInstructionClass.isAssignableFrom(newlyReachableClass)) {
            registerClass(newlyReachableClass, LibGraalCompilerSupport.get().instructionClasses, this::getLIRInstructionClassFromLIRInstruction, true, access);
        }
    }

    private void registerClass(Class<?> clazz, EconomicMap<Class<?>, Object> registry,
                    Function<Class<?>, Object> lookup, boolean excludeAbstract, DuringAnalysisAccessImpl access) {
        assert !registry.containsKey(clazz);

        if (!excludeAbstract || !Modifier.isAbstract(clazz.getModifiers())) {
            Object nodeClazz = lookup.apply(clazz);
            registry.put(clazz, nodeClazz);
            registerFields(nodeClazz, access);

            access.requireAnalysisIteration();
        }
    }

    private void registerFields(Object introspection, BeforeAnalysisAccessImpl config) {
        if (nodeClassClass.isInstance(introspection)) {

            /* The partial evaluator allocates Node classes via Unsafe. */
            AnalysisType nodeType = config.getMetaAccess().lookupJavaType(getClazzFromFieldIntrospection(introspection));
            nodeType.registerInstantiatedCallback(unused -> nodeType.registerAsUnsafeAllocated("Graal node class"));

            Object dataFields = getDataFromFieldIntrospection(introspection);
            registerFields(dataFields, config, "Graal node data field");

            Object inputEdges = getInputEdgesFromNodeClass(introspection);
            registerFields(inputEdges, config, "Graal node input edge");

            Object successorEdges = getSuccessorEdgesFromNodeClass(introspection);
            registerFields(successorEdges, config, "Graal node successor edge");

            /* Ensure field shortName is initialized, so that the instance is immutable. */
            invokeShortName(introspection);

        } else {
            assert fieldIntrospectionClass.isInstance(introspection);
            for (Object fields : getAllFieldsFromFieldIntrospection(introspection)) {
                registerFields(fields, config, "Graal field");
            }
        }
    }

    private void registerFields(Object fields, BeforeAnalysisAccessImpl config, Object reason) {
        getReplacements().put(getOffsetsFromFields(fields), new FieldsOffsetsReplacement(fields));

        for (int i = 0; i < getCountFromFields(fields); i++) {
            AnalysisField aField = config.getMetaAccess().lookupJavaField(findField(fields, i));
            aField.getType().registerAsReachable(aField);
            config.registerAsUnsafeAccessed(aField, reason);
        }
    }

    private Field findField(Object fields, int index) {
        try {
            return getDeclaringClassFromFields(fields, index).getDeclaredField(getNameFromFields(fields, index));
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;
        HostedMetaAccess hMetaAccess = config.getMetaAccess();

        getReplacements().forEach((originalOffsets, replacement) -> {
            Object fields = replacement.fields;
            long[] newOffsets = new long[getCountFromFields(fields)];
            for (int i = 0; i < newOffsets.length; i++) {
                Field field = findField(fields, i);
                assert Unsafe.getUnsafe().objectFieldOffset(field) == originalOffsets[i];
                newOffsets[i] = hMetaAccess.lookupJavaField(field).getLocation();
            }
            replacement.newOffsets = newOffsets;

            if (edgesClass.isInstance(fields)) {
                Object edges = edgesClass.cast(fields);
                replacement.newIterationInitMask = nodeClassComputeIterationMask(typeFromEdges(edges), getDirectCountFromEdges(edges), newOffsets);
            }

            replacement.newValuesAvailable = true;
        });

        ImageSingletons.lookup(FieldsOffsetsReplacements.class).sealed = true;
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        access.registerAsImmutable(LibGraalCompilerSupport.get().nodeClasses.getValues(), o -> true);
        access.registerAsImmutable(LibGraalCompilerSupport.get().instructionClasses.getValues(), o -> true);
    }

    private long[] getOffsetsFromFields(Object fields) {
        try {
            return (long[]) fieldsClassGetOffsetsMethod.invoke(fields);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private int getCountFromFields(Object fields) {
        try {
            return (int) fieldsClassGetCountMethod.invoke(fields);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Class<?> getDeclaringClassFromFields(Object fields, int index) {
        try {
            return (Class<?>) fieldsClassGetDeclaringClassMethod.invoke(fields, index);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private String getNameFromFields(Object fields, int index) {
        try {
            return (String) fieldsClassGetNameMethod.invoke(fields, index);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object typeFromEdges(Object edges) {
        try {
            assert edgesClass.isInstance(edges);
            Object edgesType = edgesClassTypeMethod.invoke(edges);
            return edgesTypeClass.cast(edgesType);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private int getDirectCountFromEdges(Object edges) {
        try {
            assert edgesClass.isInstance(edges);
            return (int) edgesClassGetDirectCountMethod.invoke(edges);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object getDataFromFieldIntrospection(Object fieldIntrospection) {
        try {
            assert fieldIntrospectionClass.isInstance(fieldIntrospection);
            Object fields = fieldIntrospectionClassGetDataMethod.invoke(fieldIntrospection);
            return fieldsClass.cast(fields);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Class<?> getClazzFromFieldIntrospection(Object fieldIntrospection) {
        try {
            assert fieldIntrospectionClass.isInstance(fieldIntrospection);
            Object clazz = fieldIntrospectionClassGetClazzMethod.invoke(fieldIntrospection);
            return (Class<?>) clazz;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object[] getAllFieldsFromFieldIntrospection(Object fieldIntrospection) {
        try {
            assert fieldIntrospectionClass.isInstance(fieldIntrospection);
            Object allFields = fieldIntrospectionClassGetAllFieldsMethod.invoke(fieldIntrospection);
            return (Object[]) fieldsClass.arrayType().cast(allFields);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object getNodeClassFromNode(Class<?> clazz) {
        try {
            assert nodeClass.isAssignableFrom(clazz);
            Object nodeClassInstance = nodeClassClassGetMethod.invoke(clazz);
            assert nodeClassClass.isInstance(nodeClassInstance);
            return nodeClassInstance;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object getInputEdgesFromNodeClass(Object nodeClazz) {
        try {
            assert nodeClassClass.isInstance(nodeClazz);
            Object inputEdges = nodeClassClassGetInputEdgesMethod.invoke(nodeClazz);
            return inputEdgesClass.cast(inputEdges);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object getSuccessorEdgesFromNodeClass(Object nodeClazz) {
        try {
            assert nodeClassClass.isInstance(nodeClazz);
            Object successorEdges = nodeClassClassGetSuccessorEdgesMethod.invoke(nodeClazz);
            return successorEdgesClass.cast(successorEdges);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private void invokeShortName(Object nodeClazz) {
        try {
            assert nodeClassClass.isInstance(nodeClazz);
            nodeClassClassGetShortNameMethod.invoke(nodeClazz);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private long nodeClassComputeIterationMask(Object edgesType, int directCount, long[] offsets) {
        try {
            assert edgesTypeClass.isInstance(edgesType);
            return (long) nodeClassClassComputeIterationMaskMethod.invoke(edgesType, directCount, offsets);
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Object getLIRInstructionClassFromLIRInstruction(Class<?> clazz) {
        try {
            assert lirInstructionClass.isAssignableFrom(clazz);
            Object nodeClassInstance = lirInstructionClassClassGetMethod.invoke(clazz);
            assert lirInstructionClassClass.isInstance(nodeClassInstance);
            return nodeClassInstance;
        } catch (Throwable e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
