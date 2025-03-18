/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.resolver;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.shared.meta.ErrorType;
import com.oracle.truffle.espresso.shared.meta.FieldAccess;
import com.oracle.truffle.espresso.shared.meta.MethodAccess;
import com.oracle.truffle.espresso.shared.meta.RuntimeAccess;
import com.oracle.truffle.espresso.shared.meta.TypeAccess;

/**
 * Provides resolution capabilities according to the Java Virtual Machine Specification on behalf of
 * a VM.
 */
public final class LinkResolver {
    /**
     * Symbolically resolves a field. Throws the appropriate exception in case of errors.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param accessingKlass The class requesting resolution.
     * @param name The name of the field.
     * @param type The type of the field.
     * @param symbolicHolder The holder of the field, as described in the constant pool.
     * @param accessCheck Whether to perform access checks on the resolved field.
     * @param loadingConstraints Whether to check loading constraints on the resolved field.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> F resolveFieldSymbolOrThrow(
                    R runtime, C accessingKlass,
                    Symbol<Name> name, Symbol<Type> type, C symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        return resolveFieldSymbolImpl(runtime, accessingKlass, name, type, symbolicHolder, accessCheck, loadingConstraints, true);
    }

    /**
     * Symbolically resolves a field. Returns null in case of errors.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param accessingKlass The class requesting resolution.
     * @param name The name of the field.
     * @param type The type of the field.
     * @param symbolicHolder The holder of the field, as described in the constant pool.
     * @param accessCheck Whether to perform access checks on the resolved field.
     * @param loadingConstraints Whether to check loading constraints on the resolved field.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> F resolveFieldSymbolOrNull(
                    R runtime, C accessingKlass,
                    Symbol<Name> name, Symbol<Type> type, C symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        try {
            return resolveFieldSymbolImpl(runtime, accessingKlass, name, type, symbolicHolder, accessCheck, loadingConstraints, false);
        } catch (Throwable e) {
            if (runtime.getErrorType(e) != null) {
                throw runtime.fatal(e, "No exception was expected");
            }
            throw e;
        }
    }

    /**
     * Resolve a field access site, given the symbolic resolution of the method. This ensures the
     * access is valid for the given site. In particular, this checks that static fields are only
     * accessed with static accesses, and that field writes to final fields are done only in the
     * constructor or class initializer. In case of errors, throws the appropriate exception.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param symbolicResolution The result of symbolic resolution of the field declared in the
     *            access site.
     * @param fieldAccessType The {@link FieldAccessType} representing the access site to resolve.
     * @param currentKlass The class in which the field access appears. Usually the declaring class
     *            of * {@code currentMethod}.
     * @param currentMethod The method in which the field access appears.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     *
     * @see FieldAccessType
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> void checkFieldAccessOrThrow(
                    R runtime, F symbolicResolution, FieldAccessType fieldAccessType,
                    C currentKlass, M currentMethod) {
        checkFieldAccessImpl(runtime, symbolicResolution, fieldAccessType, currentKlass, currentMethod, true);
    }

    /**
     * Resolve a field access site, given the symbolic resolution of the method. This ensures the
     * access is valid for the given site. In particular, this checks that static fields are only
     * accessed with static accesses, and that field writes to final fields are done only in the
     * constructor or class initializer.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param symbolicResolution The result of symbolic resolution of the field declared in the
     *            access site.
     * @param fieldAccessType The {@link FieldAccessType} representing the access site to resolve.
     * @param currentKlass The class in which the field access appears. Usually the declaring class
     *            of * {@code currentMethod}.
     * @param currentMethod The method in which the field access appears.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     *
     * @see FieldAccessType
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> boolean checkFieldAccess(
                    R runtime, F symbolicResolution, FieldAccessType fieldAccessType,
                    C currentKlass, M currentMethod) {
        return checkFieldAccessImpl(runtime, symbolicResolution, fieldAccessType, currentKlass, currentMethod, false);
    }

    /**
     * Symbolically resolves a method. Throws the appropriate exception in case of errors.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param accessingKlass The class requesting resolution.
     * @param name The name of the method.
     * @param signature The signature of the method.
     * @param symbolicHolder The holder of the method, as described in the constant pool.
     * @param accessCheck Whether to perform access checks on the resolved method.
     * @param loadingConstraints Whether to check loading constraints on the resolved method.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> M resolveMethodSymbol(
                    R runtime, C accessingKlass,
                    Symbol<Name> name, Symbol<Signature> signature, C symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return resolveMethodSymbolImpl(runtime, accessingKlass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints, true);
    }

    /**
     * Symbolically resolves a method. Returns null in case of errors.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param accessingKlass The class requesting resolution.
     * @param name The name of the method.
     * @param signature The signature of the method.
     * @param symbolicHolder The holder of the method, as described in the constant pool.
     * @param accessCheck Whether to perform access checks on the resolved method.
     * @param loadingConstraints Whether to check loading constraints on the resolved method.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> M resolveMethodSymbolOrNull(
                    R runtime, C accessingKlass,
                    Symbol<Name> name, Symbol<Signature> signature, C symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        try {
            return resolveMethodSymbolImpl(runtime, accessingKlass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints, false);
        } catch (Throwable e) {
            if (runtime.getErrorType(e) != null) {
                throw runtime.fatal(e, "No exception was expected");
            }
            throw e;
        }
    }

    /**
     * Resolve a call-site given the symbolic resolution of the method in the constant pool. Throws
     * the appropriate exception in case of errors.
     * <p>
     * The returned {@link ResolvedCall} may be used to accurately dispatch at a call-site.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param currentKlass The class in which the call site to resolve appears.
     * @param symbolicResolution The result of the symbolic resolution of the method declared in the
     *            call site.
     * @param callSiteType The {@link CallSiteType} representing the call site to resolve.
     * @param symbolicHolder The declared holder for symbolic resolution, as seen in the constant
     *            pool. May differ from {@link MethodAccess#getDeclaringClass()
     *            symbolicResolution.getDeclaringClass()}.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     *
     * @see CallSiteType
     * @see CallKind
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> ResolvedCall<C, M, F> resolveCallSiteOrThrow(
                    R runtime, C currentKlass,
                    M symbolicResolution, CallSiteType callSiteType,
                    C symbolicHolder) {
        return resolveCallSiteImpl(runtime, currentKlass, symbolicResolution, callSiteType, symbolicHolder, true);
    }

    /**
     * Resolve a call-site given the symbolic resolution of the method in the constant pool. Returns
     * null in case of errors.
     * <p>
     * The returned {@link ResolvedCall} may be used to accurately dispatch at a call-site.
     *
     * @param runtime An object from which exception types are found and the language version will
     *            be inferred.
     * @param currentKlass The class in which the call site to resolve appears.
     * @param symbolicResolution The result of the symbolic resolution of the method declared in the
     *            call site.
     * @param callSiteType The {@link CallSiteType} representing the call site to resolve.
     * @param symbolicHolder The declared holder for symbolic resolution, as seen in the constant
     *            pool. May differ from {@link MethodAccess#getDeclaringClass()
     *            symbolicResolution.getDeclaringClass()}.
     *
     * @param <R> The class providing VM access.
     * @param <C> The class representing the VM-side java {@link Class}.
     * @param <M> The class representing the VM-side java {@link java.lang.reflect.Method}.
     * @param <F> The class representing the VM-side java {@link java.lang.reflect.Field}.
     *
     * @see CallSiteType
     * @see CallKind
     */
    public static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> ResolvedCall<C, M, F> resolveCallSiteOrNull(
                    R runtime, C currentKlass,
                    M symbolicResolution, CallSiteType callSiteType,
                    C symbolicHolder) {
        try {
            return resolveCallSiteImpl(runtime, currentKlass, symbolicResolution, callSiteType, symbolicHolder, false);
        } catch (Throwable e) {
            if (runtime.getErrorType(e) != null) {
                throw runtime.fatal(e, "No exception was expected");
            }
            throw e;
        }
    }

