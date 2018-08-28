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
package com.oracle.truffle.regex.runtime;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.RegexObject;

public abstract class ExecuteRegexObjectNode extends Node {

    @Child private Node executeNode = Message.EXECUTE.createNode();

    public static ExecuteRegexObjectNode create() {
        return ExecuteRegexObjectNodeGen.create();
    }

    public abstract Object execute(RegexObject receiver, Object input, Object fromIndex);

    @Specialization(guards = "receiver == cachedReceiver", limit = "3")
    protected Object executeFixed(RegexObject receiver, Object input, Object fromIndex,
                    @Cached("receiver") @SuppressWarnings("unused") RegexObject cachedReceiver,
                    @Cached("receiver.getCompiledRegexObject()") TruffleObject cachedCompiledRegex) {
        return doExecute(cachedCompiledRegex, receiver, input, fromIndex);
    }

    @Specialization(replaces = "executeFixed")
    protected Object executeVarying(RegexObject receiver, Object input, Object fromIndex) {
        return doExecute(receiver.getCompiledRegexObject(), receiver, input, fromIndex);
    }

    private Object doExecute(TruffleObject compiledRegexObject, RegexObject regexObject, Object input, Object fromIndex) {
        try {
            return ForeignAccess.sendExecute(executeNode, compiledRegexObject, regexObject, input, fromIndex);
        } catch (InteropException ex) {
            throw ex.raise();
        }
    }
}
