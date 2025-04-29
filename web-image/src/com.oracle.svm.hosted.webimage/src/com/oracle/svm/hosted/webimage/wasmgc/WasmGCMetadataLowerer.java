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

package com.oracle.svm.hosted.webimage.wasmgc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.codegen.type.ClassMetadataLowerer;
import com.oracle.svm.webimage.api.Nothing;
import com.oracle.svm.webimage.wasmgc.WasmGCMetadata;

/**
 * In the WasmGC backend method metadata required by the JS interop is encoded in the name of the
 * exported function.
 * <p>
 * The exported name for a method is built up as follows:
 *
 * <pre>{@code
 * META.<declaring class> <method name> <return type> <parameter types separated by space>
 * }</pre>
 *
 * For example, {@link Throwable#toString()} might be encoded as follows: {@code META.5 toString 4}.
 * Where {@code 5} might be the encoding for {@link Throwable} and {@code 4} the encoding for
 * {@link String}, the return value.
 * <p>
 *
 * Class encoding is described in {@link WasmGCMetadata#registerClass(Class)} and the class instance
 * can be looked up at runtime using
 * {@link com.oracle.svm.webimage.wasmgc.WasmGCJSConversion#classFromEncoding(String)}.
 */
public class WasmGCMetadataLowerer {
    /**
     * Prefix for exports containing method metadata.
     * <p>
     * This constant is duplicated in JS code.
     */
    public static final String METADATA_PREFIX = "META.";
    /**
     * Prefix for exports containing the single abstract method of a class.
     * <p>
     * This constant is duplicated in JS code.
     */
    public static final String SAM_PREFIX = "SAM.";

    /**
     * Separator used between components of the metadata string.
     */
    public static final String SEPARATOR = " ";

    static String registerClassAndGetEncoding(HostedType type) {
        Class<?> effectiveType;
        if (type.isPrimitive() || type.getWrapped().isAnySubtypeInstantiated()) {
            effectiveType = type.getJavaClass();
        } else {
            /*
             * If the type is never instantiated, no instance of that class can exist and any type
             * checks should fail (unless the object is null). The Nothing class is designed to have
             * all type checks against it fail.
             */
            effectiveType = Nothing.class;
        }

        return WasmGCMetadata.registerClass(effectiveType);
    }

    /**
     * Encodes the method metadata for the given method.
     * <p>
     * Returns nothing if the method metadata for some reason does not need to be emitted.
     */
    static Optional<String> createMethodMetadata(HostedMethod m, String prefix) {
        HostedType type = m.getDeclaringClass();

        if (!ClassMetadataLowerer.isInstantiatedOrSubclassInstantiated(type) || !ClassMetadataLowerer.shouldEmitMetadata(m)) {
            return Optional.empty();
        }

        // TODO GR-62854 Also support and emit static methods
        if (m.isStatic()) {
            return Optional.empty();
        }

        ResolvedSignature<HostedType> signature = m.getSignature();
        List<String> parts = new ArrayList<>();

        parts.add(registerClassAndGetEncoding(type));
        parts.add(m.getName());
        parts.add(registerClassAndGetEncoding(signature.getReturnType()));

        for (int i = 0; i < signature.getParameterCount(false); i++) {
            HostedType paramType = signature.getParameterType(i);
            parts.add(registerClassAndGetEncoding(paramType));
        }

        return Optional.of(prefix + String.join(SEPARATOR, parts));
    }
}
