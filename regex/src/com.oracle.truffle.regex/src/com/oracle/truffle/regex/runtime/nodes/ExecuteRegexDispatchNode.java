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
package com.oracle.truffle.regex.runtime.nodes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.CompiledRegex;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.util.NumberConversion;

public abstract class ExecuteRegexDispatchNode extends Node {

    public abstract RegexResult execute(CompiledRegex receiver, RegexObject regexObject, Object input, Object fromIndex);

    @Specialization(guards = "receiver == cachedReceiver", limit = "4")
    public RegexResult doCached(@SuppressWarnings("unused") CompiledRegex receiver, RegexObject regexObject, Object input, Object fromIndex,
                    @Cached("create()") ExpectStringOrTruffleObjectNode expectStringOrTruffleObjectNode,
                    @Cached("create()") ExpectNumberNode expectNumberNode,
                    @SuppressWarnings("unused") @Cached("receiver") CompiledRegex cachedReceiver,
                    @Cached("create(cachedReceiver.getRegexCallTarget())") DirectCallNode directCallNode) {
        final Object unboxedInput = expectStringOrTruffleObjectNode.execute(input);
        final Number fromIndexNumber = expectNumberNode.execute(fromIndex);
        if (fromIndexNumber instanceof Long && ((Long) fromIndexNumber) > Integer.MAX_VALUE) {
            return RegexResult.NO_MATCH;
        }
        return (RegexResult) directCallNode.call(new Object[]{regexObject, unboxedInput, NumberConversion.intValue(fromIndexNumber)});
    }

    @Specialization(replaces = "doCached")
    public RegexResult doUnCached(CompiledRegex receiver, RegexObject regexObject, Object input, Object fromIndex,
                    @Cached("create()") ExpectStringOrTruffleObjectNode expectStringOrTruffleObjectNode,
                    @Cached("create()") ExpectNumberNode expectNumberNode,
                    @Cached("create()") IndirectCallNode indirectCallNode) {
        final Object unboxedInput = expectStringOrTruffleObjectNode.execute(input);
        final Number fromIndexNumber = expectNumberNode.execute(fromIndex);
        if (fromIndexNumber instanceof Long && ((Long) fromIndexNumber) > Integer.MAX_VALUE) {
            return RegexResult.NO_MATCH;
        }
        return (RegexResult) indirectCallNode.call(receiver.getRegexCallTarget(), new Object[]{regexObject, unboxedInput, NumberConversion.intValue(fromIndexNumber)});
    }

    public static ExecuteRegexDispatchNode create() {
        return ExecuteRegexDispatchNodeGen.create();
    }
}
