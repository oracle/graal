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

package com.oracle.svm.jfr;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.svm.jfr.traceid.JfrTraceIdMap;
import jdk.internal.event.Event;

class JfrRuntimeAccessImpl implements JfrRuntimeAccess {

    private final List<Class<? extends Event>> eventClasses = new ArrayList<>();

    private final Set<ClassLoader> reachableClassloaders = new HashSet<>();
    private final JfrTraceIdMap traceIdMap = new JfrTraceIdMap();

    @Override
    public List<Class<? extends Event>> getEventClasses() {
        return new ArrayList<>(eventClasses);
    }

    @Override
    public void addEventClass(Class<? extends Event> eventClass) {
        if (!Modifier.isAbstract(eventClass.getModifiers())) {
            eventClasses.add(eventClass);
        }
    }

    @Override
    public JfrTraceIdMap getTraceIdMap() {
        return traceIdMap;
    }

    @Override
    public Set<ClassLoader> getReachableClassloaders() {
        return reachableClassloaders;
    }

    @Override
    public void addClassloader(ClassLoader c) {
        this.reachableClassloaders.add(c);
    }
}
