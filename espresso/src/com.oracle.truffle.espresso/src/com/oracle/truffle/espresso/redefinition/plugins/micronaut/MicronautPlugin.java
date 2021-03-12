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
package com.oracle.truffle.espresso.redefinition.plugins.micronaut;

import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.MethodHook;
import com.oracle.truffle.espresso.jdwp.impl.JDWPLogger;
import com.oracle.truffle.espresso.redefinition.plugins.api.RedefineObject;
import com.oracle.truffle.espresso.redefinition.plugins.api.MethodLocator;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.api.TriggerClass;

import java.util.ArrayList;

// TODO - current state doesn't reflect changes to apps properly
// TODO - this needs more thorough integration to Micronaut
public class MicronautPlugin extends InternalRedefinitionPlugin {

    private static final String BEAN_CLASS_ACTIVATION_NAME = "io.micronaut.context.AbstractBeanDefinitionReference";
    private static final String EXECUTION_CLASS_ACTIVATION_NAME = "io.micronaut.context.AbstractExecutableMethod";
    private static final String DEFAULT_BEAN_CONTEXT_ACTIVATION_NAME = "io.micronaut.context.DefaultBeanContext";
    private static final String EMBEDDED_APPLICATION_CLASS_NAME = "io.micronaut.runtime.EmbeddedApplication";
    private static final String BEAN_LOCATOR_CLASS_NAME = "io.micronaut.context.BeanLocator";

    private static final String START_METHOD_NAME = "start";
    private static final String START_METHOD_SIGNATURE = "()Lio/micronaut/context/BeanContext;";

    private ArrayList<KlassRef> clinitRerunTypes;
    private boolean needsBeanRefresh;

    // the default bean context
    private RedefineObject defaultBeanContext;
    // the embedded application instance
    private RedefineObject embeddedApplicationType;

    @Override
    public String getName() {
        return "Micronaut Reloading Plugin";
    }

    @Override
    public TriggerClass[] getTriggerClasses() {
        ArrayList<TriggerClass> triggers = new ArrayList<>(3);
        triggers.add(new TriggerClass(DEFAULT_BEAN_CONTEXT_ACTIVATION_NAME, this, klass -> {
            // default bean class loaded, so register a listener on the start method
            // for fetching the context object we need on redefinitions later
            hookMethodExit(klass, new MethodLocator(START_METHOD_NAME, START_METHOD_SIGNATURE), MethodHook.Kind.ONE_TIME, (method, returnValue) -> {
                defaultBeanContext = InternalRedefinitionPlugin.createCached(returnValue);
            });
        }));
        triggers.add(new TriggerClass(BEAN_CLASS_ACTIVATION_NAME, this, klass -> addClinitRerunKlass(klass)));
        triggers.add(new TriggerClass(EXECUTION_CLASS_ACTIVATION_NAME, this, klass -> addClinitRerunKlass(klass)));
        return triggers.toArray(new TriggerClass[triggers.size()]);
    }

    private synchronized void addClinitRerunKlass(KlassRef klass) {
        if (clinitRerunTypes == null) {
            clinitRerunTypes = new ArrayList<>(1);
        }
        clinitRerunTypes.add(klass);
    }

    @Override
    public boolean reRunClinit(KlassRef klass, boolean changed) {
        if (changed) {
            KlassRef superClass = klass.getSuperClass();
            for (KlassRef rerunType : clinitRerunTypes) {
                if (rerunType == superClass) {
                    needsBeanRefresh = true;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void postClassRedefinition(KlassRef[] changedKlasses) {
        if (needsBeanRefresh && defaultBeanContext.notNull()) {
            try {
                // clear bean caches
                flushBeanCaches();
                // fetch needed class types and instances one time
                if (embeddedApplicationType == null) {
                    embeddedApplicationType = defaultBeanContext.fromType(EMBEDDED_APPLICATION_CLASS_NAME);
                }
                defaultBeanContext.invokeRaw("startEnvironment");

                // force a bean re-scan:
                defaultBeanContext.invokeRaw("readAllBeanConfigurations");
                defaultBeanContext.invokeRaw("readAllBeanDefinitionClasses");

                // re-wire beans for the application
                defaultBeanContext.invokePrecise(BEAN_LOCATOR_CLASS_NAME, "findBean", embeddedApplicationType);
            } catch (Throwable ex) {
                JDWPLogger.log("Failed to reload Micronaut beans due to %s", JDWPLogger.LogLevel.ALL, ex.getMessage());
            }
        }
        needsBeanRefresh = false;
    }

    private void flushBeanCaches() {
        try {
            clearCollection(defaultBeanContext, "singletonObjects");
            clearCollection(defaultBeanContext, "scopedProxies");
            clearCollection(defaultBeanContext, "beanDefinitionsClasses");
            clearCollection(defaultBeanContext, "containsBeanCache");
            clearCollection(defaultBeanContext, "initializedObjectsByType");
            clearCollection(defaultBeanContext, "beanConcreteCandidateCache");
            clearCollection(defaultBeanContext, "beanCandidateCache");
            clearCollection(defaultBeanContext, "beanIndex");
        } catch (NoSuchFieldException | NoSuchMethodException ex) {
            JDWPLogger.log("Failed to flush Micronaut caches due to %s", JDWPLogger.LogLevel.ALL, ex.getMessage());
        }
    }
}
