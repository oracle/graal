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
package com.oracle.truffle.espresso.redefinition.plugins.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.jdwp.api.RedefineInfo;
import com.oracle.truffle.espresso.jdwp.impl.DebuggerController;
import com.oracle.truffle.espresso.redefinition.DefineKlassListener;
import com.oracle.truffle.espresso.redefinition.plugins.api.ClassLoadAction;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class RedefinitionPluginHandler implements RedefineListener, DefineKlassListener {

    private final EspressoContext context;

    // internal plugins are immediately activated during context
    // initialization, so no need for synchronization on this set
    private final Set<InternalRedefinitionPlugin> internalPlugins = new HashSet<>(1);
    private final Map<Symbol<Symbol.Type>, List<ClassLoadAction>> classLoadActions = new HashMap<>();

    // The guest language HotSwap plugin handler passed
    // onto us if guest plugins are present at runtime.
    private ExternalPluginHandler externalPluginHandler;

    private RedefinitionPluginHandler(EspressoContext espressoContext) {
        this.context = espressoContext;
    }

    @TruffleBoundary
    public void registerClassLoadAction(String className, ClassLoadAction action) {
        synchronized (classLoadActions) {
            Symbol<Symbol.Type> type = context.getTypes().fromClassGetName(className);
            List<ClassLoadAction> list = classLoadActions.get(type);
            if (list == null) {
                list = new ArrayList<>();
                classLoadActions.put(type, list);
            }
            list.add(action);
        }
    }

    public void registerExternalHotSwapHandler(StaticObject handler) {
        if (handler != null) {
            externalPluginHandler = ExternalPluginHandler.create(handler);
        }
    }

    public static RedefinitionPluginHandler create(EspressoContext espressoContext) {
        // we use ServiceLoader to load all Espresso internal Plugins
        RedefinitionPluginHandler handler = new RedefinitionPluginHandler(espressoContext);
        ServiceLoader<InternalRedefinitionPlugin> serviceLoader = ServiceLoader.load(InternalRedefinitionPlugin.class);
        Iterator<InternalRedefinitionPlugin> pluginIterator = serviceLoader.iterator();

        while (pluginIterator.hasNext()) {
            InternalRedefinitionPlugin plugin = pluginIterator.next();
            handler.activatePlugin(plugin);
            espressoContext.registerRedefinitionPlugin(plugin);
        }
        espressoContext.getRegistries().registerListener(handler);
        return handler;
    }

    private void activatePlugin(InternalRedefinitionPlugin plugin) {
        internalPlugins.add(plugin);
        plugin.activate(context, this);
    }

    @TruffleBoundary
    @Override
    public void onKlassDefined(ObjectKlass klass) {
        synchronized (classLoadActions) {
            Symbol<Symbol.Type> type = klass.getType();
            List<ClassLoadAction> loadActions = classLoadActions.get(type);
            if (loadActions != null) {
                // fire all registered load actions
                Iterator<ClassLoadAction> it = loadActions.iterator();
                while (it.hasNext()) {
                    ClassLoadAction loadAction = it.next();
                    loadAction.fire(klass);
                }
                // free up memory after firing all actions
                classLoadActions.remove(type);
            }
        }
    }

    @Override
    public boolean shouldRerunClassInitializer(ObjectKlass klass, boolean changed, DebuggerController controller) {
        boolean rerun = false;
        // internal plugins
        for (InternalRedefinitionPlugin plugin : internalPlugins) {
            if (plugin.shouldRerunClassInitializer(klass, changed)) {
                rerun = true;
                break;
            }
        }
        // external plugins
        if (externalPluginHandler != null) {
            rerun |= externalPluginHandler.shouldRerunClassInitializer(klass, changed, controller);
        }
        return rerun;
    }

    @Override
    public void postRedefinition(ObjectKlass[] changedKlasses, DebuggerController controller) {
        // internal plugins
        for (InternalRedefinitionPlugin plugin : internalPlugins) {
            try {
                plugin.postClassRedefinition(changedKlasses);
            } catch (Throwable t) {
                // don't let individual plugin errors cause failure
                // to run other post redefinition plugins
            }
        }
        // external plugins
        if (externalPluginHandler != null) {
            externalPluginHandler.postHotSwap(changedKlasses, controller);
        }
    }

    @Override
    public void collectExtraClassesToReload(List<RedefineInfo> redefineInfos, List<RedefineInfo> additional) {
        // internal plugins
        for (InternalRedefinitionPlugin plugin : internalPlugins) {
            plugin.collectExtraClassesToReload(redefineInfos, additional);
        }
    }
}
