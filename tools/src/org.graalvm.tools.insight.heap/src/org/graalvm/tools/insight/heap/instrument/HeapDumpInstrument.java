/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.heap.instrument;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.tools.insight.Insight;

@TruffleInstrument.Registration(id = "heap", name = "Heap Dump", internal = false, services = {Insight.SymbolProvider.class, Consumer.class})
public final class HeapDumpInstrument extends TruffleInstrument {

    static final OptionType<CacheReplacement> cacheReplacementType = new OptionType<>("flush|lru", (replacement) -> {
        switch (replacement.toLowerCase()) {
            case "lru":
                return CacheReplacement.LRU;
            case "flush":
                return CacheReplacement.FLUSH;
            default:
                throw new IllegalArgumentException("Unknown cache replacement type: " + replacement);
        }
    });

    @Option(stability = OptionStability.STABLE, name = "dump", help = "Output file to write the heap dump to", usageSyntax = "<file name>", category = OptionCategory.EXPERT) //
    static final OptionKey<String> DUMP = new OptionKey<>("");

    @Option(stability = OptionStability.STABLE, help = "Size of memory cache (>0:number of cached dump events, 0: no cache, <0: infinite cache) (default: 0)", usageSyntax = "<int>", category = OptionCategory.EXPERT) //
    static final OptionKey<Integer> cacheSize = new OptionKey<>(0);

    @Option(stability = OptionStability.STABLE, help = "Replacement policy when the cache is full (flush: flush to file, no data are lost, lru: drop the oldest event, old data are lost)  (default: flush)", //
                    usageSyntax = "flush|lru", category = OptionCategory.EXPERT) //
    static final OptionKey<CacheReplacement> cacheReplacement = new OptionKey<>(CacheReplacement.FLUSH, cacheReplacementType);

    @Option(stability = OptionStability.EXPERIMENTAL, help = "Expose heap.cache array (default: false)", category = OptionCategory.INTERNAL) //
    static final OptionKey<Boolean> exposeCache = new OptionKey<>(false);

    @Override
    protected void onCreate(Env env) {
        OptionValues options = env.getOptions();
        String path = DUMP.hasBeenSet(options) ? options.get(DUMP) : null;
        HeapObject obj = new HeapObject(env, path, options.get(cacheSize), options.get(cacheReplacement), options.get(exposeCache));
        env.registerService(maybeProxy(Insight.SymbolProvider.class, obj));
        env.registerService(maybeProxy(Consumer.class, obj));
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new HeapDumpInstrumentOptionDescriptors();
    }

    static <Interface> Interface maybeProxy(Class<Interface> type, Interface delegate) {
        if (TruffleOptions.AOT) {
            return delegate;
        } else {
            return proxy(type, delegate);
        }
    }

    private static <Interface> Interface proxy(Class<Interface> type, Interface delegate) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        };
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
