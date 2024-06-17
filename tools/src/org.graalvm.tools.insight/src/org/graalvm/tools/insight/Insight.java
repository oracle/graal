/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight;

import java.util.Map;
import java.util.function.Function;

import org.graalvm.polyglot.Engine;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.TruffleObject;

/**
 * Programmatic access to the Insight instrument. Obtain an instrument instance via its {@link #ID}:
 * <p>
 * {@snippet file = "org/graalvm/tools/insight/test/Embedding.java" region = "Embedding#apply"}
 *
 * and then {@link Function#apply(java.lang.Object) evaluate} {@link org.graalvm.polyglot.Source}
 * scripts written in any language accessing the {@code agent} variable exposed to them.
 * <p>
 * See an example at <a href=
 * "https://www.graalvm.org/latest/tools/graalvm-insight/embedding/#embedding-insight-into-java">Embedding
 * Insight into Java</a>.
 *
 * Use {@link #VERSION following API} when dealing with the {@code insight} variable:
 * <p>
 * {@snippet file = "org/graalvm/tools/insight/test/InsightAPI.java" region = "InsightAPI"}
 *
 * @since 20.1
 */
public final class Insight {
    private Insight() {
    }

    /**
     * The ID of the agent script instrument is {@code "insight"}. Use it to obtain access to an
     * {@link Insight} instruments inside of your {@link Engine}:
     * <p>
     * {@snippet file = "org/graalvm/tools/insight/test/Embedding.java" region = "Embedding#apply"}
     *
     * @since 20.1
     */
    public static final String ID = "insight";

    /**
     * Version of the Insight instrument. The current version understands following Java-like
     * polyglot <em>API</em> made available to the GraalVM Insight scripts via {@code insight}
     * reference:
     * <p>
     * {@snippet file = "org/graalvm/tools/insight/test/InsightAPI.java" region = "InsightAPI"}
     *
     * @since 20.1
     */
    public static final String VERSION = "1.2";

    /**
     * Additional provider of symbols for {@link #ID Insight scripts}. All available instruments are
     * queried for implementation of this interface. If provided, they can contribute symbols with
     * their values to be available as globals when executing the {@link #ID Insight scripts}.
     * <p>
     * {@snippet file = "org/graalvm/tools/insight/test/MeaningOfWorldInstrument.java" region =
     * "org.graalvm.tools.insight.test.MeaningOfWorldInstrument"}
     * <p>
     * The previous instrument makes variable {@code meanining} with value {@code 42} available to
     * every {@link #ID Insight script} when properly registered into the virtual machine. A typical
     * way is to register your custom instrument is to use property
     * {@code truffle.class.path.append} when launching the virtual machine:
     *
     * <pre>
     * graalvm/bin/java -Dtruffle.class.path.append=meaningOfWorld.jar -jar app.jar
     * </pre>
     *
     * Take care when writing your {@link TruffleInstrument instruments} as they can alter many
     * aspects of program execution and aren't subject to any security sandbox. See
     * {@link TruffleInstrument} for more information about developing, using and registering
     * instruments.
     *
     * @since 21.0
     */
    public interface SymbolProvider {
        /**
         * Map with symbol names and their interop values.
         *
         * @return map mapping names to their primitive, {@link String} or {@link TruffleObject}
         *         values
         * @throws Exception any exception is propagated as an internal error
         * @since 21.0
         */
        @SuppressWarnings("javadoc")
        Map<String, ? extends Object> symbolsWithValues() throws Exception;
    }
}
