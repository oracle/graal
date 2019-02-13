/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.runtime;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.runtime.RegexResultMessageResolutionFactory.ReadCacheNodeGen;

@MessageResolution(receiverType = RegexResult.class)
public class RegexResultMessageResolution {

    abstract static class ResultPropertyNode extends Node {

        abstract Object execute(RegexResult receiver);
    }

    static class GetInputNode extends ResultPropertyNode {

        @Override
        Object execute(RegexResult receiver) {
            return receiver.getInput();
        }
    }

    static class IsMatchNode extends ResultPropertyNode {

        @Override
        Object execute(RegexResult receiver) {
            return receiver != RegexResult.NO_MATCH;
        }
    }

    static class GetGroupCountNode extends ResultPropertyNode {

        @Override
        Object execute(RegexResult receiver) {
            return receiver.getGroupCount();
        }
    }

    static class GetStartArrayNode extends ResultPropertyNode {

        @Override
        Object execute(RegexResult receiver) {
            return receiver.getStartArrayObject();
        }
    }

    static class GetEndArrayNode extends ResultPropertyNode {

        @Override
        Object execute(RegexResult receiver) {
            return receiver.getEndArrayObject();
        }
    }

    static class GetCompiledRegexNode extends ResultPropertyNode {

        @Override
        Object execute(RegexResult receiver) {
            return receiver.getCompiledRegex();
        }
    }

    abstract static class ReadCacheNode extends Node {

        abstract Object execute(RegexResult receiver, String symbol);

        @Specialization(guards = "symbol == cachedSymbol", limit = "6")
        public Object readIdentity(RegexResult receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("getResultProperty(symbol)") ResultPropertyNode propertyNode) {
            return propertyNode.execute(receiver);
        }

        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "6", replaces = "readIdentity")
        public Object readEquals(RegexResult receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("getResultProperty(symbol)") ResultPropertyNode propertyNode) {
            return propertyNode.execute(receiver);
        }

        static ResultPropertyNode getResultProperty(String symbol) {
            switch (symbol) {
                case "input":
                    return new GetInputNode();
                case "isMatch":
                    return new IsMatchNode();
                case "groupCount":
                    return new GetGroupCountNode();
                case "start":
                    return new GetStartArrayNode();
                case "end":
                    return new GetEndArrayNode();
                case "regex":
                    return new GetCompiledRegexNode();
                default:
                    throw UnknownIdentifierException.raise(symbol);
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class RegexResultReadNode extends Node {

        @Child ReadCacheNode cache = ReadCacheNodeGen.create();

        public Object access(RegexResult receiver, String symbol) {
            return cache.execute(receiver, symbol);
        }
    }
}
