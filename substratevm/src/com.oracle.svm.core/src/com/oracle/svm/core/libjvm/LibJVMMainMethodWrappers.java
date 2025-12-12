/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.libjvm;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.core.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

/**
 * The methods in this class are stand-ins for the JNI entrypoints of a Crema-loaded application
 * main-class. This can be removed once GR-71358 is implemented. This ImageSingleton is registered
 * only if LibJVMFeature is enabled.
 */
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class, other = PartiallyLayerAware.class)
public final class LibJVMMainMethodWrappers {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class Enabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(LibJVMMainMethodWrappers.class);
        }
    }

    private volatile Class<?> validMainClass;

    public static LibJVMMainMethodWrappers singleton() {
        return ImageSingletons.lookup(LibJVMMainMethodWrappers.class);
    }

    public static Class<?> patchMethodHolderClass(Class<?> origClazz) {
        if (!ImageSingletons.contains(LibJVMMainMethodWrappers.class)) {
            return origClazz;
        }

        Class<?> mainClass = singleton().validMainClass;
        if (mainClass != null && origClazz == mainClass) {
            return LibJVMMainMethodWrappers.class;
        }
        return origClazz;
    }

    public void setValidMainClass(Class<?> validMainClass) {
        this.validMainClass = validMainClass;
    }

    static void main(String[] args) {
        Class<?> mainClass = singleton().validMainClass;
        if (mainClass == null) {
            throw VMError.shouldNotReachHere("Calling main(String[] args) via JNI failed.");
        }

        Throwable throwable = null;
        try {
            var mainMethod = mainClass.getDeclaredMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            // Return Throwable as if not invoked via reflection
            Throwable cause = e.getCause();
            if (cause != null) {
                throwable = cause;
            } else {
                throwable = exceptionToError(mainClass, ".main(String[] args)", e);
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throwable = exceptionToError(mainClass, ".main(String[] args)", e);
        } catch (Throwable e) {
            throwable = e;
        }

        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    static void main() {
        Class<?> mainClass = singleton().validMainClass;
        if (mainClass == null) {
            throw VMError.shouldNotReachHere("Calling main() via JNI failed.");
        }

        Throwable throwable = null;
        try {
            var mainMethod = mainClass.getDeclaredMethod("main");
            mainMethod.invoke(null);
        } catch (InvocationTargetException e) {
            // Return Throwable as if not invoked via reflection
            Throwable cause = e.getCause();
            if (cause != null) {
                throwable = cause;
            } else {
                throwable = exceptionToError(mainClass, ".main()", e);
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throwable = exceptionToError(mainClass, ".main()", e);
        } catch (Throwable e) {
            throwable = e;
        }

        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    private static Throwable exceptionToError(Class<?> mainClass, String mainMethodStr, ReflectiveOperationException cause) {
        return new Error("Failed to call " + mainClass.getName() + mainMethodStr + " via reflection", cause);
    }
}
