/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpHandler;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.DebugOptions.PrintGraphTarget;
import jdk.graal.compiler.graphio.GraphOutput;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.graal.compiler.util.EconomicHashMap;

@ServiceProvider(DebugDumpHandlersFactory.class)
public class TruffleDebugHandlersFactory implements DebugDumpHandlersFactory {

    @Override
    public List<DebugDumpHandler> createHandlers(OptionValues options) {
        return List.<DebugDumpHandler> of(new TruffleASTDumpHandler());
    }

    private final class TruffleASTDumpHandler implements DebugDumpHandler {

        TruffleASTDumpHandler() {
        }

        @Override
        public void dump(Object object, DebugContext debug, boolean forced, String format, Object... arguments) {
            if (object instanceof TruffleDebugAST ast && DebugOptions.PrintGraph.getValue(debug.getOptions()) != PrintGraphTarget.Disable) {
                try {
                    var output = debug.buildOutput(GraphOutput.newBuilder(TruffleDebugAST.AST_DUMP_STRUCTURE).blocks(TruffleDebugAST.AST_DUMP_STRUCTURE));
                    Map<Object, Object> properties = new EconomicHashMap<>();
                    output.beginGroup(ast, "AST", "AST", null, 0, null);
                    output.print((TruffleDebugAST) object, properties, 0, format, arguments);
                    output.endGroup();
                    output.close();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to dump block mapping", e);
                }
            }
        }

    }

}
