/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.resolver;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.resolver.meta.ClassType;
import com.oracle.truffle.espresso.resolver.meta.ErrorType;
import com.oracle.truffle.espresso.resolver.meta.FieldType;
import com.oracle.truffle.espresso.resolver.meta.MethodType;
import com.oracle.truffle.espresso.resolver.meta.RuntimeAccess;

public final class LinkResolver<R extends RuntimeAccess<C, M, F>, C extends ClassType<C, M, F>, M extends MethodType<C, M, F>, F extends FieldType<C, M, F>> {
    private final LinkResolverImpl<R, C, M, F> impl = new LinkResolverImpl<>();

    /**
     * Symbolically resolves a field.
     *
     * @param accessingKlass The class requesting resolution.
     * @param name The name of the field.
     * @param type The type of the field.
     * @param symbolicHolder The holder of the field, as described in the constant pool.
     * @param accessCheck Whether to perform access checks on the resolved field.
     * @param loadingConstraints Whether to check loading constraints on the resolved field.
     */
    public F resolveFieldSymbol(R meta, C accessingKlass,
                    Symbol<Name> name, Symbol<Type> type, C symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        return impl.resolveFieldSymbol(meta, accessingKlass, name, type, symbolicHolder, accessCheck, loadingConstraints);
    }

    /**
     * Resolve a field access site, given the symbolic resolution of the method. This ensures the
     * access is valid for the given site. In particular, this checks that static fields are only
     * accessed with static accesses, and that field writes to final fields are done only in the
     * constructor or class initializer.
     *
     * @param currentKlass The class in which the field access appears.
     * @param currentMethod The method in which the field access appears.
     * @param symbolicResolution The result of symbolic resolution of the field declared in the
     *            access site.
     * @param fieldAccessType The {@link FieldAccessType} representing the access site to resolve.
     */
    public F resolveFieldAccess(R meta, C currentKlass, M currentMethod, F symbolicResolution, FieldAccessType fieldAccessType) {
        return impl.resolveFieldAccess(meta, symbolicResolution, fieldAccessType, currentKlass, currentMethod);
    }

    /**
     * Symbolically resolves a method.
     *
     * @param accessingKlass The class requesting resolution.
     * @param name The name of the method.
     * @param signature The signature of the method.
     * @param symbolicHolder The holder of the method, as described in the constant pool.
     * @param accessCheck Whether to perform access checks on the resolved method.
     * @param loadingConstraints Whether to check loading constraints on the resolved method.
     */
    public M resolveMethodSymbol(R meta, C accessingKlass,
                    Symbol<Name> name, Symbol<Signature> signature, C symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return impl.resolveMethodSymbol(meta, accessingKlass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints);
    }

    /**
     * Resolve a call-site given the symbolic resolution of the method in the constant pool.
     *
     * @param currentKlass The class in which the call site to resolve appears.
     * @param symbolicResolution The result of the symbolic resolution of the method declared in the
     *            call site.
     * @param callSiteType The {@link CallSiteType} representing the call site to resolve.
     * @param symbolicHolder The declared holder for symbolic resolution. May differ from
     *            {@code symbolicResolution.getDeclaringKlass()}.
     */
    public ResolvedCall<C, M, F> resolveCallSite(R meta, C currentKlass, M symbolicResolution, CallSiteType callSiteType, C symbolicHolder) {
        return impl.resolveCallSite(meta, currentKlass, symbolicResolution, callSiteType, symbolicHolder);
    }
}

final class LinkResolverImpl<R extends RuntimeAccess<C, M, F>, C extends ClassType<C, M, F>, M extends MethodType<C, M, F>, F extends FieldType<C, M, F>> {

    private static final String AN_INTERFACE = "an interface";
    private static final String A_CLASS = "a class";
    private static final String STATIC = "static";
    private static final String NON_STATIC = "non-static";
    private static final String INIT = "<init>";
    private static final String CLINIT = "<clinit>";

    @TruffleBoundary
    public F resolveFieldSymbol(R meta, C accessingKlass,
                    Symbol<Name> name, Symbol<Type> type, C symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        F f = symbolicHolder.lookupField(name, type);
        if (f == null) {
            throw meta.throwError(ErrorType.NoSuchFieldError, "%s", name);
        }
        if (accessCheck) {
            meta.fieldAccessCheck(f, accessingKlass, symbolicHolder);
        }
        if (loadingConstraints) {
            meta.fieldLoadingConstraints(f, accessingKlass);
        }
        return f;
    }

