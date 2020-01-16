/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.jfr.impl;

import java.util.HashMap;
import java.util.Map;
import jdk.jfr.FlightRecorder;
import org.graalvm.compiler.truffle.jfr.Event;
import org.graalvm.compiler.truffle.jfr.EventFactory;
import org.graalvm.compiler.truffle.jfr.CompilationEvent;
import org.graalvm.compiler.truffle.jfr.CompilationStatisticsEvent;
import org.graalvm.compiler.truffle.jfr.DeoptimizationEvent;
import org.graalvm.compiler.truffle.jfr.InvalidationEvent;

final class EventFactoryImpl implements EventFactory {

    private static final Map<Class<? extends Event>, Class<? extends jdk.jfr.Event>> spiToImpl = new HashMap<>();
    static {
        register(CompilationEventImpl.class);
        register(DeoptimizationEventImpl.class);
        register(InvalidationEventImpl.class);
        register(CompilationStatisticsEventImpl.class);
    }

    @Override
    public CompilationEvent createCompilationEvent() {
        return new CompilationEventImpl();
    }

    @Override
    public DeoptimizationEvent createDeoptimizationEvent() {
        return new DeoptimizationEventImpl();
    }

    @Override
    public InvalidationEvent createInvalidationEvent() {
        return new InvalidationEventImpl();
    }

    @Override
    public CompilationStatisticsEvent createCompilationStatisticsEvent() {
        return new CompilationStatisticsEventImpl();
    }

    @Override
    public void addPeriodicEvent(Class<? extends Event> event, Runnable producer) {
        Class<? extends jdk.jfr.Event> implClass = spiToImpl.get(event);
        if (implClass == null) {
            throw new IllegalArgumentException("Unknown event type: " + event);
        }
        FlightRecorder.addPeriodicEvent(implClass, producer);
    }

    @Override
    public void removePeriodicEvent(Class<? extends Event> event, Runnable producer) {
        Class<? extends jdk.jfr.Event> implClass = spiToImpl.get(event);
        if (implClass == null) {
            throw new IllegalArgumentException("Unknown event type: " + event);
        }
        FlightRecorder.removePeriodicEvent(producer);
    }

    private static void register(Class<? extends Event> eventClass) {
        for (Class<?> iface : eventClass.getInterfaces()) {
            if (Event.class.isAssignableFrom(iface)) {
                spiToImpl.put(iface.asSubclass(Event.class), eventClass.asSubclass(jdk.jfr.Event.class));
            }
        }
    }
}
