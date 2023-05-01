/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.junit.After;

public abstract class TestWithPolyglotOptions {

    private Context activeContext;

    @After
    public final void cleanup() {
        if (activeContext != null) {
            try {
                activeContext.leave();
            } finally {
                activeContext.close();
                activeContext = null;
            }
        }
    }

    /**
     * Creates a new {@link Builder} with default options set. The default options can be
     * overwritten using {@link Builder#option(String, String)}.
     */
    protected Builder newContextBuilder() {
        return Context.newBuilder().allowAllAccess(true).allowExperimentalOptions(true);
    }

    protected final Context setupContext(String... keyValuePairs) {
        if ((keyValuePairs.length & 1) != 0) {
            throw new IllegalArgumentException("KeyValuePairs must have even size");
        }
        Context.Builder builder = newContextBuilder();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            builder.option(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return setupContext(builder);
    }

    protected final Context setupContext(Context.Builder builder) {
        cleanup();
        Context newContext = builder.build();
        newContext.enter();
        activeContext = newContext;
        return newContext;
    }
}
