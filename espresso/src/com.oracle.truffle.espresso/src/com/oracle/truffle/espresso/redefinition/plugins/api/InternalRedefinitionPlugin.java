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
package com.oracle.truffle.espresso.redefinition.plugins.api;

import java.util.Collection;
import java.util.List;

import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.redefinition.plugins.impl.CachedRedefineObject;
import com.oracle.truffle.espresso.redefinition.plugins.impl.RedefinitionPluginHandler;
import com.oracle.truffle.espresso.redefinition.plugins.impl.UncachedRedefineObject;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

public abstract class InternalRedefinitionPlugin {

    private static final String constructorName = "<init>";

    private EspressoContext context;
    private RedefinitionPluginHandler redefinitionPluginHandler;

    public EspressoContext getContext() {
        return context;
    }

    public void activate(EspressoContext espressoContext, RedefinitionPluginHandler handler) {
        this.context = espressoContext;
        this.redefinitionPluginHandler = handler;
    }

    public abstract String getName();

    public abstract TriggerClass[] getTriggerClasses();

    public boolean reRunClinit(@SuppressWarnings("unused") KlassRef klass, @SuppressWarnings("unused") boolean changed) {
        return false;
    }

    public void fillExtraReloadClasses(@SuppressWarnings("unused") List<RedefineInfo> redefineInfos, @SuppressWarnings("unused") List<RedefineInfo> additional) {
        // default does nothing
    }

    public void postClassRedefinition(@SuppressWarnings("unused") KlassRef[] changedKlasses) {
        // default does nothing
    }

    public static RedefineObject createUncached(Object instance) {
        return new UncachedRedefineObject((StaticObject) instance);
    }

    public static RedefineObject createCached(Object instance) {
        return new CachedRedefineObject((StaticObject) instance);
    }

    public static RedefineObject createCached(KlassRef klass) {
        return new CachedRedefineObject(klass);
    }

    protected KlassRef getreflectedKlassType(Object classObject) {
        return context.getJdwpContext().getReflectedType(classObject);
    }

    protected void registerClassLoadAction(String className, ClassLoadAction action) {
        redefinitionPluginHandler.registerClassLoadAction(className, action);
    }

    protected void hookMethodExit(KlassRef klass, MethodLocator hookSpec, MethodHook.Kind kind, MethodExitHook onExitHook) {
        for (MethodRef method : klass.getDeclaredMethodRefs()) {
            if (method.getNameAsString().equals(hookSpec.getName()) && method.getSignatureAsString().equals(hookSpec.getSignature())) {
                method.addMethodHook(new RedefintionHook(onExitHook, kind));
                break;
            }
        }
    }

    protected void hookMethodEntry(KlassRef klass, MethodLocator hookSpec, MethodHook.Kind kind, MethodEntryHook onEntryHook) {
        for (MethodRef method : klass.getDeclaredMethodRefs()) {
            if (method.getNameAsString().equals(hookSpec.getName()) && method.getSignatureAsString().equals(hookSpec.getSignature())) {
                method.addMethodHook(new RedefintionHook(onEntryHook, kind));
                break;
            }
        }
    }

    protected void hookConstructor(KlassRef klass, MethodHook.Kind kind, MethodEntryHook onEntryHook) {
        for (MethodRef method : klass.getDeclaredMethodRefs()) {
            if (method.getNameAsString().equals(constructorName)) {
                method.addMethodHook(new RedefintionHook(onEntryHook, kind));
            }
        }
    }

    protected void clearCollection(@Host(Collection.class) RedefineObject object, String fieldName) throws NoSuchFieldException, NoSuchMethodException {
        RedefineObject collectionField = object.getInstanceField(fieldName);
        if (collectionField != null) {
            collectionField.invokeRaw("clear");
        }
    }
}