    // Implementation

    private LinkResolver() {
    }

    private static final String AN_INTERFACE = "an interface";
    private static final String A_CLASS = "a class";
    private static final String STATIC = "static";
    private static final String NON_STATIC = "non-static";
    private static final String INIT = "<init>";
    private static final String CLINIT = "<clinit>";

    private static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> F resolveFieldSymbolImpl(
                    R runtime, C accessingKlass,
                    Symbol<Name> name, Symbol<Type> type, C symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints, boolean throwExceptions) {
        F f = symbolicHolder.lookupField(name, type);
        if (f == null) {
            if (throwExceptions) {
                throw runtime.throwError(ErrorType.NoSuchFieldError, "%s", name);
            } else {
                return null;
            }
        }
        if (accessCheck && !f.accessChecks(accessingKlass, symbolicHolder)) {
            if (throwExceptions) {
                throw runtime.throwError(ErrorType.IllegalAccessError, "Class %s cannot access field %s#%s", accessingKlass.getJavaName(), f.getDeclaringClass().getJavaName(), name);
            }
            return null;
        }
        if (loadingConstraints) {
            try {
                f.loadingConstraints(accessingKlass, m -> {
                    throw runtime.throwError(ErrorType.LinkageError, m);
                });
            } catch (Throwable e) {
                if (runtime.getErrorType(e) != ErrorType.LinkageError) {
                    throw runtime.fatal(e, "Unexpected exception");
                }
                if (throwExceptions) {
                    throw e;
                }
                return null;
            }
        }
        return f;
    }

