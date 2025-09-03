/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.standard;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.ToEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoLinkResolver;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.resolver.CallKind;
import com.oracle.truffle.espresso.shared.resolver.CallSiteType;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNamesProvider;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

/**
 * This substitution is merely for performance reasons, to avoid the deep-dive to native. libjava
 * hardwires {@link #invoke0} to JVM_InvokeMethod in libjvm.
 */
@EspressoSubstitutions(nameProvider = Target_sun_reflect_NativeMethodAccessorImpl.SharedNativeMetohdAccessorImpl.class)
public final class Target_sun_reflect_NativeMethodAccessorImpl {
    private static final String[] NAMES = {
                    "Lsun/reflect/NativeMethodAccessorImpl;",
                    "Ljdk/internal/reflect/NativeMethodAccessorImpl;",
                    "Ljdk/internal/reflect/DirectMethodHandleAccessor$NativeAccessor;"
    };

    private Target_sun_reflect_NativeMethodAccessorImpl() {
    }

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
     * return the result of dispatching the method represented by this object on {@code receiver}
     * with parameters {@code args}
     *
     * IllegalAccessException if this {@code Method} object is enforcing Java language access
     * control and the underlying method is inaccessible.
     *
     *
     * IllegalArgumentException if the method is an instance method and the specified object
     * argument is not an instance of the class or interface declaring the underlying method (or of
     * a subclass or implementor thereof); if the number of actual and formal parameters differ; if
     * an unwrapping conversion for primitive arguments fails; or if, after possible unwrapping, a
     * parameter value cannot be converted to the corresponding formal parameter type by a method
     * invocation conversion. // @exception InvocationTargetException if the underlying method
     * throws an exception.
     *
     *
     * NullPointerException if the specified object is null and the method is an instance method.
     * exception ExceptionInInitializerError if the initialization provoked by this method fails.
     */
    @Substitution(methodName = "invoke0")
    abstract static class Invoke0 extends SubstitutionNode {
        public abstract @JavaType(Object.class) StaticObject execute(
                        @JavaType(java.lang.reflect.Method.class) StaticObject guestMethod,
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject args,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta);

