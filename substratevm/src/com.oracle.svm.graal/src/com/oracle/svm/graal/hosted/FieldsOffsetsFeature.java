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

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.fieldvaluetransformer.FieldValueTransformerWithAvailability;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.GraalCompilerSupport;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;

import jdk.graal.compiler.core.common.FieldIntrospection;
import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;

/**
 * Graal uses unsafe memory accesses to access {@link Node}s and {@link LIRInstruction}s. The
 * offsets for these accesses are maintained in {@link Fields}, which are accessible from
 * meta-classes such as {@link NodeClass} and {@link LIRInstructionClass}. We do not want to replace
 * the whole meta-classes. Instead, we just replace the {@code long[]} arrays that hold the actual
 * offsets.
 */
public class FieldsOffsetsFeature implements Feature {

    public static class IterationMaskRecomputation implements FieldValueTransformerWithAvailability {
        @Override
        public boolean isAvailable() {
            return BuildPhaseProvider.isHostedUniverseBuilt();
        }

        @Override
        public Object transform(Object receiver, Object originalValue) {
            Edges edges = (Edges) receiver;
            FieldsOffsetsReplacement replacement = FieldsOffsetsFeature.getReplacements().get(edges.getOffsets());
            assert replacement.fields == edges;
            assert replacement.newOffsets != null : "Cannot access iteration mask before field offsets are assigned";
            return replacement.newIterationInitMask;
        }
    }

    static class FieldsOffsetsReplacement {
        protected final Fields fields;
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
                if (replacement.newOffsets != null) {
                    return replacement.newOffsets;
                }
            }
        }
        return source;
    }

    /* Invoked once for every class that is reachable in the native image. */
    private static void classReachabilityListener(DuringAnalysisAccess a, Class<?> newlyReachableClass) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        if (BuildPhaseProvider.isAnalysisFinished()) {
            throw VMError.shouldNotReachHere("New class reachable after analysis: " + newlyReachableClass);
        }

        if (Node.class.isAssignableFrom(newlyReachableClass) && newlyReachableClass != Node.class) {
            FieldsOffsetsFeature.<NodeClass<?>> registerClass(newlyReachableClass, GraalCompilerSupport.get().nodeClasses, NodeClass::get, false, access);

        } else if (LIRInstruction.class.isAssignableFrom(newlyReachableClass) && newlyReachableClass != LIRInstruction.class) {
            FieldsOffsetsFeature.<LIRInstructionClass<?>> registerClass(newlyReachableClass, GraalCompilerSupport.get().instructionClasses, LIRInstructionClass::get, true, access);

        }
    }

    private static <R extends FieldIntrospection<?>> void registerClass(Class<?> clazz, EconomicMap<Class<?>, R> registry,
                    Function<Class<?>, R> lookup, boolean excludeAbstract, DuringAnalysisAccessImpl access) {
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

            /* The partial evaluator allocates Node classes via Unsafe. */
            AnalysisType nodeType = config.getMetaAccess().lookupJavaType(nodeClass.getJavaClass());
            nodeType.registerInstantiatedCallback(unused -> config.registerAsUnsafeAllocated(nodeType));

            Fields dataFields = nodeClass.getData();
            registerFields(dataFields, config, "Graal node data field");

            Fields inputEdges = nodeClass.getInputEdges();
            registerFields(inputEdges, config, "Graal node input edge");

            Fields successorEdges = nodeClass.getSuccessorEdges();
            registerFields(successorEdges, config, "Graal node successor edge");

            /* Ensure field shortName is initialized, so that the instance is immutable. */
            nodeClass.shortName();

        } else {
            for (Fields fields : introspection.getAllFields()) {
                registerFields(fields, config, "Graal field");
            }
        }
    }

    private static void registerFields(Fields fields, BeforeAnalysisAccessImpl config, Object reason) {
        getReplacements().put(fields.getOffsets(), new FieldsOffsetsReplacement(fields));

        for (int i = 0; i < fields.getCount(); i++) {
            AnalysisField aField = config.getMetaAccess().lookupJavaField(findField(fields, i));
            aField.getType().registerAsReachable(aField);
            config.registerAsUnsafeAccessed(aField, reason);
        }
    }

    private static Field findField(Fields fields, int index) {
        return fields.getField(index);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        for (FieldsOffsetsReplacement replacement : getReplacements().values()) {
            Map.Entry<long[], Long> e = replacement.fields.recomputeOffsetsAndIterationMask(a::objectFieldOffset);
            replacement.newOffsets = e.getKey();
            replacement.newIterationInitMask = e.getValue();
        }
        ImageSingletons.lookup(FieldsOffsetsReplacements.class).newValuesAvailable = true;
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        access.registerAsImmutable(GraalCompilerSupport.get().nodeClasses.getValues(), o -> true);
        access.registerAsImmutable(GraalCompilerSupport.get().instructionClasses.getValues(), o -> true);
    }
}
