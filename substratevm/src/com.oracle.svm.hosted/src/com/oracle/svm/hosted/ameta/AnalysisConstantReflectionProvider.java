/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ameta;

import com.oracle.graal.pointsto.heap.value.ValueSupplier;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.UninitializedStaticFieldValueReader;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantReflectionProvider extends SharedConstantReflectionProvider {
    private final AnalysisUniverse universe;
    private final MetaAccessProvider metaAccess;
    private final ConstantReflectionProvider originalConstantReflection;
    private final ClassInitializationSupport classInitializationSupport;

    public AnalysisConstantReflectionProvider(AnalysisUniverse universe, MetaAccessProvider metaAccess,
                    ConstantReflectionProvider originalConstantReflection, ClassInitializationSupport classInitializationSupport) {
        this.universe = universe;
        this.metaAccess = metaAccess;
        this.originalConstantReflection = originalConstantReflection;
        this.classInitializationSupport = classInitializationSupport;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return EmptyMemoryAcessProvider.SINGLETON;
    }

    @Override
    public final JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        return readValue(metaAccess, (AnalysisField) field, receiver);
    }

    public JavaConstant readValue(MetaAccessProvider suppliedMetaAccess, AnalysisField field, JavaConstant receiver) {
        JavaConstant value;
        if (classInitializationSupport.shouldInitializeAtRuntime(field.getDeclaringClass())) {
            if (field.isStatic()) {
                value = readUninitializedStaticValue(field);
            } else {
                /*
                 * Classes that are initialized at run time must not have instances in the image
                 * heap. Invoking instance methods would miss the class initialization checks. Image
                 * generation should have been aborted earlier with a user-friendly message, this is
                 * just a safeguard.
                 */
                throw VMError.shouldNotReachHere("Cannot read instance field of a class that is initialized at run time: " + field.format("%H.%n"));
            }
        } else {
            value = universe.lookup(ReadableJavaField.readFieldValue(suppliedMetaAccess, originalConstantReflection, field.wrapped, universe.toHosted(receiver)));
        }

        return interceptValue(field, value);
    }

    /** Read the field value and wrap it in a value supplier without performing any replacements. */
    public ValueSupplier<JavaConstant> readHostedFieldValue(AnalysisField field, HostedMetaAccess hMetaAccess, JavaConstant receiver) {
        if (classInitializationSupport.shouldInitializeAtRuntime(field.getDeclaringClass())) {
            if (field.isStatic()) {
                return ValueSupplier.eagerValue(readUninitializedStaticValue(field));
            } else {
                /*
                 * Classes that are initialized at run time must not have instances in the image
                 * heap. Invoking instance methods would miss the class initialization checks. Image
                 * generation should have been aborted earlier with a user-friendly message, this is
                 * just a safeguard.
                 */
                throw VMError.shouldNotReachHere("Cannot read instance field of a class that is initialized at run time: " + field.format("%H.%n"));
            }
        }

        if (field.wrapped instanceof ReadableJavaField) {
            ReadableJavaField readableField = (ReadableJavaField) field.wrapped;
            if (readableField.isValueAvailableBeforeAnalysis()) {
                /* Materialize and return the value. */
                return ValueSupplier.eagerValue(universe.lookup(readableField.readValue(metaAccess, receiver)));
            } else {
                /*
                 * Return a lazy value. This applies to RecomputeFieldValue.Kind.FieldOffset and
                 * RecomputeFieldValue.Kind.Custom. The value becomes available during hosted
                 * universe building and is installed by calling
                 * ComputedValueField.processSubstrate() or by ComputedValueField.readValue().
                 * Attempts to materialize the value earlier will result in an error.
                 */
                return ValueSupplier.lazyValue(() -> universe.lookup(readableField.readValue(hMetaAccess, receiver)),
                                readableField::isValueAvailable);
            }
        }
        return ValueSupplier.eagerValue(universe.lookup(originalConstantReflection.readFieldValue(field.wrapped, receiver)));
    }

    public JavaConstant readUninitializedStaticValue(AnalysisField field) {
        assert classInitializationSupport.shouldInitializeAtRuntime(field.getDeclaringClass());
        return UninitializedStaticFieldValueReader.readUninitializedStaticValue(field, val -> SubstrateObjectConstant.forObject(val));
    }

    public JavaConstant interceptValue(AnalysisField field, JavaConstant value) {
        JavaConstant result = value;
        if (result != null) {
            result = filterInjectedAccessor(field, result);
            result = replaceObject(result);
            result = interceptAssertionStatus(field, result);
            result = interceptWordType(field, result);
        }
        return result;
    }

    private static JavaConstant filterInjectedAccessor(AnalysisField field, JavaConstant value) {
        if (field.getAnnotation(InjectAccessors.class) != null) {
            /*
             * Fields whose accesses are intercepted by injected accessors are not actually present
             * in the image. Ideally they should never be read, but there are corner cases where
             * this happens. We intercept the value and return 0 / null.
             */
            assert !field.isAccessed();
            return JavaConstant.defaultForKind(value.getJavaKind());
        }
        return value;
    }

    /**
     * Run all registered object replacers.
     */
    private JavaConstant replaceObject(JavaConstant value) {
        if (value == JavaConstant.NULL_POINTER) {
            return JavaConstant.NULL_POINTER;
        }
        if (value.getJavaKind() == JavaKind.Object) {
            Object oldObject = universe.getSnippetReflection().asObject(Object.class, value);
            Object newObject = universe.replaceObject(oldObject);
            if (newObject != oldObject) {
                return universe.getSnippetReflection().forObject(newObject);
            }
        }
        return value;
    }

    /**
     * Intercept assertion status: the value of the field during image generation does not matter at
     * all (because it is the hosted assertion status), we instead return the appropriate runtime
     * assertion status. Field loads are also intrinsified early in
     * {@link com.oracle.svm.hosted.phases.EarlyConstantFoldLoadFieldPlugin}, but we could still see
     * such a field here if user code, e.g., accesses it via reflection.
     */
    private static JavaConstant interceptAssertionStatus(AnalysisField field, JavaConstant value) {
        if (field.isStatic() && field.isSynthetic() && field.getName().startsWith("$assertionsDisabled")) {
            Class<?> clazz = field.getDeclaringClass().getJavaClass();
            boolean assertionsEnabled = RuntimeAssertionsSupport.singleton().desiredAssertionStatus(clazz);
            return JavaConstant.forBoolean(!assertionsEnabled);
        }
        return value;
    }

    /**
     * Intercept {@link Word} types. They are boxed objects in the hosted world, but primitive
     * values in the runtime world.
     */
    private JavaConstant interceptWordType(AnalysisField field, JavaConstant value) {
        if (value.getJavaKind() == JavaKind.Object) {
            Object originalObject = universe.getSnippetReflection().asObject(Object.class, value);
            if (universe.hostVM().isRelocatedPointer(originalObject)) {
                /*
                 * Such pointers are subject to relocation therefore we don't know their values yet.
                 * Therefore there should not be a relocated pointer constant in a function which is
                 * compiled. RelocatedPointers are only allowed in non-constant fields. The caller
                 * of readValue is responsible of handling the returned value correctly.
                 */
                return value;
            } else if (originalObject instanceof WordBase) {
                return JavaConstant.forIntegerKind(universe.getWordKind(), ((WordBase) originalObject).rawValue());
            } else if (originalObject == null && field.getType().isWordType()) {
                return JavaConstant.forIntegerKind(universe.getWordKind(), 0);
            }
        }
        return value;
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        if (constant instanceof SubstrateObjectConstant) {
            Object obj = SubstrateObjectConstant.asObject(constant);
            if (obj instanceof DynamicHub) {
                return getHostVM().lookupType((DynamicHub) obj);
            } else if (obj instanceof Class) {
                // TODO do we need to make sure the hub is scanned?
                return metaAccess.lookupJavaType((Class<?>) obj);
            }
        }
        return null;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        DynamicHub dynamicHub = getHostVM().dynamicHub(type);
        registerAsReachable(getHostVM(), dynamicHub);
        assert dynamicHub != null : type.toClassName() + " has a null dynamicHub.";
        return SubstrateObjectConstant.forObject(dynamicHub);
    }

    protected static void registerAsReachable(SVMHost hostVM, DynamicHub dynamicHub) {
        assert dynamicHub != null;
        /* Make sure that the DynamicHub of this type ends up in the native image. */
        AnalysisType valueType = hostVM.lookupType(dynamicHub);
        if (!valueType.isReachable() && BuildPhaseProvider.isAnalysisFinished()) {
            throw VMError.shouldNotReachHere("Registering type as reachable after analysis: " + valueType);
        }
        valueType.registerAsReachable();
    }

    private SVMHost getHostVM() {
        return (SVMHost) universe.hostVM();
    }
}
