/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

/**
 * JDK version independent interface to JDK services used by Truffle.
 */
final class TruffleJDKServices {

    private TruffleJDKServices() {
    }

    private static InternalError shouldNotReachHere() {
        throw new InternalError("JDK specific overlay for " + TruffleJDKServices.class.getName() + " missing");
    }

    /**
     * Exports all Truffle packages to a Truffle client.
     *
     * @param loader the loader used to load the classes of the client
     * @param moduleName the name of the module containing the client. This will be {@code null} if
     *            the client is not deployed as a module.
     */
    static void exportTo(ClassLoader loader, String moduleName) {
        throw shouldNotReachHere();
    }

    /**
     * Exports all Truffle packages to the module containing {@code client}.
     *
     * @param client class in a module that requires access to Truffle
     */
    static void exportTo(Class<?> client) {
        throw shouldNotReachHere();
    }

    /**
     * Gets the ordered list of loaders for {@link Service} providers.
     *
     * @param serviceClass defines service class
     */
    static <Service> List<Iterable<Service>> getTruffleRuntimeLoaders(Class<Service> serviceClass) {
        throw shouldNotReachHere();
    }

    /**
     * Ensures that the Truffle module declares a use of {@code service}.
     *
     * @param service a class describing a service about to be loaded by Truffle
     */
    static <S> void addUses(Class<S> service) {
        throw shouldNotReachHere();
    }

    /**
     * Returns the unnamed module configured for a classloader.
     *
     * @param classLoader the class loader to return the unnamed module for.
     */
    static Object getUnnamedModule(ClassLoader classLoader) {
        throw shouldNotReachHere();
    }

    /**
     * Returns <code>true</code> if the member class is visible to the given module.
     *
     * @param lookupModule the module to use for lookups.
     * @param memberClass the class or the declaring class of the member to check.
     */
    static boolean verifyModuleVisibility(Object lookupModule, Class<?> memberClass) {
        throw shouldNotReachHere();
    }

    /**
     * Returns <code>true</code> if the class is not part of the truffle framework.
     *
     * @param clazz the class to check.
     */
    static boolean isNonTruffleClass(Class<?> clazz) {
        throw shouldNotReachHere();
    }
}
