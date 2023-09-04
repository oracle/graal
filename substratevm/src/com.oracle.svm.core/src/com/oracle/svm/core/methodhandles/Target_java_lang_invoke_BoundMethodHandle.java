/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.methodhandles;

import static com.oracle.svm.core.annotate.TargetElement.CONSTRUCTOR_NAME;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.fieldvaluetransformer.NewEmptyArrayFieldValueTransformer;

/**
 * In the JDK implementation of method handles, each bound method handle is an instance of a
 * specific species class, depending on the types of the parameters bound to the handle. For
 * example, a simple {@link MethodHandle#bindTo(Object)} call on a direct method handle will be
 * represented by a bound method handle of species "L", containing a single bound parameter named
 * argL0.
 *
 * To be able to handle every possible bound method handle species, the JDK implementation provides
 * a set of pre-compiled species for the most common cases, and generates the other ones at runtime
 * the first time they are required (see {@link Target_java_lang_invoke_ClassSpecializer}).
 *
 * The Native Image implementation of bound method handles does not follow this design. Instead, it
 * represents every bound method handle as an instance of the BoundMethodHandle class, where its
 * bound parameters are stored in an injected boxed array. This design choice allows a single
 * implementation of the methods that every species must provide, namely the make method (factory)
 * and the copyWith and copyWithExtend* methods (copy constructors).
 */
@TargetClass(className = "java.lang.invoke.BoundMethodHandle")
final class Target_java_lang_invoke_BoundMethodHandle {
    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    static Target_java_lang_invoke_BoundMethodHandle_Specializer SPECIALIZER;

    @Alias
    @TargetElement(name = CONSTRUCTOR_NAME)
    native void constructor(MethodType type, Target_java_lang_invoke_LambdaForm form);

    @Alias
    static native Target_java_lang_invoke_BoundMethodHandle_SpeciesData speciesDataFor(Target_java_lang_invoke_LambdaForm form);
}

/*
 * We hijack the species with no bound parameters for our implementation since it already inherits
 * from BoundMethodHandle and doesn't contain any superfluous members.
 */
@TargetClass(className = "java.lang.invoke.SimpleMethodHandle")
final class Target_java_lang_invoke_SimpleMethodHandle {
    /*
     * Since we represent all the bound method handle species with the basic one, the species data
     * is therefore an instance field of the class, not a static one.
     */
    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Target_java_lang_invoke_BoundMethodHandle_SpeciesData speciesData;

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    Object[] args;

    @Substitute
    Target_java_lang_invoke_SimpleMethodHandle(MethodType type, Target_java_lang_invoke_LambdaForm form) {
        SubstrateUtil.cast(this, Target_java_lang_invoke_BoundMethodHandle.class).constructor(type, form);
    }

    @Substitute
    Target_java_lang_invoke_BoundMethodHandle_SpeciesData speciesData() {
        if (speciesData == null) {
            speciesData = Target_java_lang_invoke_BoundMethodHandle.speciesDataFor(SubstrateUtil.cast(this, Target_java_lang_invoke_MethodHandle.class).internalForm());
        }
        return speciesData;
    }

    @Substitute
    Target_java_lang_invoke_BoundMethodHandle copyWithExtendL(MethodType type, Target_java_lang_invoke_LambdaForm form, Object newArg) {
        return BoundMethodHandleUtils.make(type, form, BoundMethodHandleUtils.speciesKey(this) + "L", BoundMethodHandleUtils.appendArgs(args, newArg));
    }

    @Substitute
    Target_java_lang_invoke_BoundMethodHandle copyWithExtendI(MethodType type, Target_java_lang_invoke_LambdaForm form, int newArg) {
        return BoundMethodHandleUtils.make(type, form, BoundMethodHandleUtils.speciesKey(this) + "I", BoundMethodHandleUtils.appendArgs(args, newArg));
    }

