/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.redefinition.plugins.jdkcaches;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefinitionPluginHandler;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class JDKCacheRedefinitionPlugin extends InternalRedefinitionPlugin {

    private final List<WeakReference<StaticObject>> threadGroupContexts = new ArrayList<>(4);
    private Method flushFromCachesMethod;
    private Method removeBeanInfoMethod;

    @Override
    public void activate(EspressoContext espressoContext, RedefinitionPluginHandler handler) {
        super.activate(espressoContext, handler);
        flushFromCachesMethod = espressoContext.getMeta().java_beans_Introspector_flushFromCaches;
        removeBeanInfoMethod = espressoContext.getMeta().java_beans_ThreadGroupContext_removeBeanInfo;
    }

    @Override
    public void postClassRedefinition(ObjectKlass[] changedKlasses) {
        synchronized (threadGroupContexts) {
            for (ObjectKlass changedKlass : changedKlasses) {
                if (flushFromCachesMethod != null) {
                    flushFromCachesMethod.invokeDirectStatic(changedKlass.mirror());
                }
                Iterator<WeakReference<StaticObject>> iterator = threadGroupContexts.iterator();
                while (iterator.hasNext()) {
                    WeakReference<StaticObject> ref = iterator.next();
                    StaticObject context = ref.get();
                    if (context != null) {
                        removeBeanInfoMethod.invokeDirectSpecial(context, changedKlass.mirror());
                    } else {
                        // clean up list when context was reclaimed
                        iterator.remove();
                    }
                }
            }
        }
    }

    @TruffleBoundary
    public void registerThreadGroupContext(StaticObject context) {
        synchronized (threadGroupContexts) {
            threadGroupContexts.add(new WeakReference<>(context));
        }
    }
}
