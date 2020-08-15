/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.impl;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * JDK 8 implementation of {@code TruffleJDKServices}.
 */
final class TruffleJDKServices {

    @SuppressWarnings("unused")
    static void exportTo(ClassLoader loader, String moduleName) {
        // No need to do anything on JDK 8
    }

    @SuppressWarnings("unused")
    static void exportTo(Class<?> client) {
        // No need to do anything on JDK 8
    }

    @SuppressWarnings("unused")
    static <S> void addUses(Class<S> service) {
        // No need to do anything on JDK 8
    }

    /**
     * Gets the ordered list of loaders for {@link TruffleRuntimeAccess} providers.
     */
    static <Service> List<Iterable<Service>> getTruffleRuntimeLoaders(Class<Service> serviceClass) {
        // public static List<Iterable<TruffleRuntimeAccess>> getTruffleRuntimeLoaders() {
        Iterable<Service> jvmciProviders = getJVMCIProviders(serviceClass);
        if (Boolean.getBoolean("truffle.TrustAllTruffleRuntimeProviders")) {
            ServiceLoader<Service> standardProviders = ServiceLoader.load(serviceClass);
            return Arrays.asList(jvmciProviders, standardProviders);
        } else {
            return Collections.singletonList(jvmciProviders);
        }
    }

    /**
     * Gets the providers of {@code Service} available on the JVMCI class path.
     */
    private static <Service> Iterable<Service> getJVMCIProviders(Class<Service> serviceClass) {
        ClassLoader cl = TruffleJDKServices.class.getClassLoader();
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        while (cl != null) {
            if (cl == scl) {
                /*
                 * If Truffle can see the app class loader, then it is not on the JVMCI class path.
                 * This means providers of TruffleRuntimeAccess on the JVMCI class path must be
                 * ignored as they will bind to the copy of Truffle resolved on the JVMCI class
                 * path. Failing to ignore will result in ServiceConfigurationErrors (e.g.,
                 * https://github.com/oracle/graal/issues/385#issuecomment-385313521).
                 */
                return null;
            }
            cl = cl.getParent();
        }

        Class<?> jvmciServicesClass;
        try {
            // Access JVMCI via reflection to avoid a hard
            // dependency on JVMCI from Truffle.
            jvmciServicesClass = Class.forName("jdk.vm.ci.services.Services");
        } catch (ClassNotFoundException e) {
            // JVMCI is unavailable so the default TruffleRuntime will be used
            return null;
        }

        return reflectiveServiceLoaderLoad(jvmciServicesClass, serviceClass);
    }

    @SuppressWarnings("unchecked")
    private static <Service> Iterable<Service> reflectiveServiceLoaderLoad(Class<?> servicesClass, Class<Service> serviceClass) {
        try {
            Method m = servicesClass.getDeclaredMethod("load", Class.class);
            return (Iterable<Service>) m.invoke(null, serviceClass);
        } catch (Throwable e) {
            throw new InternalError(e);
        }
    }

    static Object getUnnamedModule(@SuppressWarnings("unused") ClassLoader classLoader) {
        return null;
    }

    static boolean verifyModuleVisibility(Object currentModule, @SuppressWarnings("unused") Class<?> memberClass) {
        assert currentModule == null;
        return true;
    }

    static boolean isNonTruffleClass(Class<?> clazz) {
        // classes on the boot loader should not be cleared
        return clazz.getClassLoader() != null;
    }
}