        @Specialization
        public @JavaType(Object.class) StaticObject invoke(
                        @JavaType(java.lang.reflect.Method.class) StaticObject guestMethod,
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Object[].class) StaticObject args,
                        @Inject EspressoLanguage language,
                        @Inject Meta meta,
                        @Cached ToEspressoNode.DynamicToEspresso toEspressoNode) {
            return invoke0(guestMethod, receiver, args, language, meta, toEspressoNode);
        }
    }

    static @JavaType(Object.class) StaticObject invoke0(
                    @JavaType(java.lang.reflect.Method.class) StaticObject guestMethod, @JavaType(Object.class) StaticObject receiver,
                    @JavaType(Object[].class) StaticObject args,
                    EspressoLanguage language, Meta meta,
                    ToEspressoNode.DynamicToEspresso toEspressoNode) {
        Method reflectedMethod = Method.getHostReflectiveMethodRoot(guestMethod, meta);
        Klass klass = meta.java_lang_reflect_Method_clazz.getObject(guestMethod).getMirrorKlass(meta);

        if ((klass == meta.java_lang_invoke_MethodHandle) && ((reflectedMethod.getName() == Names.invoke) || (reflectedMethod.getName() == Names.invokeExact))) {
            StaticObject cause = Meta.initExceptionWithMessage(meta.java_lang_UnsupportedOperationException, "Cannot reflectively invoke MethodHandle.{invoke,invokeExact}");
            StaticObject invocationTargetException = Meta.initExceptionWithCause(meta.java_lang_reflect_InvocationTargetException, cause);
            throw meta.throwException(invocationTargetException);
        }

        StaticObject parameterTypes = meta.java_lang_reflect_Method_parameterTypes.getObject(guestMethod);
        return callMethodReflectively(language, meta, receiver, args, reflectedMethod, klass, parameterTypes, toEspressoNode);
    }

    public static @JavaType(Object.class) StaticObject callMethodReflectively(
                    EspressoLanguage language, Meta meta,
                    @JavaType(Object.class) StaticObject receiver,
                    @JavaType(Object[].class) StaticObject args,
                    Method m,
                    Klass klass, @JavaType(Class[].class) StaticObject parameterTypes, ToEspressoNode.DynamicToEspresso toEspressoNode) {
        // Klass should be initialized if method is static, and could be delayed until method
        // invocation, according to specs. However, JCK tests that it is indeed always initialized
        // before doing anything, even if the method to be invoked is from another class.
        klass.safeInitialize();

        Method reflectedMethod = m;
        if (reflectedMethod.isRemovedByRedefinition()) {
            try {
                reflectedMethod = m.getContext().getClassRedefinition().handleRemovedMethod(
                                reflectedMethod,
                                reflectedMethod.isStatic() ? reflectedMethod.getDeclaringKlass() : receiver.getKlass());
            } catch (EspressoException e) {
                throw meta.throwExceptionWithCause(meta.java_lang_reflect_InvocationTargetException, e.getGuestException());
            }
        }

        CallSiteType callSiteType;
        if (reflectedMethod.isStatic()) {
            callSiteType = CallSiteType.Static;
        } else if (reflectedMethod.getDeclaringKlass().isInterface()) {
            callSiteType = CallSiteType.Interface;
        } else if (reflectedMethod.isConstructor()) {
            callSiteType = CallSiteType.Special;
        } else {
            callSiteType = CallSiteType.Virtual;
        }
        ResolvedCall<Klass, Method, Field> resolvedCall = EspressoLinkResolver.resolveCallSiteOrThrow(
                        meta.getContext(),
                        null, // No current class.
                        reflectedMethod, callSiteType, klass);

        if (resolvedCall.getCallKind() != CallKind.STATIC) {
            if (StaticObject.isNull(receiver)) {
                throw meta.throwNullPointerException();
            }

            // Check class of receiver against class declaring method.
            if (!klass.isAssignableFrom(receiver.getKlass())) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "object is not an instance of declaring class");
            }
        }

        Object[] adjustedArgs = makeArgs(resolvedCall, parameterTypes, receiver, args, language, meta, toEspressoNode);

        Object result;
        try {
            result = Method.call(resolvedCall, adjustedArgs);
        } catch (EspressoException e) {
            throw meta.throwExceptionWithCause(meta.java_lang_reflect_InvocationTargetException, e.getGuestException());
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

    private static Object[] makeArgs(ResolvedCall<Klass, Method, Field> resolvedCall, StaticObject parameterTypes,
                    StaticObject receiver, StaticObject args,
                    EspressoLanguage language, Meta meta, ToEspressoNode.DynamicToEspresso toEspressoNode) {
        boolean isForeignArray = args.isForeignObject();
        Object rawForeign = null;
        InteropLibrary interop = null;

        int argsLen;
        if (isForeignArray) {
            rawForeign = args.rawForeignObject(language);
            interop = InteropLibrary.getUncached(rawForeign);
            try {
                argsLen = (int) interop.getArraySize(rawForeign);
            } catch (UnsupportedMessageException e) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "unexpected foreign object!");
            }
        } else {
            argsLen = StaticObject.isNull(args) ? 0 : args.length(language);
        }

        final Symbol<Type>[] signature = resolvedCall.getResolvedMethod().getParsedSignature();

        // Check number of arguments.
        if (SignatureSymbols.parameterCount(signature) != argsLen) {
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "wrong number of arguments!");
        }

        int argsOffset = 0;
        int adjustedArgsLen = argsLen;
        if (!resolvedCall.getCallKind().isStatic()) {
            adjustedArgsLen += 1;
            argsOffset = 1;
        }
        Object[] adjustedArgs = new Object[adjustedArgsLen];
        if (!resolvedCall.getCallKind().isStatic()) {
            adjustedArgs[0] = receiver;
        }
        for (int i = 0; i < argsLen; ++i) {
            StaticObject paramTypeMirror = parameterTypes.get(language, i);
            Klass paramKlass = paramTypeMirror.getMirrorKlass(meta);
            Object arg;
            if (isForeignArray) {
                try {
                    adjustedArgs[i + argsOffset] = toEspressoNode.execute(interop.readArrayElement(rawForeign, i), paramKlass);
                } catch (UnsupportedTypeException | UnsupportedMessageException | InvalidArrayIndexException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "unable to read argument from foreign object!");
                }
            } else {
                arg = args.get(language, i);
                // Throws guest IllegalArgumentException if the
                // parameter cannot be casted or widened.
                adjustedArgs[i + argsOffset] = checkAndWiden(meta, (StaticObject) arg, paramKlass);
            }
        }
        return adjustedArgs;
    }

    public static class SharedNativeMetohdAccessorImpl extends SubstitutionNamesProvider {
        public static SubstitutionNamesProvider INSTANCE = new SharedNativeMetohdAccessorImpl();

        @Override
        public String[] substitutionClassNames() {
            return NAMES;
        }
    }

}
