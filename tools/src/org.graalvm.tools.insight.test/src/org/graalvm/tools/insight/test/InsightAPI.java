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
package org.graalvm.tools.insight.test;

// @formatter:off // @replace regex='.*' replacement=''
import java.util.Map;
import java.util.function.Predicate;

import org.graalvm.polyglot.Source;
import org.graalvm.tools.insight.Insight;


// @start region="InsightAPI"
/** Instance of this class is accessible via {@code insight} variable
 * in the Insight scripts registered to the instrument.
 */
public interface InsightAPI {
    /** ID of the instrument. Version {@code 1.1} has been released as
     * part of GraalVM 21.1.0 release.
     *
     * @return same value of {@link Insight#ID} - e.g. {@code "insight"}
     * @since 0.3
     */
    String id();

    /** Version of the API. Version {@code 1.1} has been released as
     * part of GraalVM 21.1.0 release.
     *
     * @return same value of {@link Insight#VERSION}
     * @since 0.1
     */
    String version();

    /** Marker interface for any handler.
     * @since 0.1
     */
    interface Handler {
    }

    interface SourceInfo {
        /** Name of the {@link OnSourceLoadedHandler#sourceLoaded}.
         * @return name of the loaded source
         * @since 0.1
         */
        String name();
        /** Character content of the {@link OnSourceLoadedHandler#sourceLoaded}.
         * @return content of the loaded source
         * @since 0.1
         */
        String characters();
        /** Identification of this source's language.
         * @return String representing the language ID
         * @since 0.1
         */
        String language();
        /** Mime type of this source.
         * @return given mime type or {@code null}
         * @since 0.1
         */
        String mimeType();
        /** URI uniquely identifying the source.
         * @return the URI
         * @since 0.1
         */
        String uri();
    }

    interface SourceSectionInfo {
        /**
         * Name of the enclosing function.
         *
         * @return the name of the enclosing function
         * @since 0.1
         */
        String name();

        /**
         * Information about surrounding source.
         *
         * @return information about the surrounding source
         * @since 0.4
         */
        SourceInfo source();

        /**
         * Characters of the location.
         *
         * @return the characters of this {@link OnEventHandler.Context}
         * @since 0.4
         */
        String characters();

        /**
         * Line of this location. The same as {@link #startLine()}.
         *
         * @return line number counting from one
         * @since 0.4
         */
        int line();

        /**
         * Staring line of this location.
         *
         * @return line number counting from one
         * @since 0.4
         */
        int startLine();

        /**
         * Final line of this location.
         *
         * @return line number counting from one
         * @since 0.4
         */
        int endLine();

        /**
         * Column of this location. The same as {@link #startColumn()}.
         *
         * @return column number counting from one
         * @since 0.4
         */
        int column();

        /**
         * Starting column of this location.
         *
         * @return column number counting from one
         * @since 0.4
         */
        int startColumn();

        /**
         * Final column of this location.
         *
         * @return column number counting from one
         * @since 0.4
         */
        int endColumn();

        /**
         * Returns the 0-based index of the first character in this section.
         * Returns <code>0</code> for unavailable source
         * sections.
         *
         * @return the starting character index
         * @since 1.1
         */
        int charIndex();


        /**
         * Returns the length of this section in characters. Returns
         * <code>0</code> for unavailable source
         * sections.
         *
         * @return the number of characters in the section
         * @since 1.1
         */
        int charEndIndex();


        /**
         * Returns the index of the text position immediately following the last
         * character in the section.
         * Returns <code>0</code> for unavailable source sections.
         *
         * @return the end position of the section
         * @since 1.1
         */
        int charLength();
    }

    @FunctionalInterface
    interface OnSourceLoadedHandler extends Handler {
        /** Called when a new source is loaded into the system.
         * @param info information about the loaded source
         * @since 0.1
         */
        void sourceLoaded(SourceInfo info);
    }
    /** Register a handler to be notified when a source is loaded.
     *
     * @param event has to be {@code "source"} string
     * @param handler a callback that takes
     *      {@link SourceInfo one argument}
     */
    void on(String event, OnSourceLoadedHandler handler);

    @FunctionalInterface
    interface OnEventHandler extends Handler {
        interface Context extends SourceSectionInfo {
            /** The current return value to be returned unless
             * {@link #returnNow} is called. The only meaningful
             * values can be expected inside of {@link #on on("return", ...)}
             * handlers.
             *
             * @param frame object with variables provided
             *    as a second parameter to {@link OnEventHandler#event event}
             *    method
             * @return the current return value or {@code null},
             *    if not applicable
             * @since 0.7
             */
            Object returnValue(Map<String, Object> frame);

            /** Immediatelly exits the current handler and returns to the
             * caller. Calling this method aborts execution of the current
             * handler. It bypasses language sematics and immediatelly
             * returns the provided value to the caller. If there are multiple
             * calls to {@code returnNow} (from different handlers) the
             * first call defines the return value.
             *
             * @param value the value to return to the caller
             * @since 0.7
             * @see #returnValue(java.util.Map)
             */
            void returnNow(Object value);

            /** Walks the stack at current execution point and iterates through
             * invocation frames and their local values.
             *
             * @param <T> type to return from the iterator
             * @param it iterator to call for each frame
             *    (that is not {@link Source#isInternal() internal})
             * @return first non-{@code null} value returned
             *    from the {@code it} iterator or {@code null}
             * @since 1.0
             */
            <T> T iterateFrames(FramesIterator<T> it);
        }
        void event(Context ctx, Map<String, Object> frame);
    }

    /** Iterator for the {@link OnEventHandler.Context#iterateFrames}.
     * @since 1.0
     */
    @FunctionalInterface
    interface FramesIterator<T> {
        /** Called for each frame found on the stack.
         *
         * @param at location in the source code
         * @param frame access to local variables
         * @return {@code null} to continue iteration, non-{@code null} to
         *   return immediatelly from the
         *   {@link OnEventHandler.Context#iterateFrames} method.
         */
        T onFrame(SourceSectionInfo at, Map<String, Object> frame);
    }

    class OnConfig {
        public boolean expressions;
        public boolean statements;
        public boolean roots;

        /** String with a regular expression to match name of functions.
         * Prior to version 0.6 this had to be a
         * {@code Function<String,Boolean>}.
         */
        public String rootNameFilter;
        /* @since 0.4 */
        public Predicate<SourceInfo> sourceFilter;
        /**
         * Location in the source file.
         * @since 1.2
         */
        public OnConfigAt at;
    }

    class OnConfigAt {
        /**
         * String with a regular expression to match source path.
         * Exactly one of this or {@link #sourceURI} is a mandatory property of
         * the `at` object.
         * @since 1.2
         */
        public String sourcePath;
        /**
         * String representation of a source URI.
         * Exactly one of this or {@link #sourcePath} is a mandatory property of
         * the `at` object.
         * @since 1.2
         */
        public String sourceURI;
        /**
         * The line to match.
         * @since 1.2
         */
        public Object line;
        /**
         * The column to match.
         * @since 1.2
         */
        public Object column;
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
// @end region="InsightAPI"