    public F resolveFieldAccess(R meta, F field, FieldAccessType fieldAccessType,
                    C currentKlass, M currentMethod) {
        /*
         * PUTFIELD/GETFIELD: Otherwise, if the resolved field is a static field, putfield throws an
         * IncompatibleClassChangeError.
         *
         * PUTSTATIC/GETSTATIC: Otherwise, if the resolved field is not a static (class) field or an
         * interface field, putstatic throws an IncompatibleClassChangeError.
         */

        if (fieldAccessType.isStatic() != field.isStatic()) {
            throw meta.throwError(ErrorType.IncompatibleClassChangeError,
                            "Expected %s field %s.%s",
                            (fieldAccessType.isStatic()) ? STATIC : NON_STATIC,
                            field.getDeclaringClass().getJavaName(),
                            field.getName());
        }
        if (fieldAccessType.isPut()) {
            /*
             * PUTFIELD: Otherwise, if the field is final, it must be declared in the current class,
             * and the instruction must occur in an instance initialization method (<init>) of the
             * current class. Otherwise, an IllegalAccessError is thrown.
             *
             * PUTSTATIC: Otherwise, if the field is final, it must be declared in the current
             * class, and the instruction must occur in the <clinit> method of the current class.
             * Otherwise, an IllegalAccessError is thrown.
             */
            if (field.isFinalFlagSet()) {
                if (field.getDeclaringClass() != currentKlass) {
                    throw meta.throwError(ErrorType.IllegalAccessError,
                                    "Update to %s final field %s.%s attempted from a different class (%s) than the field's declaring class",
                                    (fieldAccessType.isStatic()) ? STATIC : NON_STATIC,
                                    field.getDeclaringClass().getJavaName(),
                                    field.getName(),
                                    currentKlass.getJavaName());
                }
                boolean enforceInitializerCheck = meta.enforceInitializerCheck(field);
                if (enforceInitializerCheck) {
                    if (!((fieldAccessType.isStatic() && currentMethod.isClassInitializer()) ||
                                    (!fieldAccessType.isStatic() && currentMethod.isConstructor()))) {
                        throw meta.throwError(ErrorType.IllegalAccessError,
                                        "Update to %s final field %s.%s attempted from a different method (%s) than the initializer method %s ",
                                        (fieldAccessType.isStatic()) ? STATIC : NON_STATIC,
                                        field.getDeclaringClass().getJavaName(),
                                        field.getName(),
                                        currentMethod.getName(),
                                        (fieldAccessType.isStatic()) ? CLINIT : INIT);
                    }
                }
            }
        }
        return field;
    }

    @TruffleBoundary
    public M resolveMethodSymbol(R meta, C accessingKlass, Symbol<Name> name,
                    Symbol<Signature> signature, C symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        M resolved;
        if (interfaceLookup != symbolicHolder.isInterface()) {
            String expected = interfaceLookup ? AN_INTERFACE : A_CLASS;
            String found = interfaceLookup ? A_CLASS : AN_INTERFACE;
            throw meta.throwError(ErrorType.IncompatibleClassChangeError, "Resolution failure for %s.\nIs %s, but %s was expected",
                            symbolicHolder.getJavaName(), found, expected);
        }

        if (symbolicHolder.isInterface()) {
            resolved = symbolicHolder.lookupInterfaceMethod(name, signature);
        } else {
            resolved = symbolicHolder.lookupMethod(name, signature);
        }
        if (resolved == null) {
            throw meta.throwError(ErrorType.NoSuchMethodError, symbolicHolder.getJavaName() + "." + name + signature);
        }
        if (accessCheck) {
            meta.methodAccessCheck(resolved, accessingKlass, symbolicHolder);
        }
        if (loadingConstraints && !meta.skipLoadingConstraints(resolved)) {
            meta.methodLoadingConstraints(resolved, accessingKlass);
        }
        return resolved;
    }

