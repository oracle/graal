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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.invoke.MethodHandleIntrinsic;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;

import jdk.vm.ci.meta.JavaKind;

/**
 * This class represents direct method handles which are not invoked through reflection for various
 * reasons. These reasons include deviations from the JDK implementation (InvokeBasic, Link and the
 * BoundMethodHandle methods) and optimizations.
 *
 * The intrinsics are created upon encountering a listed function during method handle resolution.
 * (see
 * {@link Target_java_lang_invoke_MethodHandleNatives#resolve(Target_java_lang_invoke_MemberName, Class, boolean)}).
 */
final class MethodHandleIntrinsicImpl implements MethodHandleIntrinsic {
    enum Variant {
        /* Method handle invocation */

        /* MethodHandle.invokeBasic(Object...) */
        InvokeBasic(Modifier.FINAL | Modifier.NATIVE),
        /* MethodHandle.linkTo*(Object...) */
        Link(Modifier.STATIC | Modifier.NATIVE),

        /* Field access */

        /* Unsafe.(get|put)<T>[Volatile](Object, long[, <T>]) */
        UnsafeFieldAccess(Modifier.PUBLIC | Modifier.NATIVE),

        /* Bound method handle operations */

        /* BoundMethodHandle$Species_<S>.make(MethodType, LambdaForm, Object...) */
        Make(Modifier.STATIC),
        /* BoundMethodHandle$Species_<S>.copyWithExtend<T>(MethodType, LambdaForm, <T>) */
        CopyExtend(Modifier.FINAL),
        /* BoundMethodHandle$Species_<S>.BMH_SPECIES */
        BMHSpecies(Modifier.STATIC),
        /* BoundMethodHandle$Species_<S>.arg<T><I> */
        Arg(Modifier.FINAL),

        /* Simple operations */

        /* LambdaForm.identity_<T>() */
        Identity(Modifier.STATIC | Modifier.PRIVATE),
        /* LambdaForm.zero_<T>() */
        Zero(Modifier.STATIC | Modifier.PRIVATE),
        /* Class.cast(Object) */
        Cast(Modifier.PUBLIC),

        /* Array operations */

        /* MethodHandleImpl.array(Object...) */
        Array(Modifier.STATIC | Modifier.PRIVATE),
        /* MethodHandleImpl.fillArray(Integer, Object[], Object...) */
        FillArray(Modifier.STATIC | Modifier.PRIVATE),
        /* MethodHandleImpl$ArrayAccessor.getElement*(<T>[], int) */
        GetElement(Modifier.STATIC),
        /* MethodHandleImpl$ArrayAccessor.setElement*(<T>[], int, <T>) */
        SetElement(Modifier.STATIC),
        /* MethodHandleImpl$ArrayAccessor.length(<T>[]) */
        Length(Modifier.STATIC);

        int flags;

        Variant(int flags) {
            this.flags = flags;
        }
    }

    static Map<Variant, Map<String, Map<JavaKind, Map<Integer, MethodHandleIntrinsicImpl>>>> cache = new ConcurrentHashMap<>();
    static final String NO_SPECIES = "";
    static final Set<String> unsafeFieldAccessMethodNames = new HashSet<>();

    static {
        for (String op : Arrays.asList("get", "put")) {
            for (String type : Arrays.asList("Boolean", "Byte", "Short", "Char", "Int", "Long", "Float", "Double",
                            /* JDK-8207146 renamed Unsafe.xxxObject to xxxReference. */
                            JavaVersionUtil.JAVA_SPEC == 11 ? "Object" : "Reference")) {
                for (String isVolatile : Arrays.asList("", "Volatile")) {
                    unsafeFieldAccessMethodNames.add(op + type + isVolatile);
                }
            }
        }
    }

    final Variant variant;
    final String species; /* For BoundMethodHandle intrinsics */
    final JavaKind kind;
    final int index; /* For BoundMethodHandle parameters */

    private MethodHandleIntrinsicImpl(Variant variant, String species, JavaKind kind, int index) {
        this.variant = variant;
        this.species = species;
        this.kind = kind;
        this.index = index;
    }

    private static MethodHandleIntrinsicImpl intrinsic(Variant variant, String species, JavaKind kind, int index) {
        return cache.computeIfAbsent(variant, (v) -> new ConcurrentHashMap<>())
                        .computeIfAbsent(species, (s) -> new ConcurrentHashMap<>())
                        .computeIfAbsent(kind, (t) -> new ConcurrentHashMap<>())
                        .computeIfAbsent(index, (i) -> new MethodHandleIntrinsicImpl(variant, species, kind, index));
    }

    static MethodHandleIntrinsicImpl intrinsic(Variant variant) {
        return intrinsic(variant, NO_SPECIES, JavaKind.Illegal, -1);
    }

