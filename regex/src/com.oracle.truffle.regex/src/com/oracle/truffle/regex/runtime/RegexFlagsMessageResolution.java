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
import com.oracle.truffle.regex.RegexFlags;
import com.oracle.truffle.regex.runtime.RegexFlagsMessageResolutionFactory.ReadCacheNodeGen;

@MessageResolution(receiverType = RegexFlags.class)
public class RegexFlagsMessageResolution {

    abstract static class RegexFlagsPropertyNode extends Node {

        abstract Object execute(RegexFlags receiver);
    }

    static class RegexFlagsGetSourceNode extends RegexFlagsPropertyNode {

        @Override
        Object execute(RegexFlags receiver) {
            return receiver.getSource();
        }
    }

    static class RegexFlagsGetIgnoreCaseNode extends RegexFlagsPropertyNode {

        @Override
        Object execute(RegexFlags receiver) {
            return receiver.isIgnoreCase();
        }
    }

    static class RegexFlagsGetMultilineNode extends RegexFlagsPropertyNode {

        @Override
        Object execute(RegexFlags receiver) {
            return receiver.isMultiline();
        }
    }

    static class RegexFlagsGetStickyNode extends RegexFlagsPropertyNode {

        @Override
        Object execute(RegexFlags receiver) {
            return receiver.isSticky();
        }
    }

    static class RegexFlagsGetGlobalNode extends RegexFlagsPropertyNode {

        @Override
        Object execute(RegexFlags receiver) {
            return receiver.isGlobal();
        }
    }

    static class RegexFlagsGetUnicodeNode extends RegexFlagsPropertyNode {

        @Override
        Object execute(RegexFlags receiver) {
            return receiver.isUnicode();
        }
    }

    static class RegexFlagsGetDotAllNode extends RegexFlagsPropertyNode {

        @Override
        Object execute(RegexFlags receiver) {
            return receiver.isDotAll();
        }
    }

    abstract static class ReadCacheNode extends Node {

        abstract Object execute(RegexFlags receiver, String symbol);

        @Specialization(guards = "symbol == cachedSymbol", limit = "7")
        public Object readIdentity(RegexFlags receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("getResultProperty(symbol)") RegexFlagsPropertyNode propertyNode) {
            return propertyNode.execute(receiver);
        }

        @Specialization(guards = "symbol.equals(cachedSymbol)", limit = "7", replaces = "readIdentity")
        public Object readEquals(RegexFlags receiver, @SuppressWarnings("unused") String symbol,
                        @Cached("symbol") @SuppressWarnings("unused") String cachedSymbol,
                        @Cached("getResultProperty(symbol)") RegexFlagsPropertyNode propertyNode) {
            return propertyNode.execute(receiver);
        }

        static RegexFlagsPropertyNode getResultProperty(String symbol) {
            switch (symbol) {
                case "source":
                    return new RegexFlagsGetSourceNode();
                case "ignoreCase":
                    return new RegexFlagsGetIgnoreCaseNode();
                case "multiline":
                    return new RegexFlagsGetMultilineNode();
                case "sticky":
                    return new RegexFlagsGetStickyNode();
                case "global":
                    return new RegexFlagsGetGlobalNode();
                case "unicode":
                    return new RegexFlagsGetUnicodeNode();
                case "dotAll":
                    return new RegexFlagsGetDotAllNode();
                default:
                    throw UnknownIdentifierException.raise(symbol);
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class RegexFlagsReadNode extends Node {

        @Child ReadCacheNode cache = ReadCacheNodeGen.create();

        public Object access(RegexFlags receiver, String symbol) {
            return cache.execute(receiver, symbol);
        }
    }
}