    private static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> boolean checkFieldAccessImpl(
                    R runtime, F field, FieldAccessType fieldAccessType,
                    C currentKlass, M currentMethod, boolean throwExceptions) {
        /*
         * PUTFIELD/GETFIELD: Otherwise, if the resolved field is a static field, putfield throws an
         * IncompatibleClassChangeError.
         *
         * PUTSTATIC/GETSTATIC: Otherwise, if the resolved field is not a static (class) field or an
         * interface field, putstatic throws an IncompatibleClassChangeError.
         */

        if (fieldAccessType.isStatic() != field.isStatic()) {
            if (throwExceptions) {
                throw runtime.throwError(ErrorType.IncompatibleClassChangeError,
                                "Expected %s field %s.%s",
                                (fieldAccessType.isStatic()) ? STATIC : NON_STATIC,
                                field.getDeclaringClass().getJavaName(),
                                field.getSymbolicName());
            }
            return false;
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
                    if (throwExceptions) {
                        throw runtime.throwError(ErrorType.IllegalAccessError,
                                        "Update to %s final field %s.%s attempted from a different class (%s) than the field's declaring class",
                                        (fieldAccessType.isStatic()) ? STATIC : NON_STATIC,
                                        field.getDeclaringClass().getJavaName(),
                                        field.getSymbolicName(),
                                        currentKlass.getJavaName());
                    }
                    return false;
                }
                boolean enforceInitializerCheck = field.shouldEnforceInitializerCheck();
                if (enforceInitializerCheck) {
                    if (!((fieldAccessType.isStatic() && currentMethod.isClassInitializer()) ||
                                    (!fieldAccessType.isStatic() && currentMethod.isConstructor()))) {
                        if (throwExceptions) {
                            throw runtime.throwError(ErrorType.IllegalAccessError,
                                            "Update to %s final field %s.%s attempted from a different method (%s) than the initializer method %s ",
                                            (fieldAccessType.isStatic()) ? STATIC : NON_STATIC,
                                            field.getDeclaringClass().getJavaName(),
                                            field.getSymbolicName(),
                                            currentMethod.getSymbolicName(),
                                            (fieldAccessType.isStatic()) ? CLINIT : INIT);
                        }
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> M resolveMethodSymbolImpl(R runtime,
                    C accessingKlass, Symbol<Name> name,
                    Symbol<Signature> signature, C symbolicHolder,
                    boolean interfaceLookup, boolean accessCheck, boolean loadingConstraints, boolean throwExceptions) {
        M resolved;
        if (interfaceLookup != symbolicHolder.isInterface()) {
            if (throwExceptions) {
                String expected = interfaceLookup ? AN_INTERFACE : A_CLASS;
                String found = interfaceLookup ? A_CLASS : AN_INTERFACE;
                throw runtime.throwError(ErrorType.IncompatibleClassChangeError, "Resolution failure for %s.\nIs %s, but %s was expected",
                                symbolicHolder.getJavaName(), found, expected);
            }
            return null;
        }

        if (symbolicHolder.isInterface()) {
            resolved = symbolicHolder.lookupInterfaceMethod(name, signature);
        } else {
            resolved = symbolicHolder.lookupMethod(name, signature);
        }
        if (resolved == null) {
            if (throwExceptions) {
                throw runtime.throwError(ErrorType.NoSuchMethodError, "%s.%s%s", symbolicHolder.getJavaName(), name, signature);
            }
            return null;
        }
        if (accessCheck && !resolved.accessChecks(accessingKlass, symbolicHolder)) {
            if (throwExceptions) {
                throw runtime.throwError(ErrorType.IllegalAccessError, "Class %s cannot access method %s#%s", accessingKlass.getJavaName(), resolved.getDeclaringClass().getJavaName(), name);
            }
            return null;
        }
        if (loadingConstraints && !resolved.shouldSkipLoadingConstraints()) {
            try {
                resolved.loadingConstraints(accessingKlass, m -> {
                    throw runtime.throwError(ErrorType.LinkageError, m);
                });
            } catch (Throwable e) {
                if (runtime.getErrorType(e) != ErrorType.LinkageError) {
                    throw runtime.fatal(e, "Unexpected exception");
                }
                if (throwExceptions) {
                    throw e;
                }
                return null;
            }
        }
        return resolved;
    }

    private static <R extends RuntimeAccess<C, M, F>, C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> ResolvedCall<C, M, F> resolveCallSiteImpl(
                    R runtime,
                    C currentKlass, M symbolicResolution,
                    CallSiteType callSiteType, C symbolicHolder, boolean throwExceptions) {
        M resolved = symbolicResolution;
        CallKind callKind;
        switch (callSiteType) {
            case Static:
                // Otherwise, if the resolved method is an instance method, the invokestatic
                // instruction throws an IncompatibleClassChangeError.
                if (!resolved.isStatic()) {
                    if (throwExceptions) {
                        throw runtime.throwError(ErrorType.IncompatibleClassChangeError, "Expected static method '%s.%s%s'",
                                        resolved.getDeclaringClass().getJavaName(),
                                        resolved.getSymbolicName(),
                                        resolved.getSymbolicSignature());
                    }
                    return null;
                }
                callKind = CallKind.STATIC;
                break;
            case Interface:
                // Otherwise, if the resolved method is static or (jdk8 or earlier) private, the
                // invokeinterface instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic() ||
                                (runtime.getJavaVersion().java8OrEarlier() && resolved.isPrivate())) {
                    if (throwExceptions) {
                        throw runtime.throwError(ErrorType.IncompatibleClassChangeError, "Expected instance not static method '%s.%s%s'",
                                        resolved.getDeclaringClass().getJavaName(),
                                        resolved.getSymbolicName(),
                                        resolved.getSymbolicSignature());
                    }
                    return null;
                }
                if (resolved.isPrivate()) {
                    assert runtime.getJavaVersion().java9OrLater() : "Should have thrown in previous check.";
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
                    if (throwExceptions) {
                        throw runtime.throwError(ErrorType.IncompatibleClassChangeError, "Expected instance method '%s.%s%s'",
                                        resolved.getDeclaringClass().getJavaName(),
                                        resolved.getSymbolicName(),
                                        resolved.getSymbolicSignature());
                    }
                    return null;
                }
                if (resolved.isFinalFlagSet() || resolved.getDeclaringClass().isFinalFlagSet() || resolved.isPrivate()) {
                    callKind = CallKind.DIRECT;
                } else if (resolved.hasVTableIndex()) {
                    callKind = CallKind.VTABLE_LOOKUP;
                } else {
                    // This case can only happen if implicit interface methods are not added to the
                    // vtables.
                    assert resolved.getDeclaringClass().isInterface();
                    callKind = CallKind.ITABLE_LOOKUP;
                }
                break;
            case Special:
                // Otherwise, if the resolved method is an instance initialization method, and the
                // class in which it is declared is not the class symbolically referenced by the
                // instruction, a NoSuchMethodError is thrown.
                if (resolved.isConstructor()) {
                    if (resolved.getDeclaringClass().getSymbolicName() != symbolicHolder.getSymbolicName()) {
                        if (throwExceptions) {
                            throw runtime.throwError(ErrorType.NoSuchMethodError,
                                            "%s.%s%s",
                                            resolved.getDeclaringClass().getJavaName(),
                                            resolved.getSymbolicName(),
                                            resolved.getSymbolicSignature());
                        }
                        return null;
                    }
                }
                // Otherwise, if the resolved method is a class (static) method, the invokespecial
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    if (throwExceptions) {
                        throw runtime.throwError(ErrorType.IncompatibleClassChangeError, "Expected instance not static method '%s.%s%s'",
                                        resolved.getDeclaringClass().getJavaName(),
                                        resolved.getSymbolicName(),
                                        resolved.getSymbolicSignature());
                    }
                    return null;
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
                                    currentKlass != null &&
                                    symbolicHolder != currentKlass &&
                                    currentKlass.getSuperClass() != null &&
                                    symbolicHolder != currentKlass.getSuperClass() &&
                                    symbolicHolder.isAssignableFrom(currentKlass)) {
                        resolved = currentKlass.getSuperClass().lookupInstanceMethod(resolved.getSymbolicName(), resolved.getSymbolicSignature());
                    }
                }
                callKind = CallKind.DIRECT;
                break;
            default:
                throw runtime.fatal("Resolution for %s", callSiteType);
        }
        return new ResolvedCall<>(callKind, resolved);
    }
}
