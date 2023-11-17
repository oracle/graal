/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.ref;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.JavaVersion;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

import sun.misc.Unsafe;

public final class FinalizationSupport {

    private static final MethodHandle NEW_ESPRESSO_FINAL_REFERENCE;

    public static void ensureInitialized() {
        /* trigger static initializer */
    }

    static {
        if (EspressoOptions.InjectClasses) {
            try {
                byte[] publicFinalReferenceBytes = ClassAssembler.assemblePublicFinalReference();
                Class<?> publicFinalReference = injectClassInBootClassLoader(publicFinalReferenceBytes);
                EspressoError.guarantee("java.lang.ref.PublicFinalReference".equals(publicFinalReference.getName()),
                                "Injected class is not named java.lang.ref.PublicFinalReference");
                EspressoError.guarantee("java.lang.ref.FinalReference".equals(publicFinalReference.getSuperclass().getName()),
                                "Injected class does not subclass java.lang.ref.FinalReference");

                byte[] espressoFinalReferenceBytes = ClassAssembler.assembleEspressoFinalReference();
                Class<?> espressoFinalReference = loadClassInDedicatedClassLoader(EspressoReference.class.getClassLoader(), espressoFinalReferenceBytes);
                EspressoError.guarantee("com.oracle.truffle.espresso.ref.EspressoFinalReference".equals(espressoFinalReference.getName()),
                                "Injected class is not named com.oracle.truffle.espresso.ref.EspressoFinalReference");
                EspressoError.guarantee("java.lang.ref.PublicFinalReference".equals(espressoFinalReference.getSuperclass().getName()),
                                "Injected class does not subclass java.lang.ref.PublicFinalReference");

                NEW_ESPRESSO_FINAL_REFERENCE = MethodHandles.privateLookupIn(espressoFinalReference, MethodHandles.lookup()).findConstructor(espressoFinalReference,
                                MethodType.methodType(void.class, StaticObject.class, StaticObject.class, ReferenceQueue.class));
            } catch (Throwable t) {
                throw EspressoError.shouldNotReachHere("Error injecting while injecting classes to support finalization in the host (version " + JavaVersion.HOST_VERSION + ")", t);
            }
        } else {
            NEW_ESPRESSO_FINAL_REFERENCE = null;
        }
    }

    @TruffleBoundary
    public static EspressoReference createEspressoFinalReference(EspressoContext context, StaticObject self, StaticObject referent) {
        assert context.getEspressoEnv().UseHostFinalReference && canUseHostFinalReference();
        try {
            return (EspressoReference) NEW_ESPRESSO_FINAL_REFERENCE.invoke(self, referent, context.getReferenceQueue());
        } catch (StackOverflowError | OutOfMemoryError e) {
            throw e;
        } catch (Throwable e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    public static boolean canUseHostFinalReference() {
        return NEW_ESPRESSO_FINAL_REFERENCE != null;
    }

    @SuppressWarnings("deprecation")
    private static void setAccessible(AccessibleObject target, boolean value) throws Throwable {
        if (EspressoOptions.UnsafeOverride) {
            /*
             * Tested on Java 11, 16 and 17. In Java 12+, AccessibleObject.override was added to the
             * reflection blocklist. Illegal access checks can be circumvented by getting the
             * implementation lookup from MethodHandles with Unsafe.
             */
            Unsafe unsafe = UnsafeAccess.get();
            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            long implLookupFieldOffset = unsafe.staticFieldOffset(implLookupField);
            Object lookupStaticFieldBase = unsafe.staticFieldBase(implLookupField);
            MethodHandles.Lookup implLookup = (MethodHandles.Lookup) unsafe.getObject(lookupStaticFieldBase, implLookupFieldOffset);
            final MethodHandle overrideSetter = implLookup.findSetter(AccessibleObject.class, "override", boolean.class);
            // Force-enable access to j.l.ClassLoader#defineClass1.
            overrideSetter.invokeWithArguments(target, value);
        } else {
            // Try sane reflective access.
            target.setAccessible(value);
        }
    }

    private static Class<?> injectClassInBootClassLoader(byte[] classBytes) throws Throwable {
        EspressoError.guarantee(JavaVersion.HOST_VERSION.java11OrLater(), "Injection mechanism only supports host Java 11+.");
        // Inject class via j.l.ClassLoader#defineClass1 (private native method).
        Method defineClass1 = ClassLoader.class.getDeclaredMethod("defineClass1",
                        ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
        setAccessible(defineClass1, true);
        Class<?> definedClass = (Class<?>) defineClass1.invoke(null, null, null, classBytes, 0, classBytes.length, null, null);
        setAccessible(defineClass1, false);
        return definedClass;
    }

    private static Class<?> loadClassInDedicatedClassLoader(ClassLoader parent, byte[] bytes) {
        return new ClassLoader(parent) {
            final Class<?> definedClass = defineClass(null, bytes, 0, bytes.length);

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                if (definedClass.getName().equals(name)) {
                    return definedClass;
                } else {
                    return super.findClass(name);
                }
            }
        }.definedClass;
    }
}
