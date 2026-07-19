/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;

/**
 * Embedding replacement for the {@code js --jvm --polyglot --insight=agent-python.py agent-fib.js}
 * launcher invocation: a Python Insight script instrumenting a JavaScript application.
 */
public final class AgentPythonMain {
    public static void main(String[] args) throws Exception {
        try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).build()) {
            Source insightSource = Source.newBuilder("python", readResource("agent-python.py"), "agent-python.py")
                            .uri(URI.create("internal:///sources/agent-python.py"))
                            .buildLiteral();
            Instrument insight = engine.getInstruments().get("insight");
            @SuppressWarnings("unchecked")
            Function<Source, AutoCloseable> registerInsight = insight.lookup(Function.class);
            try (Context context = Context.newBuilder("js", "python")
                            .engine(engine)
                            .allowAllAccess(true)
                            .allowExperimentalOptions(true)
                            .build()) {
                try (AutoCloseable handle = registerInsight.apply(insightSource)) {
                    context.eval(Source.newBuilder("js", readResource("agent-fib.js"), "agent-fib.js").buildLiteral());
                }
            }
        }
    }

    private static String readResource(String name) throws IOException {
        try (InputStream in = AgentPythonMain.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IOException("Missing resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private AgentPythonMain() {
    }
}