    @Substitute
    Target_java_lang_invoke_BoundMethodHandle copyWithExtendJ(MethodType type, Target_java_lang_invoke_LambdaForm form, long newArg) {
        return BoundMethodHandleUtils.make(type, form, BoundMethodHandleUtils.speciesKey(this) + "J", BoundMethodHandleUtils.appendArgs(args, newArg));
    }

    @Substitute
    Target_java_lang_invoke_BoundMethodHandle copyWithExtendF(MethodType type, Target_java_lang_invoke_LambdaForm form, float newArg) {
        return BoundMethodHandleUtils.make(type, form, BoundMethodHandleUtils.speciesKey(this) + "F", BoundMethodHandleUtils.appendArgs(args, newArg));
    }

    @Substitute
    Target_java_lang_invoke_BoundMethodHandle copyWithExtendD(MethodType type, Target_java_lang_invoke_LambdaForm form, double newArg) {
        return BoundMethodHandleUtils.make(type, form, BoundMethodHandleUtils.speciesKey(this) + "D", BoundMethodHandleUtils.appendArgs(args, newArg));
    }

    @Substitute
    Target_java_lang_invoke_BoundMethodHandle copyWith(MethodType type, Target_java_lang_invoke_LambdaForm form) {
        return BoundMethodHandleUtils.make(type, form, BoundMethodHandleUtils.speciesKey(this), args);
    }
}

/* Hardcoded species, needs a special case to avoid initialization */
@TargetClass(className = "java.lang.invoke.BoundMethodHandle", innerClass = "Species_L")
final class Target_java_lang_invoke_BoundMethodHandle_Species_L {
    @Substitute
    static Target_java_lang_invoke_BoundMethodHandle make(MethodType mt, Target_java_lang_invoke_LambdaForm lf, Object argL0) {
        return BoundMethodHandleUtils.make(mt, lf, "L", argL0);
    }
}

@TargetClass(className = "java.lang.invoke.BoundMethodHandle", innerClass = "SpeciesData")
final class Target_java_lang_invoke_BoundMethodHandle_SpeciesData {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Custom, declClass = NewEmptyArrayFieldValueTransformer.class, isFinal = true) //
    private Target_java_lang_invoke_BoundMethodHandle_SpeciesData[] extensions;
}

@TargetClass(className = "java.lang.invoke.BoundMethodHandle", innerClass = "Specializer")
final class Target_java_lang_invoke_BoundMethodHandle_Specializer {
}

final class BoundMethodHandleUtils {
    /* Bound method handle constructor */
    static Target_java_lang_invoke_BoundMethodHandle make(MethodType type, Target_java_lang_invoke_LambdaForm form, String species, Object... args) {
        Target_java_lang_invoke_SimpleMethodHandle bmh = new Target_java_lang_invoke_SimpleMethodHandle(type, form);
        var specializer = SubstrateUtil.cast(Target_java_lang_invoke_BoundMethodHandle.SPECIALIZER, Target_java_lang_invoke_ClassSpecializer.class);
        bmh.speciesData = SubstrateUtil.cast(specializer.findSpecies(species), Target_java_lang_invoke_BoundMethodHandle_SpeciesData.class);
        bmh.args = (args != null) ? Arrays.copyOf(args, args.length) : new Object[0];
        return SubstrateUtil.cast(bmh, Target_java_lang_invoke_BoundMethodHandle.class);
    }

    static Object[] appendArgs(Object[] args, Object newArg) {
        if (args == null) {
            return new Object[]{newArg};
        }
        Object[] newArgs = new Object[args.length + 1];
        System.arraycopy(args, 0, newArgs, 0, args.length);
        newArgs[args.length] = newArg;
        return newArgs;
    }

    static String speciesKey(Target_java_lang_invoke_SimpleMethodHandle bmh) {
        return (String) SubstrateUtil.cast(bmh.speciesData(), Target_java_lang_invoke_ClassSpecializer_SpeciesData.class).key();
    }
}
