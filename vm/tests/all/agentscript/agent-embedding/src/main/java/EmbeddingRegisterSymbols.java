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

import java.io.ByteArrayOutputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

@SuppressWarnings("unchecked")
public class EmbeddingRegisterSymbols {
    public static void main(String... args) throws Exception {
        switch (args[0]) {
            case "primitives": {
                Source src = Source.newBuilder("js",
                    "  insight.on('enter', (ctx, frame) => {"
                    + "  if (--count <= 0) throw msg + count;"
                    + "}, { roots : true });",
                    "insight.js"
                ).build();
                exportAndTest(src, (ctx, registerSymbols) -> {
                    registerSymbols.accept("msg", "'Stop: '");
                    registerSymbols.accept("count", "42");
                });
                break;
            }
            case "object": {
                Source src = Source.newBuilder("js",
                    "  insight.on('enter', (ctx, frame) => { with (data) {"
                    + "  if (--count <= 0) throw msg + count; }"
                    + "}, { roots : true });",
                    "insight.js"
                ).build();
                exportAndTest(src, (ctx, registerSymbols) -> {
                    registerSymbols.accept("data", "({ msg : 'Stop: ', count : 42 })");
                });
                break;
            }
            default:
                throw new IllegalArgumentException();
        }
    }

    private static void exportAndTest(
        Source src, BiConsumer<Context, BiConsumer<String,String>> withRegisterSymbols
    ) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context ctx = Context.newBuilder().out(out).err(out).build()) {
            Engine eng = ctx.getEngine();
            Instrument insightInstrument = eng.getInstruments().get("insight");
            assert insightInstrument != null : "Insight instrument found";
            Instrument registerInstrument = eng.getInstruments().get("registerSymbols");
            assert registerInstrument != null : "Our register symbols instrument found";

            BiConsumer<String,String> registerSymbols = registerInstrument.lookup(BiConsumer.class);
            assert registerSymbols != null : "Insight supports registration of symbols";
            withRegisterSymbols.accept(ctx, registerSymbols);

            Function<Source,AutoCloseable> registerScripts = insightInstrument.lookup(Function.class);
            assert registerScripts != null : "Insight supports registration of scripts";

            try (AutoCloseable close = registerScripts.apply(src)) {
                Value fib = ctx.eval("js", "(function fib(n) {\n" +
                        "  if (n <= 2) return 1;\n" +
                        "  return fib(n - 1) + fib(n - 2);\n" +
                        "})"
                );
                int fib10 = fib.execute(10).asInt();
                assert fib10 == 55 : "fib(10) == " + fib10;
            } finally {
                System.out.println(out.toString("UTF-8"));
            }
        }
        System.out.println("The execution shall end with an exception!");
    }
}
