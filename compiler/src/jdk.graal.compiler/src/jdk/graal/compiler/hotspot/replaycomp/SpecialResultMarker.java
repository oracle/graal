/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp;

/**
 * A marker representing the result of a recorded operation that did not produce a non-null value:
 * it may have thrown an exception, returned null, or there is no recorded result.
 */
public sealed interface SpecialResultMarker {
    /**
     * Marker indicating that no result was recorded.
     */
    NoResultMarker NO_RESULT_MARKER = new NoResultMarker();

    /**
     * Marker indicating that a null result was recorded.
     */
    NullResultMarker NULL_RESULT_MARKER = new NullResultMarker();

    /**
     * Materializes the result represented by this marker.
     *
     * @return the materialized result, or {@code null} if the result is null
     * @throws Throwable if the recorded operation threw an exception
     */
    Object materialize() throws Throwable;

    /**
     * A marker indicating that no result was recorded.
     */
    final class NoResultMarker implements SpecialResultMarker {
        private NoResultMarker() {
        }

        @Override
        public String toString() {
            return "[no recorded result]";
        }

        @Override
        public Object materialize() throws Throwable {
            throw new IllegalStateException();
        }
    }

    /**
     * A marker indicating that a null result was recorded.
     */
    final class NullResultMarker implements SpecialResultMarker {
        private NullResultMarker() {
        }

        @Override
        public String toString() {
            return "[null result]";
        }

        @Override
        public Object materialize() throws Throwable {
            return null;
        }
    }

    /**
     * A marker indicating that an exception was thrown during the operation.
     */
    final class ExceptionThrownMarker implements SpecialResultMarker {
        private final Throwable thrown;

        /**
         * Creates a new instance of this marker with the given thrown exception.
         *
         * @param thrown the exception that was thrown
         */
        public ExceptionThrownMarker(Throwable thrown) {
            this.thrown = thrown;
        }

        /**
         * Returns the exception that was thrown.
         *
         * @return the thrown exception
         */
        public Throwable getThrown() {
            return thrown;
        }

        @Override
        public String toString() {
            return "[thrown: " + thrown + "]";
        }

        @Override
        public Object materialize() throws Throwable {
            throw thrown;
        }
    }
}
