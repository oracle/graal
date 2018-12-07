/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;

/**
 * Helper class representing a single resolved receiver class that exports multiple libraries.
 */
final class ResolvedDispatch {

    private static final ConcurrentHashMap<Class<?>, ResolvedDispatch> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, ResolvedExports<?>[]> REGISTRY = new ConcurrentHashMap<>();

    // the root of every receiver class chain.
    private static final ResolvedDispatch OBJECT_RECEIVER = new ResolvedDispatch(null, Object.class);
    private final ResolvedDispatch parent;
    private final Class<?> dispatchClass;
    private final Map<Class<?>, ResolvedExports<?>> libraries;

    @SuppressWarnings({"hiding", "unchecked"})
    private ResolvedDispatch(ResolvedDispatch parent, Class<?> dispatchClass, ResolvedExports<?>... libs) {
        this.parent = parent;
        this.dispatchClass = dispatchClass;
        Map<Class<?>, ResolvedExports<?>> libraries = new LinkedHashMap<>();
        for (ResolvedExports<?> lib : libs) {
            libraries.put(lib.getLibrary(), lib);
        }
        this.libraries = libraries;
    }

    @SuppressWarnings("unchecked")
    <T extends Library> ResolvedExports<T> getLibrary(Class<T> libraryClass) {
        ResolvedExports<?> lib = libraries.get(libraryClass);
        if (lib == null && parent != null) {
            lib = parent.getLibrary(libraryClass);
        }
        return (ResolvedExports<T>) lib;
    }

    @TruffleBoundary
    static ResolvedDispatch lookup(Class<?> receiverClass) {
        ResolvedDispatch type = CACHE.get(receiverClass);
        if (type == null) {
            type = resolveClass(receiverClass);
        }
        return type;
    }

    static <T extends Library> void register(Class<?> receiverClass, ResolvedExports<?>... libs) {
        ResolvedExports<?>[] prevLibs = REGISTRY.put(receiverClass, libs);
        if (prevLibs != null) {
            throw new IllegalStateException("Receiver " + receiverClass + " is already registered.");
        }
        // eagerly resolve known receivers in AOT mode
        if (TruffleOptions.AOT) {
            lookup(receiverClass);
        }
    }

    @Override
    public String toString() {
        return "ResolvedReceiver[" + dispatchClass.getName() + "]";
    }

    Set<Class<?>> getLibraries() {
        return libraries.keySet();
    }

    private static boolean hasExports(Class<?> c) {
        return c.getAnnotationsByType(ExportLibrary.class).length > 0;
    }

    private static ResolvedDispatch resolveClass(Class<?> dispatchClass) {
        if (dispatchClass == null) {
            return OBJECT_RECEIVER;
        }
        ResolvedDispatch parent = resolveClass(dispatchClass.getSuperclass());
        ResolvedDispatch resolved;
        ResolvedExports<?>[] libs = REGISTRY.get(dispatchClass);
        if (libs == null && hasExports(dispatchClass)) {
            /*
             * We can omit loading classes in AOT mode as they are resolved eagerly using the
             * TruffleFeature. We can also omit if the type was already resolved.
             */
            if (!TruffleOptions.AOT) {
                loadGeneratedClass(dispatchClass);
                libs = REGISTRY.get(dispatchClass);
            }
            if (libs == null) {
                throw new AssertionError(String.format("Libraries for class '%s' could not be resolved. Not registered?", dispatchClass.getName()));
            }
        }

        if (libs != null) {
            resolved = new ResolvedDispatch(parent, dispatchClass, libs);
        } else {
            resolved = parent;
        }

        ResolvedDispatch concurrent = CACHE.putIfAbsent(dispatchClass, resolved);
        if (concurrent != null) {
            return concurrent;
        } else {
            return resolved;
        }
    }

    static void loadGeneratedClass(Class<?> currentReceiverClass) {
        String generatedClassName = currentReceiverClass.getPackage().getName() + "." + currentReceiverClass.getSimpleName() + "Gen";
        try {
            Class.forName(generatedClassName);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(String.format("Generated class '%s' for class '%s' not found. " +
                            "Did the Truffle annotation processor run?", generatedClassName, currentReceiverClass.getName()), e);
        }
    }

}
