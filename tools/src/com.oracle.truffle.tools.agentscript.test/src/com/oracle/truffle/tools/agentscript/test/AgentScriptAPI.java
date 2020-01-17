/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.test;

// @formatter:off
import com.oracle.truffle.tools.agentscript.AgentScript;
import java.util.Map;
import java.util.function.Predicate;


// BEGIN: AgentScriptAPI
/** Instance of this class is accessible via {@code agent} variable
 * in the {@link AgentScript}
 * {@link AgentScript#registerAgentScript registered scripts}.
 */
public interface AgentScriptAPI {
    /** ID of the instrument. Version {@code 0.3} has been released as
     * part of GraalVM 20.0.0 release.
     *
     * @return same value of {@link AgentScript#ID} - e.g. {@code "agentscript"}
     * @since 0.3
     */
    String id();

    /** Version of the API. Version {@code 0.1} has been released as
     * part of GraalVM 19.3.0 release.
     *
     * @return same value of {@link AgentScript#VERSION}
     * @since 0.1
     */
    String version();

    /** Marker interface for any handler.
     * @since 0.1
     */
    interface Handler {
    }

    @FunctionalInterface
    interface OnSourceLoadedHandler extends Handler {
        interface Info {
            /** Name of the {@link #sourceLoaded}.
             * @return name of the loaded source
             */
            String name();
            /** Character content of the {@link #sourceLoaded}.
             * @return content of the loaded source
             */
            String characters();
            /** Identification of this source's language.
             * @return String representing the language ID
             */
            String language();
            /** Mime type of this source.
             * @return given mime type or {@code null}
             */
            String mimeType();
            /** URI uniquely identifying the source.
             * @return the URI
             */
            String uri();
        }
        void sourceLoaded(Info info);
    }
    /** Register a handler to be notified when a source is loaded.
     *
     * @param event has to be {@code "source"} string
     * @param handler a callback that takes
     *      {@link OnSourceLoadedHandler.Info one argument}
     */
    void on(String event, OnSourceLoadedHandler handler);

    @FunctionalInterface
    interface OnEventHandler extends Handler {
        interface Context {
            String name();
        }
        void event(Context ctx, Map<String, Object> frame);
    }
    class OnConfig {
        public boolean expressions;
        public boolean statements;
        public boolean roots;
        public Predicate<String> rootNameFilter;
    }
    /** Register a handler on a particular elements in the source code.
     *
     * @param event one of {@code "enter"}, {@code "return"} strings
     * @param handler callback
     *      {@link OnEventHandler#event function with two arguments}
     * @param config config object to identify locations to listen to
     */
    void on(String event, OnEventHandler handler, OnConfig config);

    @FunctionalInterface
    interface OnCloseHandler extends Handler {
        void closed();
    }
    /** Register on close handler.
     *
     * @param event must be {@code "close"} string
     * @param handler no args function to be notified when execution ends
     */
    void on(String event, OnCloseHandler handler);

    /** Unregisters a handler.
     * 
     * @param event the event type to unregister from
     * @param handler the instance of handler registered 
     *   by one of the {@code on} methods
     * @since 0.2
     */
    void off(String event, Handler handler);
}
// END: AgentScriptAPI
