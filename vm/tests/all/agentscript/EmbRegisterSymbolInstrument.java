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

package registersymbols;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.graalvm.tools.insight.Insight;

@TruffleInstrument.Registration(
    id = "registerSymbols",
    name = "Registers new symbols for Insight",
    version = "demo",
    services = { BiConsumer.class, Insight.SymbolProvider.class }
)
@SuppressWarnings("unchecked")
public final class EmbRegisterSymbolInstrument extends TruffleInstrument
implements BiConsumer<String, String> {
    private final Map<String,String> values = new HashMap<>();

    @Override
    protected void onCreate(Env env) {
        env.registerService(new Insight.SymbolProvider() {
            @Override
            public Map<String, Object> symbolsWithValues() throws Exception {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, String> symbolAndCode : values.entrySet()) {
                    Source src = Source.newBuilder("js", symbolAndCode.getValue(), symbolAndCode.getKey() + ".js").build();
                    CallTarget target = env.parse(src);
                    Object value = target.call();
                    map.put(symbolAndCode.getKey(), value);
                }
                return map;
            }
        });
        env.registerService(this);
    }


    @Override
    public void accept(String n, String v) {
        values.put(n, v);
    }
}
