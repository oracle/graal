/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.tools.agentscript.impl.InsightFilter.Data;
import com.oracle.truffle.tools.agentscript.impl.InsightPerSource.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class InsightPerContext {
    final InsightInstrument insight;
    final TruffleContext context;
    private boolean initialized;
    private final Map<Key, List<Object>> functionsForBinding = new HashMap<>();

    InsightPerContext(InsightInstrument insight, TruffleContext context) {
        this.insight = insight;
        this.context = context;
    }

    synchronized boolean needsInitialization() {
        if (initialized) {
            return false;
        } else {
            initialized = true;
            return true;
        }
    }

    synchronized void register(Key key, Object function) {
        List<Object> arr = functionsForBinding.get(key);
        if (arr == null) {
            arr = new ArrayList<>();
            functionsForBinding.put(key, arr);
        }
        arr.add(function);
    }

    synchronized boolean unregister(Key key, Object function) {
        if (key == null) {
            boolean r = false;
            for (List<Object> arr : functionsForBinding.values()) {
                for (Iterator<Object> it = arr.iterator(); it.hasNext();) {
                    Data data = (Data) it.next();
                    if (function.equals(data.fn)) {
                        it.remove();
                        r = true;
                    }
                }
            }
            return r;
        } else {
            List<Object> arr = functionsForBinding.get(key);
            if (arr != null) {
                return arr.remove(function);
            }
        }
        return false;
    }

    synchronized Object[] functionsFor(Key key) {
        List<Object> arr = functionsForBinding.get(key);
        return arr == null ? new Object[0] : arr.toArray();
    }

    void onClosed(Key closedKey) {
        final InteropLibrary iop = InteropLibrary.getFactory().getUncached();
        for (Object closeFn : functionsFor(closedKey)) {
            try {
                iop.execute(closeFn);
            } catch (InteropException ex) {
                throw InsightException.raise(ex);
            }
        }
        synchronized (this) {
            functionsForBinding.clear();
        }
    }
}
