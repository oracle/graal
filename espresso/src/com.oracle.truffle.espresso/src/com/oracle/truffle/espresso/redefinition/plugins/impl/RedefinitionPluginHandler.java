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
import java.util.Collections;
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
import com.oracle.truffle.espresso.redefinition.ClassLoadListener;
import com.oracle.truffle.espresso.redefinition.plugins.api.ClassLoadAction;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.api.TriggerClass;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class RedefinitionPluginHandler implements RedefineListener, ClassLoadListener {

    private final EspressoContext context;
    private final Set<InternalRedefinitionPlugin> internalPlugins = Collections.synchronizedSet(new HashSet<>(1));
    private final Map<Symbol<Symbol.Type>, Set<TriggerClass>> internalTriggers;
    private final Map<Symbol<Symbol.Type>, List<ClassLoadAction>> classLoadActions = Collections.synchronizedMap(new HashMap<>());

    // The guest language HotSwap plugin handler passed
    // onto us if guest plugins are present at runtime.
    private ExternalPluginHandler externalPluginHandler;

    private RedefinitionPluginHandler(EspressoContext espressoContext, Map<Symbol<Symbol.Type>, Set<TriggerClass>> triggers) {
        this.context = espressoContext;
        this.internalTriggers = triggers;
    }

    @TruffleBoundary
    public void registerClassLoadAction(String className, ClassLoadAction action) {
        Symbol<Symbol.Type> type = context.getTypes().fromClassGetName(className);
        List<ClassLoadAction> list = classLoadActions.get(type);
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<>());
            classLoadActions.put(type, list);
        }
        list.add(action);
    }

    public void registerExternalHotSwapHandler(StaticObject handler) {
        if (handler != null) {
            externalPluginHandler = ExternalPluginHandler.create(handler);
        }
    }

    public static RedefinitionPluginHandler create(EspressoContext espressoContext) {
        // we use ServiceLoader to load all Espresso internal Plugins
        ServiceLoader<InternalRedefinitionPlugin> serviceLoader = ServiceLoader.load(InternalRedefinitionPlugin.class);
        Iterator<InternalRedefinitionPlugin> pluginIterator = serviceLoader.iterator();

        Map<Symbol<Symbol.Type>, Set<TriggerClass>> triggers = new HashMap<>();
        while (pluginIterator.hasNext()) {
            InternalRedefinitionPlugin plugin = pluginIterator.next();
            for (TriggerClass triggerClass : plugin.getTriggerClasses()) {
                Symbol<Symbol.Type> triggerType = espressoContext.getTypes().fromClassGetName(triggerClass.getClassName());
                Set<TriggerClass> triggerClasses = triggers.get(triggerType);
                if (triggerClasses == null) {
                    triggerClasses = new HashSet<>(1);
                }
                triggerClasses.add(triggerClass);
                triggers.put(triggerType, triggerClasses);
            }
        }
        RedefinitionPluginHandler handler = new RedefinitionPluginHandler(espressoContext, triggers);
        espressoContext.getRegistries().registerListener(handler);
        return handler;
    }

    @TruffleBoundary
    @Override
    public void onClassLoad(ObjectKlass klass) {
        // internal plugins
        Symbol<Symbol.Type> type = klass.getType();
        if (internalTriggers.containsKey(type)) {
            Set<TriggerClass> triggerClasses = internalTriggers.get(type);
            for (TriggerClass triggerClass : triggerClasses) {
                if (!internalPlugins.contains(triggerClass.getPlugin())) {
                    triggerClass.getPlugin().activate(klass.getContext(), this);
                    internalPlugins.add(triggerClass.getPlugin());
                }
                triggerClass.fire(klass);
            }
        }
        // fire registered load actions
        List<ClassLoadAction> loadActions = classLoadActions.getOrDefault(type, Collections.emptyList());
        Iterator<ClassLoadAction> it = loadActions.iterator();
        while (it.hasNext()) {
            ClassLoadAction loadAction = it.next();
            loadAction.fire(klass);
            it.remove();
        }
        if (loadActions.isEmpty()) {
            classLoadActions.remove(type);
        }
    }

    // listener methods
    @Override
    public boolean rerunClinit(ObjectKlass klass, boolean changed) {
        boolean rerun = false;
        // internal plugins
        for (InternalRedefinitionPlugin plugin : internalPlugins) {
            if (plugin.reRunClinit(klass, changed)) {
                rerun = true;
                break;
            }
        }
        // external plugins
        if (externalPluginHandler != null) {
            rerun |= externalPluginHandler.rerunClassInit(klass, changed);
        }
        return rerun;
    }

    @Override
    public void postRedefition(ObjectKlass[] changedKlasses) {
        // internal plugins
        for (InternalRedefinitionPlugin plugin : internalPlugins) {
            plugin.postClassRedefinition(changedKlasses);
        }
        // external plugins
        if (externalPluginHandler != null) {
            externalPluginHandler.postHotSwap(changedKlasses);
        }
    }

    @Override
    public void addExtraReloadClasses(List<RedefineInfo> redefineInfos, List<RedefineInfo> additional) {
        // internal plugins
        for (InternalRedefinitionPlugin plugin : internalPlugins) {
            plugin.fillExtraReloadClasses(redefineInfos, additional);
        }
    }
}
