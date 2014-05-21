/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.jfr.events;

import java.net.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.hotspot.events.*;
import com.oracle.jrockit.jfr.*;

/**
 * A JFR implementation for {@link EventProvider}. This implementation is used when Flight Recorder
 * is turned on.
 */
@ServiceProvider(EventProvider.class)
public final class JFREventProvider implements EventProvider {

    @SuppressWarnings("deprecation") private final Producer producer;

    @SuppressWarnings("deprecation")
    public JFREventProvider() {
        try {
            /*
             * The "HotSpot JVM" producer is a native producer and we cannot use it. So we create
             * our own. This has the downside that Mission Control is confused and doesn't show
             * Graal's events in the "Code" tab. There are plans to revise the JFR code for JDK 9.
             */
            producer = new Producer("HotSpot JVM", "Oracle Hotspot JVM", "http://www.oracle.com/hotspot/jvm/");
            producer.register();
        } catch (URISyntaxException e) {
            throw new InternalError(e);
        }

        // Register event classes with Producer.
        for (Class<?> c : JFREventProvider.class.getDeclaredClasses()) {
            if (c.isAnnotationPresent(EventDefinition.class)) {
                assert com.oracle.jrockit.jfr.InstantEvent.class.isAssignableFrom(c) : c;
                registerEvent(c);
            }
        }
    }

    /**
     * Register an event class with the {@link Producer}.
     *
     * @param c event class
     * @return the {@link EventToken event token}
     */
    @SuppressWarnings({"deprecation", "javadoc", "unchecked"})
    private final EventToken registerEvent(Class<?> c) {
        try {
            return producer.addEvent((Class<? extends com.oracle.jrockit.jfr.InstantEvent>) c);
        } catch (InvalidEventDefinitionException | InvalidValueException e) {
            throw new InternalError(e);
        }
    }

    public CompilationEvent newCompilationEvent() {
        return new JFRCompilationEvent();
    }

    /**
     * A JFR compilation event.
     *
     * <p>
     * See: event {@code Compilation} in {@code src/share/vm/trace/trace.xml}
     */
    @SuppressWarnings("deprecation")
    @EventDefinition(name = "Compilation", path = "vm/compiler/compilation")
    public static class JFRCompilationEvent extends com.oracle.jrockit.jfr.DurationEvent implements CompilationEvent {

        /*
         * FIXME method should be a Method* but we can't express that in Java.
         */
        @ValueDefinition(name = "Java Method") public String method;
        @ValueDefinition(name = "Compilation ID", relationKey = "COMP_ID") public int compileId;
        @ValueDefinition(name = "Compilation Level") public short compileLevel;
        @ValueDefinition(name = "Succeeded") public boolean succeeded;
        @ValueDefinition(name = "On Stack Replacement") public boolean isOsr;
        @ValueDefinition(name = "Compiled Code Size", contentType = ContentType.Bytes) public int codeSize;
        @ValueDefinition(name = "Inlined Code Size", contentType = ContentType.Bytes) public int inlinedBytes;

        public void setMethod(String method) {
            this.method = method;
        }

        public void setCompileId(int id) {
            this.compileId = id;
        }

        public void setCompileLevel(int compileLevel) {
            this.compileLevel = (short) compileLevel;
        }

        public void setSucceeded(boolean succeeded) {
            this.succeeded = succeeded;
        }

        public void setIsOsr(boolean isOsr) {
            this.isOsr = isOsr;
        }

        public void setCodeSize(int codeSize) {
            this.codeSize = codeSize;
        }

        public void setInlinedBytes(int inlinedBytes) {
            this.inlinedBytes = inlinedBytes;
        }
    }

    public CompilerFailureEvent newCompilerFailureEvent() {
        return new JFRCompilerFailureEvent();
    }

    /**
     * A JFR compiler failure event.
     *
     * <p>
     * See: event {@code CompilerFailure} in {@code src/share/vm/trace/trace.xml}
     */
    @SuppressWarnings("deprecation")
    @EventDefinition(name = "Compilation Failure", path = "vm/compiler/failure")
    public static class JFRCompilerFailureEvent extends com.oracle.jrockit.jfr.InstantEvent implements CompilerFailureEvent {

        @ValueDefinition(name = "Compilation ID", relationKey = "COMP_ID") public int compileId;
        @ValueDefinition(name = "Message", description = "The failure message") public String failure;

        public void setCompileId(int id) {
            this.compileId = id;
        }

        public void setMessage(String message) {
            this.failure = message;
        }
    }

}