    static MethodHandleIntrinsicImpl intrinsic(Variant variant, JavaKind kind) {
        return intrinsic(variant, NO_SPECIES, kind, -1);
    }

    static MethodHandleIntrinsicImpl intrinsic(Variant variant, String species) {
        return intrinsic(variant, species, JavaKind.Illegal, -1);
    }

    static MethodHandleIntrinsicImpl intrinsic(Variant variant, JavaKind kind, int index) {
        return intrinsic(variant, NO_SPECIES, kind, index);
    }

    private static JavaKind kindForKey(char key) {
        if (key == 'L') {
            return JavaKind.Object;
        }
        return JavaKind.fromPrimitiveOrVoidTypeChar(key);
    }

    @Override
    public Object execute(Object... args) throws Throwable {
        switch (variant) {
            /*
             * Recursive call to invokeBasic. We don't invoke it through reflection to avoid
             * infinite recursion
             */
            case InvokeBasic: {
                assert args.length >= 1;
                Target_java_lang_invoke_MethodHandle mh = (Target_java_lang_invoke_MethodHandle) args[0];
                Object[] invokeArgs = Arrays.copyOfRange(args, 1, args.length);
                return mh.invokeBasic(invokeArgs);
            }

            /*
             * The linkTo methods are used to create optimized invokers for a direct method handle.
             * We handle direct method handles explicitly in MethodHandle.invokeBasic, so we don't
             * need those invokers.
             */
            case Link:
                throw shouldNotReachHere("linkTo methods should not be executed");

            /*
             * The Unsafe.(get|put)<Type>[Volatile] method are resolved internally by the JDK
             * implementation, but we don't use them to access fields. We keep those as intrinsics
             * to avoid substituting a complex method just to filter them out.
             */
            case UnsafeFieldAccess:
                throw shouldNotReachHere("unsafe field access methods should not be executed");

            /*
             * Bound method handle constructor. Creates an instance of BoundMethodHandle with an
             * array of saved parameters (see Target_java_lang_invoke_BoundMethodHandle).
             */
            case Make: {
                assert args.length >= 2;
                MethodType methodType = (MethodType) args[0];
                Target_java_lang_invoke_LambdaForm form = (Target_java_lang_invoke_LambdaForm) args[1];
                Object[] actualArgs = new Object[args.length - 2];
                System.arraycopy(args, 2, actualArgs, 0, actualArgs.length);
                return BoundMethodHandleUtils.make(methodType, form, species, actualArgs);
            }

            /*
             * Bound method handle copy constructor, with optionally an additional saved parameter
             * (see Target_java_lang_invoke_BoundMethodHandle).
             */
            case CopyExtend: {
                assert (kind == JavaKind.Void && args.length == 3) || args.length == 4;
                Target_java_lang_invoke_SimpleMethodHandle bmh = (Target_java_lang_invoke_SimpleMethodHandle) args[0];
                MethodType methodType = (MethodType) args[1];
                Target_java_lang_invoke_LambdaForm form = (Target_java_lang_invoke_LambdaForm) args[2];
                Object newArg = (kind != JavaKind.Void) ? args[3] : null;
                switch (kind) {
                    case Object:
                        return bmh.copyWithExtendL(methodType, form, newArg);
                    case Int:
                        return bmh.copyWithExtendI(methodType, form, (int) newArg);
                    case Long:
                        return bmh.copyWithExtendJ(methodType, form, (long) newArg);
                    case Float:
                        return bmh.copyWithExtendF(methodType, form, (float) newArg);
                    case Double:
                        return bmh.copyWithExtendD(methodType, form, (double) newArg);
                    case Void:
                        return bmh.copyWith(methodType, form);
                }
                throw shouldNotReachHere("illegal kind");
            }

            /*
             * Accessor for the species descriptor of a bound method handle (see
             * Target_java_lang_invoke_BoundMethodHandle).
             */
            case BMHSpecies: {
                assert args.length == 1;
                Target_java_lang_invoke_SimpleMethodHandle bmh = (Target_java_lang_invoke_SimpleMethodHandle) args[0];
                return bmh.speciesData;
            }

            /*
             * Bound method handle saved parameter accessor (see
             * Target_java_lang_invoke_BoundMethodHandle).
             */
            case Arg: {
                assert args.length == 1;
                Target_java_lang_invoke_MethodHandle mh = (Target_java_lang_invoke_MethodHandle) args[0];
                if (args[0] instanceof Target_java_lang_invoke_MethodHandleImpl_IntrinsicMethodHandle) {
                    mh = (SubstrateUtil.cast(args[0], Target_java_lang_invoke_MethodHandleImpl_IntrinsicMethodHandle.class).getTarget());
                }

                if (Target_java_lang_invoke_SimpleMethodHandle.class.isAssignableFrom(mh.getClass())) {
                    Target_java_lang_invoke_SimpleMethodHandle bmh = SubstrateUtil.cast(mh, Target_java_lang_invoke_SimpleMethodHandle.class);
                    return bmh.args[index];
                } else {
                    char kindChar = kind == JavaKind.Object ? 'L' : kind.getTypeChar();
                    String argName = "arg" + kindChar + index;
                    Field argField = mh.getClass().getDeclaredField(argName);
                    argField.setAccessible(true);
                    return argField.get(mh);
                }
            }

            /* java.lang.invoke.MethodHandleImpl helper functions */

            case Identity:
                assert args.length == 1;
                return args[0];

            case Zero:
                assert args.length == 0;
                switch (kind) {
                    case Object:
                        return null;
                    case Int:
                        return 0;
                    case Long:
                        return 0L;
                    case Float:
                        return 0F;
                    case Double:
                        return 0D;
                    default:
                        throw shouldNotReachHere("Unknown zero kind: " + kind);
                }

            case Cast: {
                assert args.length == 2;
                Class<?> clazz = (Class<?>) args[0];
                Object obj = args[1];
                return clazz.cast(obj);
            }

            /*
             * Creates an array from a variable number of parameters. This array is already here for
             * us.
             */
            case Array:
                return Arrays.copyOf(args, args.length);

            /* Fills an existing array with a variable number of given arguments. */
            case FillArray: {
                assert args.length >= 3;
                Integer pos = (Integer) args[0];
                Object[] dest = (Object[]) args[1];
                Object[] src = Arrays.copyOfRange(args, 2, args.length);
                System.arraycopy(src, 0, dest, pos, src.length);
                return dest;
            }

            /* java.lang.invoke.MethodHandleImpl$ArrayAccessor helper functions */

            case GetElement: {
                assert args.length == 2;
                Object array = args[0];
                int i = (int) args[1];
                switch (kind) {
                    case Object:
                        return ((Object[]) array)[i];
                    case Boolean:
                        return ((boolean[]) array)[i];
                    case Byte:
                        return ((byte[]) array)[i];
                    case Short:
                        return ((short[]) array)[i];
                    case Char:
                        return ((char[]) array)[i];
                    case Int:
                        return ((int[]) array)[i];
                    case Long:
                        return ((long[]) array)[i];
                    case Float:
                        return ((float[]) array)[i];
                    case Double:
                        return ((double[]) array)[i];
                    default:
                        throw shouldNotReachHere("Illegal intrinsic kind: " + kind);
                }
            }

            case SetElement: {
                assert args.length == 3;
                Object array = args[0];
                int i = (int) args[1];
                Object value = args[2];
                switch (kind) {
                    case Object:
                        ((Object[]) array)[i] = value;
                        return null;
                    case Boolean:
                        ((boolean[]) array)[i] = (boolean) value;
                        return null;
                    case Byte:
                        ((byte[]) array)[i] = (byte) value;
                        return null;
                    case Short:
                        ((short[]) array)[i] = (short) value;
                        return null;
                    case Char:
                        ((char[]) array)[i] = (char) value;
                        return null;
                    case Int:
                        ((int[]) array)[i] = (int) value;
                        return null;
                    case Long:
                        ((long[]) array)[i] = (long) value;
                        return null;
                    case Float:
                        ((float[]) array)[i] = (float) value;
                        return null;
                    case Double:
                        ((double[]) array)[i] = (double) value;
                        return null;
                    default:
                        throw shouldNotReachHere("Illegal intrinsic kind: " + kind);
                }
            }

            case Length: {
                assert args.length == 1;
                Object array = args[0];
                switch (kind) {
                    case Object:
                        return ((Object[]) array).length;
                    case Boolean:
                        return ((boolean[]) array).length;
                    case Byte:
                        return ((byte[]) array).length;
                    case Short:
                        return ((short[]) array).length;
                    case Char:
                        return ((char[]) array).length;
                    case Int:
                        return ((int[]) array).length;
                    case Long:
                        return ((long[]) array).length;
                    case Float:
                        return ((float[]) array).length;
                    case Double:
                        return ((double[]) array).length;
                    default:
                        throw shouldNotReachHere("Illegal intrinsic kind: " + kind);
                }
            }

            default:
                throw shouldNotReachHere("Unknown intrinsic: " + this);
        }
    }

