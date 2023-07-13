/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.jfr.impl;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.runtime.jfr.CompilationEvent;
import com.oracle.truffle.runtime.jfr.CompilationStatisticsEvent;
import com.oracle.truffle.runtime.jfr.DeoptimizationEvent;
import com.oracle.truffle.runtime.jfr.Event;
import com.oracle.truffle.runtime.jfr.EventFactory;
import com.oracle.truffle.runtime.jfr.InvalidationEvent;

import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Name;

final class EventFactoryImpl implements EventFactory {

    private static final Map<Class<? extends Event>, Class<? extends jdk.jfr.Event>> spiToImpl = new HashMap<>();
    static {
        register(CompilationEventImpl.class);
        register(DeoptimizationEventImpl.class);
        register(InvalidationEventImpl.class);
        register(CompilationStatisticsEventImpl.class);
    }

    EventFactoryImpl() {
    }

    @Override
    public Class<? extends Annotation> getRequiredAnnotation() {
        return Name.class;
    }

    @Override
    public boolean isInitialized() {
        return FlightRecorder.isInitialized();
    }

    @Override
    public void addInitializationListener(Runnable listener) {
        Objects.requireNonNull(listener, "Listener must be non null.");
        FlightRecorder.addListener(new FlightRecorderListener() {
            @Override
            public void recorderInitialized(FlightRecorder recorder) {
                listener.run();
            }
        });
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
