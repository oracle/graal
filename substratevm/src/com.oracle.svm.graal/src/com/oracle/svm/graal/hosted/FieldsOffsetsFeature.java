/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hosted;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.compiler.core.common.FieldIntrospection;
import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.CompositeValue;
import org.graalvm.compiler.lir.CompositeValueClass;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.api.DefaultUnsafePartition;
import com.oracle.graal.pointsto.api.UnsafePartitionKind;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalSupport;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.GraalEdgeUnsafePartition;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.misc.Unsafe;

/**
 * Graal uses unsafe memory accesses to access {@link Node}s and {@link LIRInstruction}s. The
 * offsets for these accesses are maintained in {@link Fields}, which are accessible from
 * meta-classes such as {@link NodeClass} and {@link LIRInstructionClass}. We do not want to replace
 * the whole meta-classes. Instead, we just replace the {@code long[]} arrays that hold the actual
 * offsets.
 */
public class FieldsOffsetsFeature implements Feature {

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    abstract static class IterationMaskRecomputation implements RecomputeFieldValue.CustomFieldValueComputer {
        @Override
        public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
            Edges edges = getEdges((NodeClass<?>) receiver);
            FieldsOffsetsReplacement replacement = FieldsOffsetsFeature.getReplacements().get(edges.getOffsets());
            assert replacement.fields == edges;
            assert replacement.newValuesAvailable : "Cannot access iteration mask before field offsets are assigned";
            return replacement.newIterationInitMask;
        }

