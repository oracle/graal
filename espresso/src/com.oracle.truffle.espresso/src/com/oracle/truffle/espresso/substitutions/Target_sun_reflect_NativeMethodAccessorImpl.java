/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * This substitution is merely for performance reasons, to avoid the deep-dive to native. libjava
 * hardwires {@link #invoke0} to JVM_InvokeMethod in libjvm.
 */
@EspressoSubstitutions(nameProvider = Target_sun_reflect_NativeMethodAccessorImpl.SharedNativeMetohdAccessorImpl.class)
public final class Target_sun_reflect_NativeMethodAccessorImpl {

    /**
     * Checks argument for reflection, checking type matches and widening for primitives. Throws
     * guest IllegalArgumentException if any of the arguments does not match.
     */
    public static Object checkAndWiden(Meta meta, StaticObject arg, Klass targetKlass) {
        if (targetKlass.isPrimitive()) {
            if (StaticObject.isNull(arg)) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "argument type mismatch");
            }
            Klass argKlass = arg.getKlass();
            switch (targetKlass.getJavaKind()) {
                case Boolean:
                    if (argKlass == meta.java_lang_Boolean) {
                        return meta.unboxBoolean(arg);
                    }
                    break; // fail

                case Byte:
                    if (argKlass == meta.java_lang_Byte) {
                        return meta.unboxByte(arg);
                    }
                    break; // fail

                case Char:
                    if (argKlass == meta.java_lang_Character) {
                        return meta.unboxCharacter(arg);
                    }
                    break; // fail

                case Short:
                    if (argKlass == meta.java_lang_Short) {
                        return meta.unboxShort(arg);
                    }
                    if (argKlass == meta.java_lang_Byte) {
                        return (short) meta.unboxByte(arg);
                    }
                    break; // fail

                case Int:
                    if (argKlass == meta.java_lang_Integer) {
                        return meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.java_lang_Byte) {
                        return (int) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.java_lang_Character) {
                        return (int) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.java_lang_Short) {
                        return (int) meta.unboxShort(arg);
                    }
                    break; // fail

                case Float:
                    if (argKlass == meta.java_lang_Float) {
                        return meta.unboxFloat(arg);
                    }
                    if (argKlass == meta.java_lang_Byte) {
                        return (float) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.java_lang_Character) {
                        return (float) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.java_lang_Short) {
                        return (float) meta.unboxShort(arg);
                    }
                    if (argKlass == meta.java_lang_Integer) {
                        return (float) meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.java_lang_Long) {
                        return (float) meta.unboxLong(arg);
                    }
                    break; // fail

                case Long:
                    if (argKlass == meta.java_lang_Long) {
                        return meta.unboxLong(arg);
                    }
                    if (argKlass == meta.java_lang_Integer) {
                        return (long) meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.java_lang_Byte) {
                        return (long) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.java_lang_Character) {
                        return (long) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.java_lang_Short) {
                        return (long) meta.unboxShort(arg);
                    }
                    break; // fail

                case Double:
                    if (argKlass == meta.java_lang_Double) {
                        return meta.unboxDouble(arg);
                    }
                    if (argKlass == meta.java_lang_Float) {
                        return (double) meta.unboxFloat(arg);
                    }
                    if (argKlass == meta.java_lang_Integer) {
                        return (double) meta.unboxInteger(arg);
                    }
                    if (argKlass == meta.java_lang_Byte) {
                        return (double) meta.unboxByte(arg);
                    }
                    if (argKlass == meta.java_lang_Character) {
                        return (double) meta.unboxCharacter(arg);
                    }
                    if (argKlass == meta.java_lang_Short) {
                        return (double) meta.unboxShort(arg);
                    }
                    if (argKlass == meta.java_lang_Long) {
                        return (double) meta.unboxLong(arg);
                    }
                    break; // fail
            }
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "argument type mismatch");
        } else {
            if (StaticObject.notNull(arg) && !targetKlass.isAssignableFrom(arg.getKlass())) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "argument type mismatch");
            }
            return arg;
        }
    }

    /**
     * Invokes the underlying method represented by this {@code Method} object, on the specified
     * object with the specified parameters. Individual parameters are automatically unwrapped to
     * match primitive formal parameters, and both primitive and reference parameters are subject to
     * method invocation conversions as necessary.
     *
     * <p>
     * If the underlying method is static, then the specified {@code receiver} argument is ignored.
     * It may be null.
     *
     * <p>
     * If the number of formal parameters required by the underlying method is 0, the supplied
     * {@code args} array may be of length 0 or null.
     *
     * <p>
     * If the underlying method is an instance method, it is invoked using dynamic method lookup as
     * documented in The Java Language Specification, Second Edition, section 15.12.4.4; in
     * particular, overriding based on the runtime type of the target object will occur.
     *
     * <p>
     * If the underlying method is static, the class that declared the method is initialized if it
     * has not already been initialized.
     *
     * <p>
     * If the method completes normally, the value it returns is returned to the caller of invoke;
     * if the value has a primitive type, it is first appropriately wrapped in an object. However,
     * if the value has the type of an array of a primitive type, the elements of the array are
     * <i>not</i> wrapped in objects; in other words, an array of primitive type is returned. If the
     * underlying method return type is void, the invocation returns null.
     *
     * @param receiver the object the underlying method is invoked from
     * @param args the arguments used for the method call
     * @return the result of dispatching the method represented by this object on {@code receiver}
     *         with parameters {@code args}
     *
     *         IllegalAccessException if this {@code Method} object is enforcing Java language
     *         access control and the underlying method is inaccessible.
     *
     *
     *         IllegalArgumentException if the method is an instance method and the specified object
     *         argument is not an instance of the class or interface declaring the underlying method
     *         (or of a subclass or implementor thereof); if the number of actual and formal
     *         parameters differ; if an unwrapping conversion for primitive arguments fails; or if,
     *         after possible unwrapping, a parameter value cannot be converted to the corresponding
     *         formal parameter type by a method invocation conversion. // @exception
     *         InvocationTargetException if the underlying method throws an exception.
     *
     * 
     *         NullPointerException if the specified object is null and the method is an instance
     *         method. exception ExceptionInInitializerError if the initialization provoked by this
     *         method fails.
     */
    @Substitution
    public static @JavaType(Object.class) StaticObject invoke0(@JavaType(java.lang.reflect.Method.class) StaticObject guestMethod, @JavaType(Object.class) StaticObject receiver,
                    @JavaType(Object[].class) StaticObject args, @Inject Meta meta) {
        StaticObject curMethod = guestMethod;

        Method reflectedMethod = null;
        while (reflectedMethod == null) {
            reflectedMethod = (Method) meta.HIDDEN_METHOD_KEY.getHiddenObject(curMethod);
            if (reflectedMethod == null) {
                curMethod = meta.java_lang_reflect_Method_root.getObject(curMethod);
            }
        }
        Klass klass = meta.java_lang_reflect_Method_clazz.getObject(guestMethod).getMirrorKlass();

        if (klass == meta.java_lang_invoke_MethodHandle && (reflectedMethod.getName() == Name.invoke || reflectedMethod.getName() == Name.invokeExact)) {
            StaticObject cause = Meta.initExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "Cannot reflectively invoke MethodHandle.{invoke,invokeExact}");
            StaticObject invocationTargetException = Meta.initExceptionWithCause(meta.java_lang_reflect_InvocationTargetException, cause);
            throw meta.throwException(invocationTargetException);
        }

        StaticObject parameterTypes = meta.java_lang_reflect_Method_parameterTypes.getObject(guestMethod);
        StaticObject result = callMethodReflectively(meta, receiver, args, reflectedMethod, klass, parameterTypes);
        return result;
    }

    public static @JavaType(Object.class) StaticObject callMethodReflectively(Meta meta, @JavaType(Object.class) StaticObject receiver, @JavaType(Object[].class) StaticObject args, Method m,
                    Klass klass, @JavaType(Class[].class) StaticObject parameterTypes) {
        // Klass should be initialized if method is static, and could be delayed until method
        // invocation, according to specs. However, JCK tests that it is indeed always initialized
        // before doing anything, even if the method to be invoked is from another class.
        klass.safeInitialize();

        Method reflectedMethod = m;
        if (reflectedMethod.isRemovedByRedefition()) {
            reflectedMethod = m.getContext().getClassRedefinition().handleRemovedMethod(
                            reflectedMethod,
                            reflectedMethod.isStatic() ? reflectedMethod.getDeclaringKlass() : receiver.getKlass());

        }

        Method method;      // actual method to invoke
        Klass targetKlass;  // target klass, receiver's klass for non-static

        if (reflectedMethod.isStatic()) {
            // Ignore receiver argument;.
            method = reflectedMethod;
            targetKlass = klass;
        } else {
            if (StaticObject.isNull(receiver)) {
                throw meta.throwNullPointerException();
            }

            // Check class of receiver against class declaring method.
            if (!klass.isAssignableFrom(receiver.getKlass())) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "object is not an instance of declaring class");
            }

            // target klass is receiver's klass
            targetKlass = receiver.getKlass();
            // no need to resolve if method is private or <init>
            if (reflectedMethod.isPrivate() || Name._init_.equals(reflectedMethod.getName())) {
                method = reflectedMethod;
            } else {
                // resolve based on the receiver
                if (reflectedMethod.getDeclaringKlass().isInterface()) {
                    // resolve interface call
                    // Match resolution errors with those thrown due to reflection inlining
                    // Linktime resolution & IllegalAccessCheck already done by Class.getMethod()
                    method = reflectedMethod;
                    assert targetKlass instanceof ObjectKlass;
                    method = ((ObjectKlass) targetKlass).itableLookup(method.getDeclaringKlass(), method.getITableIndex());
                    if (method != null) {
                        // Check for abstract methods as well
                        if (!method.hasCode()) {
                            // new default: 65315
                            throw meta.throwExceptionWithCause(meta.java_lang_reflect_InvocationTargetException, Meta.initException(meta.java_lang_AbstractMethodError));
                        }
                    }
                } else {
                    // if the method can be overridden, we resolve using the vtable index.
                    method = reflectedMethod;
                    // VTable is live, use it
                    method = targetKlass.vtableLookup(method.getVTableIndex());
                    if (method != null) {
                        // Check for abstract methods as well
                        if (method.isAbstract()) {
                            // new default: 65315
                            throw meta.throwExceptionWithCause(meta.java_lang_reflect_InvocationTargetException, Meta.initException(meta.java_lang_AbstractMethodError));
                        }
                    }
                }
            }
        }

        // Comment from HotSpot:
        // I believe this is a ShouldNotGetHere case which requires
        // an internal vtable bug. If you ever get this please let Karen know.
        if (method == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, "please let Karen know");
        }

        int argsLen = StaticObject.isNull(args) ? 0 : args.length();
        final Symbol<Type>[] signature = method.getParsedSignature();

        // Check number of arguments.
        if (Signatures.parameterCount(signature, false) != argsLen) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "wrong number of arguments!");
        }

        Object[] adjustedArgs = new Object[argsLen];
        for (int i = 0; i < argsLen; ++i) {
            StaticObject arg = args.get(i);
            StaticObject paramTypeMirror = parameterTypes.get(i);
            Klass paramKlass = paramTypeMirror.getMirrorKlass();
            // Throws guest IllegallArgumentException if the parameter cannot be casted or widened.
            adjustedArgs[i] = checkAndWiden(meta, arg, paramKlass);
        }

        Object result;
        try {
            result = method.invokeDirect(receiver, adjustedArgs);
        } catch (EspressoException e) {
            throw meta.throwExceptionWithCause(meta.java_lang_reflect_InvocationTargetException, e.getExceptionObject());
        }

        if (reflectedMethod.getReturnKind() == JavaKind.Void) {
            return StaticObject.NULL;
        }
        if (reflectedMethod.getReturnKind().isPrimitive()) {
            return Meta.box(meta, result);
        }

        // Result is not void nor primitive, pass through.
        return (StaticObject) result;
    }

    public static class SharedNativeMetohdAccessorImpl extends SubstitutionNamesProvider {
        private static String[] NAMES = new String[]{
                        TARGET_SUN_REFLECT_NATIVEMETHODACCESSORIMPL,
                        TARGET_JDK_INTERNAL_REFLECT_NATIVEMETHODACCESSORIMPL
        };
        public static SubstitutionNamesProvider INSTANCE = new SharedNativeMetohdAccessorImpl();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

    private static final String TARGET_SUN_REFLECT_NATIVEMETHODACCESSORIMPL = "Target_sun_reflect_NativeMethodAccessorImpl";
    private static final String TARGET_JDK_INTERNAL_REFLECT_NATIVEMETHODACCESSORIMPL = "Target_jdk_internal_reflect_NativeMethodAccessorImpl";

}
