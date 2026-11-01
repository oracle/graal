/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/** Generates annotated polyglot proxies independently of runtime compilation support. */
final class PolyglotProxyFeature implements Feature {

    /* An interface-typed array cannot be materialized by registerAsImmutable during analysis. */
    private static Object[] proxies = new Object[0];
    private static Method[] exactEntryMethods = new Method[0];

    /** Called reflectively by the Native Image Truffle integration. */
    @SuppressWarnings("unused")
    private static void initializeNativeImageState(Class<?>[] candidates) {
        Arrays.sort(candidates, Comparator.comparing(PolyglotProxyClass::proxyKey));
        Map<String, Class<?>> classesByKey = new HashMap<>();
        Predicate<Method> exact = EngineAccessor.RUNTIME::supportsExactGeneratedProxyCall;
        Object[] generatedProxies = new Object[candidates.length];
        List<Method> generatedExactEntries = new ArrayList<>();
        int index = 0;
        for (Class<?> interfaceType : candidates) {
            String key = PolyglotProxyClass.proxyKey(interfaceType);
            Class<?> previous = classesByKey.putIfAbsent(key, interfaceType);
            if (previous != null && previous != interfaceType) {
                throw new IllegalStateException(String.format("Duplicate native-image generated proxy key %s for %s and %s", key, previous, interfaceType));
            }
            PolyglotProxyClass metadata = PolyglotProxyClass.registerGeneratedProxy(interfaceType, exact);
            generatedProxies[index++] = metadata;
            for (Method entry : metadata.getExactEntryMethods()) {
                if (entry != null) {
                    generatedExactEntries.add(entry);
                }
            }
        }
        proxies = generatedProxies;
        exactEntryMethods = generatedExactEntries.toArray(Method[]::new);
        EngineAccessor.RUNTIME.registerExactGeneratedProxyEntries(exactEntryMethods);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        for (Object value : proxies) {
            PolyglotProxyClass proxy = (PolyglotProxyClass) value;
            RuntimeReflection.register(proxy.getConstructor().getDeclaringClass());
            RuntimeReflection.register(proxy.getConstructor());
            RuntimeReflection.register(proxy.getMethods());
        }
        RuntimeReflection.register(exactEntryMethods);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        access.registerAsImmutable(proxies);
    }
}
