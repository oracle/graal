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

//Checkstyle: allow reflection

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;

import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.serviceprovider.GraalServices;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.JDK9OrLater;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import sun.reflect.generics.factory.GenericsFactory;
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

    @TargetElement(name = "bounds", onlyWith = JDK8OrEarlier.class) //
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeVariableBoundsComputer.class) //
    private Type[] boundsJDK8OrEarlier;

    @TargetElement(name = "bounds", onlyWith = JDK9OrLater.class) //
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeVariableBoundsComputer.class) //
    private volatile Object[] boundsJDK9OrLater;

    /* The bounds value is cached. The boundASTs field is not used at run time. */
    @Delete //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private FieldTypeSignature[] boundASTs;

    @Alias GenericDeclaration genericDeclaration;

    @Substitute
    @SuppressWarnings("unused")
    private Target_sun_reflect_generics_reflectiveObjects_TypeVariableImpl(GenericDeclaration decl, String n, FieldTypeSignature[] bs, GenericsFactory f) {
        throw VMError.shouldNotReachHere("sun.reflect.generics.reflectiveObjects.TypeVariableImpl constructor was removed. " +
                        "All the TypeVariableImpl objects should be allocated at image build time and cached in the native image heap.");
    }

    @Substitute
    public Type[] getBounds() {
        /* Variant method bodies from JDK-8 and JDK-9 to use the appropriately typed variables. */
        if (GraalServices.Java8OrEarlier) {
            return boundsJDK8OrEarlier;
        } else {
            Object[] value = boundsJDK9OrLater;
            /* We might want to reify the bounds eagerly during image generation: GR-10494. */
            // GR-10494: @formatter:off
            // GR-10494: if (value instanceof FieldTypeSignature[]) {
            // GR-10494:     value = Util_sun_reflect_generics_reflectiveObjects_TypeVariableImpl.reifyBounds(this, (FieldTypeSignature[]) value);
            // GR-10494:     boundsJDK9OrLater = value;
            // GR-10494: }
            // GR-10494: @formatter:on
            return (Type[]) value.clone();
        }
    }

    /** Reason for substitutions: disable access checks in original method. */
    @Substitute
    public GenericDeclaration getGenericDeclaration() {
        return genericDeclaration;
    }
}

final class Util_sun_reflect_generics_reflectiveObjects_TypeVariableImpl {

    /** Emulate the Java class hierarchy. */
    static Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator asLazyReflectiveObjectGenerator(
                    Target_sun_reflect_generics_reflectiveObjects_TypeVariableImpl typeVariableImpl) {
        /* TODO: Can I just cast between these types and get checks at runtime? */
        return KnownIntrinsics.convertUnknownValue(typeVariableImpl, Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator.class);
    }

    /** Emulate virtual dispatch. */
    static Type[] reifyBounds(Target_sun_reflect_generics_reflectiveObjects_TypeVariableImpl typeVariableImpl, FieldTypeSignature[] boundASTs) {
        return asLazyReflectiveObjectGenerator(typeVariableImpl).reifyBounds(boundASTs);
    }
}

@TargetClass(className = "sun.reflect.generics.reflectiveObjects.LazyReflectiveObjectGenerator", onlyWith = JDK9OrLater.class)
final class Target_sun_reflect_generics_reflectiveObjects_LazyReflectiveObjectGenerator {

    @Alias
    @TargetElement(onlyWith = JDK9OrLater.class)
    native Type[] reifyBounds(FieldTypeSignature[] boundASTs);
}

class TypeVariableBoundsComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ((TypeVariableImpl<?>) receiver).getBounds();
    }
}

@TargetClass(sun.reflect.generics.reflectiveObjects.WildcardTypeImpl.class)
final class Target_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl {

    /* Cache the upperBounds value. */

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplUpperBoundsComputer.class) //
    @TargetElement(name = "upperBounds", onlyWith = JDK8OrEarlier.class) //
    private Type[] upperBoundsJDK8OrEarlier;

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplUpperBoundsComputer.class) //
    @TargetElement(name = "upperBounds", onlyWith = JDK9OrLater.class) //
    private Object[] upperBoundsJDK9OrLater;

    /* Cache the lowerBounds value. */

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplLowerBoundsComputer.class) //
    @TargetElement(name = "lowerBounds", onlyWith = JDK8OrEarlier.class) //
    private Type[] lowerBoundsJDK8OrEarlier;

    @Alias //
    @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplLowerBoundsComputer.class) //
    @TargetElement(name = "lowerBounds", onlyWith = JDK9OrLater.class) //
    private Object[] lowerBoundsJDK9OrLater;

    /* The upperBounds value is cached. The upperBoundASTs field is not used at run time. */
    @Delete //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private FieldTypeSignature[] upperBoundASTs;
    /* The lowerBounds value is cached. The lowerBoundASTs field is not used at run time. */
    @Delete //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private FieldTypeSignature[] lowerBoundASTs;

    @Substitute
    @SuppressWarnings("unused")
    private Target_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl(FieldTypeSignature[] ubs, FieldTypeSignature[] lbs, GenericsFactory f) {
        throw VMError.shouldNotReachHere("sun.reflect.generics.reflectiveObjects.WildcardTypeImpl constructor was removed." +
                        "All the WildcardTypeImpl objects should be allocated at image build time and cached in the native image heap.");
    }

    @Substitute
    public Type[] getUpperBounds() {
        if (GraalServices.Java8OrEarlier) {
            return upperBoundsJDK8OrEarlier;
        } else {
            Object[] value = upperBoundsJDK9OrLater;
            if (value instanceof FieldTypeSignature[]) {
                value = Util_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl.reifyBounds(this, (FieldTypeSignature[]) value);
                upperBoundsJDK9OrLater = value;
            }
            return (Type[]) value.clone();
        }
    }

    @Substitute
    public Type[] getLowerBounds() {
        if (GraalServices.Java8OrEarlier) {
            return lowerBoundsJDK8OrEarlier;
        } else {
            Object[] value = lowerBoundsJDK9OrLater;
            if (value instanceof FieldTypeSignature[]) {
                value = Util_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl.reifyBounds(this, (FieldTypeSignature[]) value);
                lowerBoundsJDK9OrLater = value;
            }
            return (Type[]) value.clone();
        }
    }
}

class WildcardTypeImplUpperBoundsComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ((WildcardTypeImpl) receiver).getUpperBounds();
    }
}

class WildcardTypeImplLowerBoundsComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ((WildcardTypeImpl) receiver).getLowerBounds();
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

public class SunReflectTypeSubstitutions {
}
