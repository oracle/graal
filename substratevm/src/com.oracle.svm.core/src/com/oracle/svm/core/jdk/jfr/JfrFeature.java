/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.core.jdk.RuntimeSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.jfr.logging.JfrLogger;
import com.oracle.svm.core.jdk.jfr.remote.JfrAutoSessionManager;
import com.oracle.svm.core.jdk.jfr.recorder.checkpoint.types.traceid.JfrTraceId;

import jdk.internal.event.Event;

@AutomaticFeature
public class JfrFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JfrRuntimeAccess.class, new JfrRuntimeAccessImpl());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (JfrAvailability.withJfr) {
            // JFR-TODO: test command line options for startup timer, file output, etc.
            RuntimeSupport.getRuntimeSupport().addStartupHook(JfrAutoSessionManager::startupHook);
            RuntimeSupport.getRuntimeSupport().addShutdownHook(JfrAutoSessionManager::shutdownHook);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (!JfrAvailability.withJfr) {
            return;
        }
        Class<?> eventClass = access.findClassByName("jdk.internal.event.Event");
        JfrRuntimeAccess jfrRuntime = ImageSingletons.lookup(JfrRuntimeAccess.class);
        if (eventClass != null && access.isReachable(eventClass)) {
            Set<Class<?>> s = access.reachableSubtypes(eventClass);
            s.forEach(c -> jfrRuntime.addEventClass((Class<? extends Event>) c));
        }
        Set<Class<?>> reachableClasses = access.reachableSubtypes(Object.class);
        Set<ClassLoader> classLoaders = new HashSet<>();
        Set<Module> modules = new HashSet<>();
        for (Class<?> clazz : reachableClasses) {
            if (JfrTraceId.getTraceId(clazz) == -1) {
                JfrTraceId.assign(clazz);
            }
            ClassLoader cl = clazz.getClassLoader();
            if (cl != null && !classLoaders.contains(cl)) {
                JfrTraceId.assign(cl);
                classLoaders.add(cl);
                if (access.isReachable(cl.getClass())) {
                    jfrRuntime.addClassloader(cl);
                }
            }
            Module module = clazz.getModule();
            if (module != null && !modules.contains(module)) {
                JfrTraceId.assign(module);
                modules.add(module);
            }
            // Packages are assigned a TraceId at runtime when first used in a constant pool
        }

    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        if (!JfrAvailability.withJfr) {
            return;
        }
        Class<?> eventClass = access.findClassByName("jdk.jfr.internal.instrument.JDKEvents");
        if (eventClass != null && access.isReachable(eventClass)) {
            try {
                Method initialize = eventClass.getMethod("initialize");
                initialize.setAccessible(true);
                initialize.invoke(null);

            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        eventClass = access.findClassByName("jdk.internal.event.Event");
        if (eventClass != null && access.isReachable(eventClass)) {
            Set<Class<?>> s = access.reachableSubtypes(eventClass);
            for (Class<?> c : s) {
                if (c.getCanonicalName().equals("jdk.jfr.Event")
                        || c.getCanonicalName().equals("jdk.internal.event.Event")
                        || c.getCanonicalName().equals("jdk.jfr.events.AbstractJDKEvent")) {
                    continue;
                }
                try {
                    Field f = c.getDeclaredField("eventHandler");
                    RuntimeReflection.register(f);
                } catch (Exception e) {
                    JfrLogger.logError("Unable to register eventHandler for:", c.getCanonicalName());
                }
            }
        } else {
            JfrLogger.logError("Unable to register fields for jfr event subclasses");
        }
    }
}
