/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.parser.flavors.PythonFlagsMessageResolutionFactory.ReadCacheNodeGen;

@MessageResolution(receiverType = PythonFlags.class)
class PythonFlagsMessageResolution {

    static class PythonFlagsGetFlagNode {

        private final char flagChar;

        PythonFlagsGetFlagNode(char flagChar) {
            this.flagChar = flagChar;
        }

        Object execute(PythonFlags receiver) {
            return receiver.hasFlag(flagChar);
        }
    }

    abstract static class ReadCacheNode extends Node {

        abstract Object execute(PythonFlags receiver, String symbol);

        @Specialization(guards = "symbol == cachedSymbol", limit = "8")
        public Object readIdentity(PythonFlags receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("buildFlagGetter(symbol)") PythonFlagsGetFlagNode getFlagNode) {
            return getFlagNode.execute(receiver);
        }

        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "8", replaces = "readIdentity")
        public Object readEquals(PythonFlags receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("buildFlagGetter(symbol)") PythonFlagsGetFlagNode getFlagNode) {
            return getFlagNode.execute(receiver);
        }

        static PythonFlagsGetFlagNode buildFlagGetter(String symbol) {
            char flagChar;
            switch (symbol) {
                case "ASCII":
                    flagChar = 'a';
                    break;
                case "DOTALL":
                    flagChar = 's';
                    break;
                case "IGNORECASE":
                    flagChar = 'i';
                    break;
                case "LOCALE":
                    flagChar = 'L';
                    break;
                case "MULTILINE":
                    flagChar = 'm';
                    break;
                case "TEMPLATE":
                    flagChar = 't';
                    break;
                case "UNICODE":
                    flagChar = 'u';
                    break;
                case "VERBOSE":
                    flagChar = 'x';
                    break;
                default:
                    throw UnknownIdentifierException.raise(symbol);
            }
            return new PythonFlagsGetFlagNode(flagChar);
        }
    }

    @Resolve(message = "READ")
    abstract static class PythonFlagsReadNode extends Node {

        @Child ReadCacheNode cache = ReadCacheNodeGen.create();

        public Object access(PythonFlags receiver, String symbol) {
            return cache.execute(receiver, symbol);
        }
    }
}
