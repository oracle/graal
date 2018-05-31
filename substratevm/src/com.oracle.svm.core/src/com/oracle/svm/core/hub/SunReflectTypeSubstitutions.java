/* 
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved. 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER. 
 * 
 * This code is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU General Public License version 2 only, as 
 * published by the Free Software Foundation. 
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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
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
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = TypeVariableBoundsComputer.class) private Type[] bounds;
    /* The bounds value is cached. The boundASTs field is not used at run time. */
    @Delete private FieldTypeSignature[] boundASTs;

    @Alias GenericDeclaration genericDeclaration;

    @Substitute
    @SuppressWarnings("unused")
    private Target_sun_reflect_generics_reflectiveObjects_TypeVariableImpl(GenericDeclaration decl, String n, FieldTypeSignature[] bs, GenericsFactory f) {
        throw VMError.shouldNotReachHere("sun.reflect.generics.reflectiveObjects.TypeVariableImpl constructor was removed. " +
                        "All the TypeVariableImpl objects should be allocated at image build time and cached in the native image heap.");
    }

    @Substitute
    public Type[] getBounds() {
        return bounds;
    }

    /** Reason for substitutions: disable access checks in original method. */
    @Substitute
    public GenericDeclaration getGenericDeclaration() {
        return genericDeclaration;
    }
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
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplUpperBoundsComputer.class) private Type[] upperBounds;
    /* Cache the lowerBounds value. */
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = WildcardTypeImplLowerBoundsComputer.class) private Type[] lowerBounds;
    /* The upperBounds value is cached. The upperBoundASTs field is not used at run time. */
    @Delete private FieldTypeSignature[] upperBoundASTs;
    /* The lowerBounds value is cached. The lowerBoundASTs field is not used at run time. */
    @Delete private FieldTypeSignature[] lowerBoundASTs;

    @Substitute
    @SuppressWarnings("unused")
    private Target_sun_reflect_generics_reflectiveObjects_WildcardTypeImpl(FieldTypeSignature[] ubs, FieldTypeSignature[] lbs, GenericsFactory f) {
        throw VMError.shouldNotReachHere("sun.reflect.generics.reflectiveObjects.WildcardTypeImpl constructor was removed." +
                        "All the WildcardTypeImpl objects should be allocated at image build time and cached in the native image heap.");
    }

    @Substitute
    public Type[] getUpperBounds() {
        return upperBounds;
    }

    @Substitute
    public Type[] getLowerBounds() {
        return lowerBounds;
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

public class SunReflectTypeSubstitutions {
}