    /*
     * We use string comparison to perform the lookup to avoid pulling in the methods we want to
     * intrinsify through reflection.
     */
    static MethodHandleIntrinsicImpl resolve(Target_java_lang_invoke_MemberName memberName) {
        Class<?> declaringClass = memberName.getDeclaringClass();
        String name = memberName.name;

        if (declaringClass == Target_java_lang_invoke_MethodHandle.class) {
            switch (name) {
                case "invokeBasic":
                    return intrinsic(Variant.InvokeBasic);
                case "linkToVirtual":
                case "linkToStatic":
                case "linkToSpecial":
                case "linkToInterface":
                    return intrinsic(Variant.Link);
            }
        } else if ("jdk.internal.misc.Unsafe".equals(declaringClass.getTypeName()) &&
                        unsafeFieldAccessMethodNames.contains(name)) {
            return intrinsic(Variant.UnsafeFieldAccess);
        } else if (declaringClass == Target_java_lang_invoke_BoundMethodHandle.class ||
                        /*
                         * The L species is directly accessed in some places and needs a special
                         * case to redirect it to the correct intrinsic
                         */
                        declaringClass == Target_java_lang_invoke_BoundMethodHandle_Species_L.class ||
                        /*
                         * Method handles that were resolved at build time try to access their
                         * fields via the original species classes, so we need to redirect those.
                         */
                        declaringClass.getTypeName().startsWith("java.lang.invoke.BoundMethodHandle$Species_")) {
            /* Bound parameter fields can take arbitrary kinds and indexes */
            if (name.startsWith("arg")) {
                JavaKind kind = kindForKey(name.charAt("arg".length()));
                int index = Integer.parseInt(name.substring("arg".length() + 1));
                return intrinsic(Variant.Arg, kind, index);
            }

            switch (name) {
                case "make": {
                    Class<?>[] paramTypes = memberName.getMethodType().parameterArray();
                    StringBuilder species = new StringBuilder();
                    for (int i = 2; i < paramTypes.length; ++i) {
                        JavaKind kind = JavaKind.fromJavaClass(paramTypes[i]);
                        species.append(kind == JavaKind.Object ? 'L' : kind.getTypeChar());
                    }
                    return intrinsic(Variant.Make, species.toString());
                }
                case "BMH_SPECIES":
                    return intrinsic(Variant.BMHSpecies);
                case "copyWithExtendL":
                case "copyWithExtendI":
                case "copyWithExtendJ":
                case "copyWithExtendF":
                case "copyWithExtendD":
                    JavaKind kind = kindForKey(name.charAt("copyWithExtend".length()));
                    return intrinsic(Variant.CopyExtend, kind);
                case "copyWith":
                    return intrinsic(Variant.CopyExtend);
            }
        } else if (declaringClass == Target_java_lang_invoke_LambdaForm.class) {
            switch (name) {
                case "identity_L":
                case "identity_I":
                case "identity_J":
                case "identity_F":
                case "identity_D":
                case "identity_V": {
                    JavaKind kind = kindForKey(name.charAt("identity_".length()));
                    return intrinsic(Variant.Identity, kind);
                }
                case "zero_L":
                case "zero_I":
                case "zero_J":
                case "zero_F":
                case "zero_D": {
                    JavaKind kind = kindForKey(name.charAt("zero_".length()));
                    return intrinsic(Variant.Zero, kind);
                }
            }
        } else if (declaringClass == DynamicHub.class) {
            if ("cast".equals(name)) {
                return intrinsic(Variant.Cast);
            }
        } else if (declaringClass == Target_java_lang_invoke_MethodHandleImpl.class) {
            switch (name) {
                case "array":
                    return intrinsic(Variant.Array);
                case "fillArray":
                    return intrinsic(Variant.FillArray);
            }
        } else if (declaringClass == Target_java_lang_invoke_MethodHandleImpl_ArrayAccessor.class) {
            switch (name) {
                case "getElementL":
                case "getElementZ":
                case "getElementB":
                case "getElementS":
                case "getElementC":
                case "getElementI":
                case "getElementJ":
                case "getElementF":
                case "getElementD": {
                    JavaKind kind = kindForKey(name.charAt("getElement".length()));
                    return intrinsic(Variant.GetElement, kind);
                }
                case "setElementL":
                case "setElementZ":
                case "setElementB":
                case "setElementS":
                case "setElementC":
                case "setElementI":
                case "setElementJ":
                case "setElementF":
                case "setElementD": {
                    JavaKind kind = kindForKey(name.charAt("setElement".length()));
                    return intrinsic(Variant.SetElement, kind);
                }
                case "lengthL":
                case "lengthZ":
                case "lengthB":
                case "lengthS":
                case "lengthC":
                case "lengthI":
                case "lengthJ":
                case "lengthF":
                case "lengthD": {
                    JavaKind kind = kindForKey(name.charAt("length".length()));
                    return intrinsic(Variant.Length, kind);
                }
            }
        }
        return null; /* No intrinsic found */
    }
}

@TargetClass(className = "java.lang.invoke.MethodHandleImpl", innerClass = "IntrinsicMethodHandle")
final class Target_java_lang_invoke_MethodHandleImpl_IntrinsicMethodHandle {
    @Alias
    protected native Target_java_lang_invoke_MethodHandle getTarget();
}
