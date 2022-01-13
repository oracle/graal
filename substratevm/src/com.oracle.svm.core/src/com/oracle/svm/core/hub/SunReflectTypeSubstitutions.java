/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Type;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.CustomFieldValueComputer;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.reflect.generics.reflectiveObjects.TypeVariableImpl;
import sun.reflect.generics.reflectiveObjects.WildcardTypeImpl;
import sun.reflect.generics.tree.FieldTypeSignature;

/**
 * The bounds in TypeVariableImpl and WildcardTypeImpl are lazily computed. We capture the value and
 * cache it in the native image heap.
 */

@TargetClass(sun.reflect.generics.reflectiveObjects.TypeVariableImpl.class)
final class Target_sun_reflect_generics_reflectiveObjects_TypeVariableImpl {

    @Alias private String name;

    /* Cache the bounds value. */

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeVariableBoundsComputer.class) //
    private Object[] bounds;

    @Alias GenericDeclaration genericDeclaration;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeVariableAnnotationsComputer.class) //
    Annotation[] annotations;

    @Inject @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeVariableAnnotatedBoundsComputer.class) //
    AnnotatedType[] annotatedBounds;

    @Substitute
    public AnnotatedType[] getAnnotatedBounds() {
        return annotatedBounds;
    }

    /** Reason for substitutions: disable access checks in original method. */
    @Substitute
    public GenericDeclaration getGenericDeclaration() {
        return genericDeclaration;
    }

    @Substitute
    public Annotation[] getAnnotations() {
        return annotations;
    }
}

final class Util_sun_reflect_generics_reflectiveObjects_TypeVariableImpl {

    /** Emulate the Java class hierarchy. */
    static Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator asLazyReflectiveObjectGenerator(
                    Target_sun_reflect_generics_reflectiveObjects_TypeVariableImpl typeVariableImpl) {
        return SubstrateUtil.cast(typeVariableImpl, Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator.class);
    }

    /** Emulate virtual dispatch. */
    static Type[] reifyBounds(Target_sun_reflect_generics_reflectiveObjects_TypeVariableImpl typeVariableImpl, FieldTypeSignature[] boundASTs) {
        return asLazyReflectiveObjectGenerator(typeVariableImpl).reifyBounds(boundASTs);
    }
}

@TargetClass(className = "sun.reflect.generics.reflectiveObjects.LazyReflectiveObjectGenerator")
final class Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator {

    @Alias
    native Type[] reifyBounds(FieldTypeSignature[] boundASTs);
}

class TypeVariableBoundsComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public RecomputeFieldValue.ValueAvailability valueAvailability() {
        return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
    }

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return GuardedBoundsAccess.getBounds((TypeVariableImpl<?>) receiver);
    }
}

class TypeVariableAnnotatedBoundsComputer implements CustomFieldValueComputer {
    @Override
    public RecomputeFieldValue.ValueAvailability valueAvailability() {
        return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
    }

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return GuardedBoundsAccess.getAnnotatedBounds((TypeVariableImpl<?>) receiver);
    }
}

class TypeVariableAnnotationsComputer implements CustomFieldValueComputer {
    @Override
    public RecomputeFieldValue.ValueAvailability valueAvailability() {
        return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
    }

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return GuardedAnnotationAccess.getAnnotations((TypeVariableImpl<?>) receiver);
    }
}

@TargetClass(sun.reflect.generics.reflectiveObjects.WildcardTypeImpl.class)
final class Target_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl {

    /* Cache the upperBounds value. */
    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplUpperBoundsComputer.class) //
    private Object[] upperBounds;

    /* Cache the lowerBounds value. */
    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplLowerBoundsComputer.class) //
    private Object[] lowerBounds;

    @Substitute
    public Type[] getUpperBounds() {
        Object[] value = upperBounds;
        if (value instanceof FieldTypeSignature[]) {
            value = Util_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl.reifyBounds(this, (FieldTypeSignature[]) value);
            upperBounds = value;
        }
        return (Type[]) value.clone();
    }

    @Substitute
    public Type[] getLowerBounds() {
        Object[] value = lowerBounds;
        if (value instanceof FieldTypeSignature[]) {
            value = Util_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl.reifyBounds(this, (FieldTypeSignature[]) value);
            lowerBounds = value;
        }
        return (Type[]) value.clone();
    }
}

class WildcardTypeImplUpperBoundsComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public RecomputeFieldValue.ValueAvailability valueAvailability() {
        return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
    }

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return GuardedBoundsAccess.getUpperBounds((WildcardTypeImpl) receiver);
    }
}

class WildcardTypeImplLowerBoundsComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public RecomputeFieldValue.ValueAvailability valueAvailability() {
        return RecomputeFieldValue.ValueAvailability.BeforeAnalysis;
    }

    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return GuardedBoundsAccess.getLowerBounds((WildcardTypeImpl) receiver);
    }
}

final class Util_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl {

    /** Emulate the Java class hierarchy. */
    @SuppressFBWarnings(value = "BC", justification = "Widening cast between @TargetClasses")
    static Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator asLazyReflectiveObjectGenerator(
                    Target_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl wildCardTypeImpl) {
        return Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator.class.cast(wildCardTypeImpl);
    }

    /** Emulate virtual dispatch. */
    static Type[] reifyBounds(Target_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl wildCardTypeImpl, FieldTypeSignature[] boundASTs) {
        return asLazyReflectiveObjectGenerator(wildCardTypeImpl).reifyBounds(boundASTs);
    }
}

class GuardedBoundsAccess {

    static Type[] getLowerBounds(WildcardTypeImpl receiver) {
        try {
            return receiver.getLowerBounds();
        } catch (TypeNotPresentException | MalformedParameterizedTypeException e) {
            /*
             * This computer is used to compute the value of the WildcardTypeImpl.lowerBounds field.
             * As per WildcardTypeImpl.getLowerBounds() javadoc:
             *
             * "Returns an array of <tt>Type</tt> objects representing the lower bound(s) of this
             * type variable. Note that if no lower bound is explicitly declared, the lower bound is
             * the type of <tt>null</tt>. In this case, a zero length array is returned."
             *
             * Thus, if getLowerBounds() throws a TypeNotPresentException, i.e., any of the bounds
             * refers to a non-existent type declaration, or a MalformedParameterizedTypeException,
             * i.e., any of the bounds refer to a parameterized type that cannot be instantiated, we
             * conservatively return a zero length array.
             */
            return new Type[0];
        }
    }

    static Type[] getUpperBounds(WildcardTypeImpl receiver) {
        try {
            return receiver.getUpperBounds();
        } catch (TypeNotPresentException | MalformedParameterizedTypeException e) {
            /*
             * This computer is used to compute the value of the WildcardTypeImpl.upperBounds field.
             * As per WildcardTypeImpl.getUpperBounds() javadoc:
             *
             * "Returns an array of <tt>Type</tt> objects representing the upper bound(s) of this
             * type variable. Note that if no upper bound is explicitly declared, the upper bound is
             * <tt>Object</tt>."
             *
             * Thus, if getUpperBounds() throws a TypeNotPresentException, i.e., any of the bounds
             * refers to a non-existent type declaration, or a MalformedParameterizedTypeException,
             * i.e., any of the bounds refer to a parameterized type that cannot be instantiated, we
             * conservatively return the upper bound.
             */
            return new Type[0];
        }
    }

    static Type[] getBounds(TypeVariableImpl<?> receiver) {
        try {
            return receiver.getBounds();
        } catch (TypeNotPresentException | MalformedParameterizedTypeException e) {
            /*
             * This computer is used to compute the value of the TypeVariableImpl.bounds field. As
             * per TypeVariableImpl.getBounds() javadoc:
             *
             * "Returns an array of <tt>Type</tt> objects representing the upper bound(s) of this
             * type variable. Note that if no upper bound is explicitly declared, the upper bound is
             * <tt>Object</tt>."
             *
             * Thus, if getBounds() throws a TypeNotPresentException, i.e., any of the bounds refers
             * to a non-existent type declaration, or a MalformedParameterizedTypeException, i.e.,
             * any of the bounds refer to a parameterized type that cannot be instantiated, we
             * conservatively return the upper bound.
             */
            return new Type[]{Object.class};
        }
    }

    static AnnotatedType[] getAnnotatedBounds(TypeVariableImpl<?> receiver) {
        try {
            return receiver.getAnnotatedBounds();
        } catch (TypeNotPresentException | MalformedParameterizedTypeException e) {
            /*
             * This computer is used to compute the value of the TypeVariableImpl.annotatedBounds
             * injected field. The original method calls TypeVariableImpl.getBounds() and if no
             * bounds are present it returns an empty array.
             */
            return new AnnotatedType[0];
        }
    }
}

public class SunReflectTypeSubstitutions {
}