    public ResolvedCall<C, M, F> resolveCallSite(R meta, C currentKlass, M symbolicResolution,
                    CallSiteType callSiteType, C symbolicHolder) {
        M resolved = symbolicResolution;
        CallKind callKind;
        switch (callSiteType) {
            case Static:
                // Otherwise, if the resolved method is an instance method, the invokestatic
                // instruction throws an IncompatibleClassChangeError.
                if (!resolved.isStatic()) {
                    throw meta.throwError(ErrorType.IncompatibleClassChangeError, "Expected static method '%s.%s%s'",
                                    resolved.getDeclaringClass().getJavaName(),
                                    resolved.getName(),
                                    resolved.getRawSignature());
                }
                callKind = CallKind.STATIC;
                break;
            case Interface:
                // Otherwise, if the resolved method is static or (jdk8 or earlier) private, the
                // invokeinterface instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic() ||
                                (meta.getJavaVersion().java8OrEarlier() && resolved.isPrivate())) {
                    throw meta.throwError(ErrorType.IncompatibleClassChangeError, "Expected instance not static method '%s.%s%s'",
                                    resolved.getDeclaringClass().getJavaName(),
                                    resolved.getName(),
                                    resolved.getRawSignature());
                }
                if (resolved.isPrivate()) {
                    assert meta.getJavaVersion().java9OrLater() : "Should have thrown in previous check.";
                    // Interface private methods do not appear in itables.
                    callKind = CallKind.DIRECT;
                } else if (resolved.getDeclaringClass().isJavaLangObject()) {
                    // Can happen in old classfiles that calls j.l.Object methods on interfaces.
                    callKind = CallKind.VTABLE_LOOKUP;
                } else {
                    callKind = CallKind.ITABLE_LOOKUP;
                }
                break;
            case Virtual:
                // Otherwise, if the resolved method is a class (static) method, the invokevirtual
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    throw meta.throwError(ErrorType.IncompatibleClassChangeError, "Expected instance method '%s.%s%s'",
                                    resolved.getDeclaringClass().getJavaName(),
                                    resolved.getName(),
                                    resolved.getRawSignature());
                }
                if (resolved.isFinalFlagSet() || resolved.getDeclaringClass().isFinalFlagSet() || resolved.isPrivate()) {
                    callKind = CallKind.DIRECT;
                } else {
                    callKind = CallKind.VTABLE_LOOKUP;
                }
                break;
            case Special:
                // Otherwise, if the resolved method is an instance initialization method, and the
                // class in which it is declared is not the class symbolically referenced by the
                // instruction, a NoSuchMethodError is thrown.
                if (resolved.isConstructor()) {
                    if (resolved.getDeclaringClass().getName() != symbolicHolder.getName()) {
                        throw meta.throwError(ErrorType.NoSuchMethodError,
                                        "%s.%s%s",
                                        resolved.getDeclaringClass().getJavaName(),
                                        resolved.getName(),
                                        resolved.getRawSignature());
                    }
                }
                // Otherwise, if the resolved method is a class (static) method, the invokespecial
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    throw meta.throwError(ErrorType.IncompatibleClassChangeError, "Expected instance not static method '%s.%s%s'",
                                    resolved.getDeclaringClass().getJavaName(),
                                    resolved.getName(),
                                    resolved.getRawSignature());
                }
                // If all of the following are true, let C be the direct superclass of the current
                // class:
                //
                // * The resolved method is not an instance initialization method (&sect;2.9).
                //
                // * If the symbolic reference names a class (not an interface), then that class is
                // a superclass of the current class.
                //
                // * The ACC_SUPER flag is set for the class file (&sect;4.1). In Java SE 8 and
                // above, the Java Virtual Machine considers the ACC_SUPER flag to be set in every
                // class file, regardless of the actual value of the flag in the class file and the
                // version of the class file.
                if (!resolved.isConstructor()) {
                    if (!symbolicHolder.isInterface() &&
                                    symbolicHolder != currentKlass &&
                                    currentKlass.getSuperClass() != null &&
                                    symbolicHolder != currentKlass.getSuperClass() &&
                                    symbolicHolder.isAssignableFrom(currentKlass)) {
                        resolved = currentKlass.getSuperClass().lookupInstanceMethod(resolved.getName(), resolved.getRawSignature());
                    }
                }
                callKind = CallKind.DIRECT;
                break;
            default:
                throw meta.fatal("Resolution for %s", callSiteType);
        }
        return new ResolvedCall<>(callKind, resolved);
    }
}
