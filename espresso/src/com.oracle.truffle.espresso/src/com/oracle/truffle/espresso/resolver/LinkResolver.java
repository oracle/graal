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

import static com.oracle.truffle.espresso.meta.EspressoError.cat;

import java.util.Locale;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.constantpool.Resolution;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;

public final class LinkResolver {
    /**
     * Symbolically resolves a method.
     *
     * @param meta
     * @param accessingKlass The class requesting resolution.
     * @param name The name of the method.
     * @param signature The signature of the method.
     * @param symbolicHolder The holder of the method, as described in the constant pool.
     * @param accessCheck Whether to perform access checks on the resolved method.
     * @param loadingConstraints Whether to check loading constraints on the resolved method.
     */
    public static Method resolveSymbol(Meta meta, ObjectKlass accessingKlass,
                    Symbol<Name> name, Symbol<Signature> signature, Klass symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolverImpl.resolveSymbol(meta, accessingKlass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints);
    }

    /**
     * Resolve a call-site given the symbolic resolution of the method in the constant pool.
     *
     * @param meta
     * @param currentKlass The class in which the call site to resolve appears.
     * @param symbolicResolution The result of the symbolic resolution of the method declared in the
     *            call site.
     * @param callSiteType The {@link CallSiteType} representing the call site to resolve.
     * @param symbolicHolder The declared holder for symbolic resolution. May differ from
     *            {@code symbolicResolution.getDeclaringKlass()}.
     */
    public static ResolvedCall resolveCallSite(Meta meta, Klass currentKlass, Method symbolicResolution, CallSiteType callSiteType, Klass symbolicHolder) {
        return LinkResolverImpl.resolveCallSite(meta, currentKlass, symbolicResolution, callSiteType, symbolicHolder);
    }

    // Only static
    private LinkResolver() {
    }
}

final class LinkResolverImpl {

    private static final String AN_INTERFACE = "an interface";
    private static final String A_CLASS = "a class";

    @TruffleBoundary
    public static Method resolveSymbol(Meta meta, ObjectKlass accessingKlass, Symbol<Name> name, Symbol<Signature> signature, Klass symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        Method resolved;
        if (interfaceLookup != symbolicHolder.isInterface()) {
            String expected = interfaceLookup ? AN_INTERFACE : A_CLASS;
            String found = interfaceLookup ? A_CLASS : AN_INTERFACE;
            meta.throwExceptionWithMessage(meta.java_lang_IncompatibleClassChangeError, cat("Resolution failure for ", symbolicHolder.getExternalName(), ".\n",
                            "Is ", found, ", but ", expected, " was expected."));
        }

        if (symbolicHolder.isInterface()) {
            resolved = ((ObjectKlass) symbolicHolder).resolveInterfaceMethod(name, signature);
        } else {
            resolved = symbolicHolder.lookupMethod(name, signature);
        }
        if (resolved == null) {
            throw meta.throwExceptionWithMessage(meta.java_lang_NoSuchMethodError, symbolicHolder.getNameAsString() + "." + name + signature);
        }
        if (accessCheck) {
            Resolution.memberDoAccessCheck(accessingKlass, symbolicHolder, resolved, meta);
        }
        if (loadingConstraints && !resolved.isPolySignatureIntrinsic()) {
            resolved.checkLoadingConstraints(accessingKlass.getDefiningClassLoader(), resolved.getDeclaringKlass().getDefiningClassLoader());
        }
        return resolved;
    }

    public static ResolvedCall resolveCallSite(Meta meta, Klass currentKlass, Method symbolicResolution, CallSiteType callSiteType, Klass symbolicHolder) {
        Method resolved = symbolicResolution;
        CallKind callKind;
        switch (callSiteType) {
            case Static:
                // Otherwise, if the resolved method is an instance method, the invokestatic
                // instruction throws an IncompatibleClassChangeError.
                if (!resolved.isStatic()) {
                    throw throwBoundary(meta, meta.java_lang_IncompatibleClassChangeError, "Expected static method '%s.%s%s'",
                                    resolved.getDeclaringKlass().getName(),
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
                    throw throwBoundary(meta, meta.java_lang_IncompatibleClassChangeError, "Expected instance method '%s.%s%s'",
                                    resolved.getDeclaringKlass().getName(),
                                    resolved.getName(),
                                    resolved.getRawSignature());
                }
                if (resolved.getITableIndex() < 0) {
                    if (resolved.isPrivate()) {
                        assert meta.getJavaVersion().java9OrLater();
                        // Interface private methods do not appear in itables.
                        callKind = CallKind.DIRECT;
                    } else {
                        assert resolved.getVTableIndex() >= 0;
                        // Can happen in old classfiles that calls j.l.Object on interfaces.
                        callKind = CallKind.VTABLE_LOOKUP;
                    }
                } else {
                    callKind = CallKind.ITABLE_LOOKUP;
                }
                break;
            case Virtual:
                // Otherwise, if the resolved method is a class (static) method, the invokevirtual
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    throw throwBoundary(meta, meta.java_lang_IncompatibleClassChangeError, "Expected instance not static method '%s.%s%s'",
                                    resolved.getDeclaringKlass().getName(),
                                    resolved.getName(),
                                    resolved.getRawSignature());
                }
                if (resolved.isFinalFlagSet() || resolved.getDeclaringKlass().isFinalFlagSet() || resolved.isPrivate()) {
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
                    if (resolved.getDeclaringKlass().getName() != symbolicHolder.getName()) {
                        throw throwBoundary(meta, meta.java_lang_NoSuchMethodError,
                                        "%s.%s%s",
                                        resolved.getDeclaringKlass().getName(),
                                        resolved.getName(),
                                        resolved.getRawSignature());
                    }
                }
                // Otherwise, if the resolved method is a class (static) method, the invokespecial
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    throw throwBoundary(meta, meta.java_lang_IncompatibleClassChangeError, "Expected instance not static method '%s.%s%s'",
                                    resolved.getDeclaringKlass().getName(),
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
                                    currentKlass.getSuperKlass() != null &&
                                    symbolicHolder != currentKlass.getSuperKlass() &&
                                    symbolicHolder.isAssignableFrom(currentKlass)) {
                        resolved = currentKlass.getSuperKlass().lookupMethod(resolved.getName(), resolved.getRawSignature(), Klass.LookupMode.INSTANCE_ONLY);
                    }
                }
                callKind = CallKind.DIRECT;
                break;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.unimplemented("Resolution for " + callSiteType);
        }
        return new ResolvedCall(callKind, resolved);
    }

    @TruffleBoundary
    private static RuntimeException throwBoundary(Meta meta, ObjectKlass exceptionKlass, String messageFormat, Object... args) {
        throw meta.throwExceptionWithMessage(exceptionKlass, String.format(Locale.ENGLISH, messageFormat, args));
    }
}
