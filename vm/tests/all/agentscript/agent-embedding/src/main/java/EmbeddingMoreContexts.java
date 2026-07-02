/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unchecked")
public class EmbeddingMoreContexts {
    public static void main(String... args) throws Exception {
        Integer repeat = Integer.parseInt(args[0]);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Engine eng = Engine.create();
        Instrument instrument = eng.getInstruments().get("insight");
        assert instrument != null : "Insight instrument found";
        
        Source fibSrc = Source.create("js", "(function fib(n) {\n" +
            "  if (n <= 2) return 1;\n" +
            "  return fib(n - 1) + fib(n - 2);\n" +
            "})"
        );
        
        try (AutoCloseable c = registerAgent(instrument)) {
            for (int i = 1; i <= repeat; i++) {
                out.reset();
                assert out.size() == 0 : "Output is clear " + out.size();
                try (Context ctx = Context.newBuilder().engine(eng).out(out).err(out).build()) {
                    Value fib = ctx.eval(fibSrc);

                    int value = fib.execute(10 + i).asInt();
                    String txt = out.toString("UTF-8");
                    assert txt.contains("calling fib with 5") : "Expecting debug output (round " + i + ") :\n" + txt;
                    System.out.println(txt.split("\n")[1]);
                    System.out.println("result is " + value);
                }
            }
        }
        
        System.out.println("OK " + repeat + " times!");
    }

    private static AutoCloseable registerAgent(Instrument instrument) throws InterruptedException, IOException {
        Function<Source,AutoCloseable> api = instrument.lookup(Function.class);
        assert api != null : "Instrument exposes a function like API";
        ClassLoader l = api.getClass().getClassLoader();
        assert l == null : "No special loader found: " + l;

        URL agentScript = EmbeddingMoreContexts.class.getResource("agent-embedding.js");
        assert agentScript != null : "Script found";

        Source src = Source.newBuilder("js", agentScript).build();
        return api.apply(src);
    }
}
