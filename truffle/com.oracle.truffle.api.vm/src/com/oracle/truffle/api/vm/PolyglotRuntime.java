/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import com.oracle.truffle.api.impl.DispatchOutputStream;
import java.io.InputStream;

/**
 * A runtime environment for one or more {@link PolyglotEngine} instances. By default multiple
 * {@linkplain PolyglotEngine engines} operate independently - e.g. they have their own isolated
 * {@linkplain PolyglotRuntime runtime}. However, sometimes it may be useful (for example to save
 * valuable resources needed for code and various other metadata) to group a set of
 * {@linkplain PolyglotEngine engines} into a single {@linkplain PolyglotRuntime runtime}. One can
 * do it like this:
 * <p>
 * {@codesnippet com.oracle.truffle.api.instrumentation.test.AbstractInstrumentationTest}
 * <p>
 * The above example prepares a single instance of the {@link PolyglotRuntime runtime} and
 * {@link PolyglotEngine.Builder#runtime(com.oracle.truffle.api.vm.PolyglotRuntime) uses it} when
 * configuring {@linkplain PolyglotEngine.Builder engine builder} in the <code>createEngine</code>
 * method. The method may be called multiple times yielding engines sharing essential execution
 * resources behind the scene.
 *
 * @since 0.25
 */
public final class PolyglotRuntime {
    private PolyglotShared shared;

    private PolyglotRuntime() {
    }

    /**
     * Starts creation of a new runtime instance. Call any methods of the {@link Builder} and finish
     * the creation by calling {@link Builder#build()}.
     *
     * @return new instance of a builder
     * @since 0.25
     */
    public static Builder newBuilder() {
        return new PolyglotRuntime().new Builder();
    }

    PolyglotShared createShared(DispatchOutputStream realOut, DispatchOutputStream realErr, InputStream realIn) {
        if (shared == null) {
            shared = new PolyglotShared(realOut, realErr, realIn);
        }
        return shared;
    }

    /**
     * Builder for creating new instance of a {@link PolyglotRuntime}.
     * 
     * @since 0.25
     */
    public final class Builder {
        private Builder() {
        }

        /**
         * Creates new instance of a runtime. Uses data stored in this builder to configure it. Once
         * the instance is obtained, pass it to
         * {@link PolyglotEngine.Builder#runtime(com.oracle.truffle.api.vm.PolyglotRuntime)} method.
         *
         * @return new instances of the {@link PolyglotRuntime}
         * @since 0.25
         */
        public PolyglotRuntime build() {
            return new PolyglotRuntime();
        }
    }
}
