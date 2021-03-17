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
import com.oracle.truffle.espresso.redefinition.plugins.api.RedefineObject;
import com.oracle.truffle.espresso.redefinition.plugins.api.MethodLocator;
import com.oracle.truffle.espresso.redefinition.plugins.api.InternalRedefinitionPlugin;
import com.oracle.truffle.espresso.redefinition.plugins.api.TriggerClass;

import java.util.ArrayList;

public class MicronautPlugin extends InternalRedefinitionPlugin {

    private static final String BEAN_CLASS_ACTIVATION_NAME = "io.micronaut.context.AbstractBeanDefinitionReference";
    private static final String EXECUTION_CLASS_ACTIVATION_NAME = "io.micronaut.context.AbstractExecutableMethod";
    private static final String MICRONAUT_CLASS = "io.micronaut.runtime.Micronaut";
    private static final String ROUTING_IN_BOUND_HANDLER = "io.micronaut.http.server.netty.RoutingInBoundHandler";

    private static final String RUN = "run";
    private static final String RUN_SIG = "([Ljava/lang/Class;[Ljava/lang/String;)Lio/micronaut/context/ApplicationContext;";
    private static final String STOP = "stop";
    private static final String CHANNEL_READ_0 = "channelRead0";
    private static final String CHANNEL_READ_0_SIG = "(Lio/netty/channel/ChannelHandlerContext;Lio/micronaut/http/HttpRequest;)V";


    private ArrayList<KlassRef> clinitRerunTypes;
    private boolean needsBeanRefresh;

    // the default bean context
    private RedefineObject micronautContext;

    private RedefineObject[] runArgs;
    private RedefineObject micronautClassInstance;

    private KlassRef rountingHandler;

    @Override
    public String getName() {
        return "Micronaut Reloading Plugin";
    }

    @Override
    public TriggerClass[] getTriggerClasses() {
        ArrayList<TriggerClass> triggers = new ArrayList<>(4);
        triggers.add(new TriggerClass(MICRONAUT_CLASS, this, klass -> {
            // we need the application context when reloading
            hookMethodExit(klass, new MethodLocator(RUN, RUN_SIG), MethodHook.Kind.INDEFINITE, (method, returnValue) -> {
                micronautContext = InternalRedefinitionPlugin.createCached(returnValue);
            });
            // we need the run arguments for re-starting a fresh context
            hookMethodEntry(klass, new MethodLocator(RUN, RUN_SIG), MethodHook.Kind.INDEFINITE, (method, variables) -> {
                micronautClassInstance = InternalRedefinitionPlugin.createCached(klass);
                // collect run arguments
                runArgs = new RedefineObject[2];
                // array of j.l.Class instances
                runArgs[0] = InternalRedefinitionPlugin.createUncached(variables[0].getValue());
                // array of String args
                runArgs[1] = InternalRedefinitionPlugin.createUncached(variables[1].getValue());
            });
        }));
        triggers.add(new TriggerClass(BEAN_CLASS_ACTIVATION_NAME, this, klass -> addClinitRerunKlass(klass)));
        triggers.add(new TriggerClass(EXECUTION_CLASS_ACTIVATION_NAME, this, klass -> addClinitRerunKlass(klass)));
        triggers.add(new TriggerClass(ROUTING_IN_BOUND_HANDLER, this, klass -> {
            rountingHandler = klass;
        }));

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
            for (KlassRef rerunType : clinitRerunTypes) {
                if (rerunType.isAssignable(klass)) {
                    needsBeanRefresh = true;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void postClassRedefinition(KlassRef[] changedKlasses) {
        if (needsBeanRefresh && micronautContext != null) {
            // OK, simple HotSwap is not enough, so register a reload hook
            // in the HTTP pipeline that restarts the context on the next
            // request.
            hookMethodEntry(rountingHandler, new MethodLocator(CHANNEL_READ_0, CHANNEL_READ_0_SIG), MethodHook.Kind.ONE_TIME, (method, variables) -> {
                try {
                    // restart Micronaut application context
                    micronautContext.invoke(STOP);
                    micronautClassInstance.invoke("run", runArgs);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
        }
        needsBeanRefresh = false;
    }
}