        protected abstract Edges getEdges(NodeClass<?> nodeClass);
    }

    public static class InputsIterationMaskRecomputation extends IterationMaskRecomputation {
        @Override
        protected Edges getEdges(NodeClass<?> nodeClass) {
            return nodeClass.getInputEdges();
        }
    }

    public static class SuccessorsIterationMaskRecomputation extends IterationMaskRecomputation {
        @Override
        protected Edges getEdges(NodeClass<?> nodeClass) {
            return nodeClass.getSuccessorEdges();
        }
    }

    static class FieldsOffsetsReplacement {
        protected final Fields fields;
        protected boolean newValuesAvailable;
        protected long[] newOffsets;
        protected long newIterationInitMask;

        protected FieldsOffsetsReplacement(Fields fields) {
            this.fields = fields;
        }
    }

    static class FieldsOffsetsReplacements {
        protected final Map<long[], FieldsOffsetsReplacement> replacements = new IdentityHashMap<>();
        protected boolean newValuesAvailable;
    }

    protected static Map<long[], FieldsOffsetsReplacement> getReplacements() {
        return ImageSingletons.lookup(FieldsOffsetsReplacements.class).replacements;
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;

        ImageSingletons.add(FieldsOffsetsReplacements.class, new FieldsOffsetsReplacements());
        access.registerObjectReplacer(FieldsOffsetsFeature::replaceFieldsOffsets);
        access.registerClassReachabilityListener(FieldsOffsetsFeature::classReachabilityListener);
    }

    private static Object replaceFieldsOffsets(Object source) {
        if (source instanceof Fields) {
            /*
             * All instances of Fields must have been registered before, otherwise we miss the
             * substitution of its offsets array.
             */
            assert !ImageSingletons.lookup(FieldsOffsetsReplacements.class).newValuesAvailable || getReplacements().containsKey(((Fields) source).getOffsets()) : source;

        } else if (source instanceof long[]) {
            FieldsOffsetsReplacement replacement = getReplacements().get(source);
            if (replacement != null) {
                assert source == replacement.fields.getOffsets();

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
    private static void classReachabilityListener(DuringAnalysisAccess a, Class<?> newlyReachableClass) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        if (Node.class.isAssignableFrom(newlyReachableClass) && newlyReachableClass != Node.class) {
            FieldsOffsetsFeature.<NodeClass<?>> registerClass(newlyReachableClass, GraalSupport.get().nodeClasses, NodeClass::get, false, access);

        } else if (LIRInstruction.class.isAssignableFrom(newlyReachableClass) && newlyReachableClass != LIRInstruction.class) {
            FieldsOffsetsFeature.<LIRInstructionClass<?>> registerClass(newlyReachableClass, GraalSupport.get().instructionClasses, LIRInstructionClass::get, true, access);

        } else if (CompositeValue.class.isAssignableFrom(newlyReachableClass) && newlyReachableClass != CompositeValue.class) {
            FieldsOffsetsFeature.<CompositeValueClass<?>> registerClass(newlyReachableClass, GraalSupport.get().compositeValueClasses, CompositeValueClass::get, true, access);
        }
    }

    private static <R extends FieldIntrospection<?>> void registerClass(Class<?> clazz, Map<Class<?>, R> registry, Function<Class<?>, R> lookup, boolean excludeAbstract,
                    DuringAnalysisAccessImpl access) {
        assert !registry.containsKey(clazz);

        if (!excludeAbstract || !Modifier.isAbstract(clazz.getModifiers())) {
            R nodeClass = lookup.apply(clazz);
            registry.put(clazz, nodeClass);
            registerFields(nodeClass, access);

            access.requireAnalysisIteration();
        }
    }

    private static void registerFields(FieldIntrospection<?> introspection, BeforeAnalysisAccessImpl config) {
        if (introspection instanceof NodeClass<?>) {
            NodeClass<?> nodeClass = (NodeClass<?>) introspection;

            Fields dataFields = nodeClass.getData();
            registerFields(dataFields, DefaultUnsafePartition.get(), config);

            Fields inputEdges = nodeClass.getInputEdges();
            registerFields(inputEdges, GraalEdgeUnsafePartition.get(), config);

            Fields successorEdges = nodeClass.getSuccessorEdges();
            registerFields(successorEdges, GraalEdgeUnsafePartition.get(), config);

            /* Ensure field shortName is initialized, so that the instance is immutable. */
            nodeClass.shortName();

        } else {
            for (Fields fields : introspection.getAllFields()) {
                registerFields(fields, DefaultUnsafePartition.get(), config);
            }
        }
    }

    private static void registerFields(Fields fields, UnsafePartitionKind partitionKind, BeforeAnalysisAccessImpl config) {
        getReplacements().put(fields.getOffsets(), new FieldsOffsetsReplacement(fields));

        for (int i = 0; i < fields.getCount(); i++) {
            config.registerAsUnsafeAccessed(config.getMetaAccess().lookupJavaField(findField(fields, i)), partitionKind);
        }
    }

    private static Field findField(Fields fields, int index) {
        try {
            return fields.getDeclaringClass(index).getDeclaredField(fields.getName(index));
        } catch (NoSuchFieldException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        CompilationAccessImpl config = (CompilationAccessImpl) a;
        HostedMetaAccess hMetaAccess = config.getMetaAccess();

        for (FieldsOffsetsReplacement replacement : getReplacements().values()) {
            Fields fields = replacement.fields;
            long[] newOffsets = new long[fields.getCount()];
            for (int i = 0; i < newOffsets.length; i++) {
                Field field = findField(fields, i);
                assert UNSAFE.objectFieldOffset(field) == fields.getOffsets()[i];
                newOffsets[i] = hMetaAccess.lookupJavaField(field).getLocation();
            }
            replacement.newOffsets = newOffsets;

            if (fields instanceof Edges) {
                Edges edges = (Edges) fields;
                replacement.newIterationInitMask = NodeClass.computeIterationMask(edges.type(), edges.getDirectCount(), newOffsets);
            }

            replacement.newValuesAvailable = true;
        }
        ImageSingletons.lookup(FieldsOffsetsReplacements.class).newValuesAvailable = true;
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        access.registerAsImmutable(GraalSupport.get().nodeClasses.values(), o -> true);
        access.registerAsImmutable(GraalSupport.get().instructionClasses.values(), o -> true);
    }
}
