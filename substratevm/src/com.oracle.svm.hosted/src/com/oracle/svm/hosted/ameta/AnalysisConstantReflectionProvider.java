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

import java.lang.reflect.Modifier;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantReflectionProvider extends SharedConstantReflectionProvider {
    private final AnalysisUniverse universe;
    private final ConstantReflectionProvider originalConstantReflection;
    private final ClassInitializationSupport classInitializationSupport;

    public AnalysisConstantReflectionProvider(AnalysisUniverse universe, ConstantReflectionProvider originalConstantReflection, ClassInitializationSupport classInitializationSupport) {
        this.universe = universe;
        this.originalConstantReflection = originalConstantReflection;
        this.classInitializationSupport = classInitializationSupport;
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return EmptyMemoryAcessProvider.SINGLETON;
    }

    @Override
    public final JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        if (field instanceof AnalysisField) {
            return readValue((AnalysisField) field, receiver);
        } else {
            return super.readFieldValue(field, receiver);
        }
    }

    public JavaConstant readValue(AnalysisField field, JavaConstant receiver) {
        if (classInitializationSupport.shouldInitializeAtRuntime(field.getDeclaringClass())) {
            if (field.isStatic()) {
                /*
                 * Static fields of classes that are initialized at run time have the default
                 * (uninitialized) value in the image heap.
                 */
                return JavaConstant.defaultForKind(field.getStorageKind());
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

        return interceptValue(field, universe.lookup(ReadableJavaField.readFieldValue(originalConstantReflection, field.wrapped, universe.toHosted(receiver))));
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
     * assertion status.
     */
    private static JavaConstant interceptAssertionStatus(AnalysisField field, JavaConstant value) {
        if (Modifier.isStatic(field.getModifiers()) && field.isSynthetic() && field.getName().startsWith("$assertionsDisabled")) {
            String unsubstitutedName = field.wrapped.getDeclaringClass().toJavaName();
            if (unsubstitutedName.startsWith("java.") || unsubstitutedName.startsWith("javax.") || unsubstitutedName.startsWith("sun.")) {
                /*
                 * This is a JDK class, so not likely an assertion that we care about, and some JDK
                 * assertions do not hold in Substrate VM. Note that we match the original class
                 * (before substitution) here to keep assertions in substitution code.
                 */
                return JavaConstant.TRUE;
            }
            /* For the user-provided filter, match substitution target names to avoid confusion. */
            String className = field.getDeclaringClass().toJavaName();
            return JavaConstant.forBoolean(!SubstrateOptions.getRuntimeAssertionsForClass(className));
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
                throw VMError.shouldNotReachHere("Must not have java.lang.Class object: " + obj);
            }
        }
        return null;
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        DynamicHub dynamicHub = getHostVM().dynamicHub(type);
        assert dynamicHub != null : type.toClassName() + " has a null dynamicHub.";
        registerHub(getHostVM(), dynamicHub);
        return SubstrateObjectConstant.forObject(dynamicHub);
    }

    private SVMHost getHostVM() {
        return (SVMHost) universe.hostVM();
    }

    protected static void registerHub(SVMHost hostVM, DynamicHub dynamicHub) {
        assert dynamicHub != null;
        /* Make sure that the DynamicHub of this type ends up in the native image. */
        AnalysisType valueType = hostVM.lookupType(dynamicHub);
        if (!valueType.isInTypeCheck()) {
            valueType.registerAsInTypeCheck();
        }
    }
}
