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

import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.api.MethodLocator;
import com.oracle.truffle.espresso.redefinition.plugins.api.RedefineObject;
import com.oracle.truffle.espresso.redefinition.plugins.api.TriggerClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JDKCacheRedefinitionPlugin extends InternalRedefinitionPlugin {

    public static final String INTROSPECTOR_CLASS = "java.beans.Introspector";
    public static final String FLUSH_CACHES_METHOD = "flushFromCaches";
    public static final String FLUSH_CACHES_SIG = "(Ljava/lang/Class;)V";
    private MethodRef flushFromCachesMethod;

    public static final String THREAD_GROUP_CONTEXT = "java.beans.ThreadGroupContext";
    public static final String REMOVE_BEAN_INFO = "removeBeanInfo";
    private List<RedefineObject> threadGroupContext = Collections.synchronizedList(new ArrayList<>(1));

    @Override
    public String getName() {
        return "JDK Cache Flushing Plugin";
    }

    @Override
    public TriggerClass[] getTriggerClasses() {
        TriggerClass[] triggerClasses = new TriggerClass[2];
        triggerClasses[0] = new TriggerClass(INTROSPECTOR_CLASS, this, klass -> {
            hookMethodEntry(klass, new MethodLocator(FLUSH_CACHES_METHOD, FLUSH_CACHES_SIG), MethodHook.Kind.ONE_TIME,
                            ((method, variables) -> flushFromCachesMethod = method));
        });
        triggerClasses[1] = new TriggerClass(THREAD_GROUP_CONTEXT, this, klass -> {
            hookConstructor(klass, MethodHook.Kind.INDEFINITE, ((method, variables) -> {
                threadGroupContext.add(InternalRedefinitionPlugin.createCached(variables[0].getValue()));
            }));
        });
        return triggerClasses;
    }

    @Override
    public void postClassRedefinition(KlassRef[] changedKlasses) {
        for (KlassRef changedKlass : changedKlasses) {
            Object guestKlass = getGuestClassInstance(changedKlass);
            if (flushFromCachesMethod != null) {
                flushFromCachesMethod.invokeMethod(null, new Object[]{guestKlass});
            }
            for (RedefineObject context : threadGroupContext) {
                try {
                    context.invoke(REMOVE_BEAN_INFO, InternalRedefinitionPlugin.createUncached(guestKlass));
                } catch (NoSuchMethodException e) {
                    // TODO - add logging
                }
            }
        }
    }
}
