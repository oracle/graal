/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.nodes.commands.AddPathToBindingsCache;
import com.oracle.truffle.espresso.nodes.commands.ReferenceProcessCache;
import com.oracle.truffle.espresso.runtime.dispatch.messages.InteropMessageFactory;

public class LazyContextCaches extends ContextAccessImpl {
    // region Command processing

    public LazyContextCaches(EspressoContext context) {
        super(context);
    }

    @CompilationFinal //
    private volatile ReferenceProcessCache referenceProcessCache = null;
    @CompilationFinal //
    private volatile AddPathToBindingsCache addPathToBindingsCache = null;

    public ReferenceProcessCache getReferenceProcessCache() {
        ReferenceProcessCache cache = referenceProcessCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                cache = referenceProcessCache;
                if (cache == null) {
                    cache = (referenceProcessCache = new ReferenceProcessCache(getContext()));
                }
            }
        }
        return cache;
    }

    public AddPathToBindingsCache getAddPathToBindingsCache() {
        AddPathToBindingsCache cache = addPathToBindingsCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                cache = addPathToBindingsCache;
                if (cache == null) {
                    cache = (addPathToBindingsCache = new AddPathToBindingsCache(getContext()));
                }
            }
        }
        return cache;
    }

    // endregion Command processing

    // region Shared Interop

    // Maps interop messages to their implementation. The key depends on both the interop message
    // and the dispatch class of the receiver.
    private final ConcurrentHashMap<InteropMessageFactory.Key, CallTarget> sharedInteropCache = new ConcurrentHashMap<>();

    public CallTarget getInteropMessage(String message, Class<?> dispatch, Supplier<CallTarget> supplier) {
        InteropMessageFactory.Key key = new InteropMessageFactory.Key(dispatch, message);
        return sharedInteropCache.computeIfAbsent(key, (unused) -> supplier.get());
    }
    // endregion Shared Interop
}
