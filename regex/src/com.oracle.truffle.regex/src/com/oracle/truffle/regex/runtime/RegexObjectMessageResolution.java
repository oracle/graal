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
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.runtime.RegexObjectMessageResolutionFactory.ReadCacheNodeGen;

@MessageResolution(receiverType = RegexObject.class)
public class RegexObjectMessageResolution {

    abstract static class RegexObjectPropertyNode extends Node {

        abstract Object execute(RegexObject receiver);
    }

    static class GetPatternNode extends RegexObjectPropertyNode {

        @Override
        Object execute(RegexObject receiver) {
            return receiver.getSource().getPattern();
        }
    }

    static class GetFlagsNode extends RegexObjectPropertyNode {

        @Override
        Object execute(RegexObject receiver) {
            return receiver.getSource().getFlags();
        }
    }

    static class GetExecMethodNode extends RegexObjectPropertyNode {

        @Override
        Object execute(RegexObject receiver) {
            return receiver.getExecMethod();
        }
    }

    static class GetNamedCaptureGroupsNode extends RegexObjectPropertyNode {

        @Override
        Object execute(RegexObject receiver) {
            return receiver.getNamedCaptureGroups();
        }
    }

    abstract static class ReadCacheNode extends Node {

        abstract Object execute(RegexObject receiver, String symbol);

        @Specialization(guards = "symbol == cachedSymbol", limit = "4")
        Object readIdentity(RegexObject receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("getResultProperty(symbol)") RegexObjectPropertyNode propertyNode) {
            return propertyNode.execute(receiver);
        }

        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "4", replaces = "readIdentity")
        Object readEquals(RegexObject receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("getResultProperty(symbol)") RegexObjectPropertyNode propertyNode) {
            return propertyNode.execute(receiver);
        }

        static RegexObjectPropertyNode getResultProperty(String symbol) {
            switch (symbol) {
                case "exec":
                    return new GetExecMethodNode();
                case "pattern":
                    return new GetPatternNode();
                case "flags":
                    return new GetFlagsNode();
                case "groups":
                    return new GetNamedCaptureGroupsNode();
                default:
                    throw UnknownIdentifierException.raise(symbol);
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class RegexObjectReadNode extends Node {

        @Child ReadCacheNode cache = ReadCacheNodeGen.create();

        public Object access(RegexObject receiver, String symbol) {
            return cache.execute(receiver, symbol);
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class RegexObjectInvokeNode extends Node {

        @Child private ExecuteRegexObjectNode executeNode = ExecuteRegexObjectNode.create();

        public Object access(RegexObject receiver, String name, Object[] args) {
            if (!name.equals("exec")) {
                throw UnknownIdentifierException.raise(name);
            }
            if (args.length != 2) {
                throw ArityException.raise(2, args.length);
            }
            return executeNode.execute(receiver, args[0], args[1]);
        }
    }
}
