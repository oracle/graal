/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.micro.expr;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.Source;

public abstract class EvalExpression extends Expression {

    final Source source;

    EvalExpression(Source source) {
        this.source = source;
    }

    @TruffleBoundary
    CallTarget parse(Env env) {
        return env.parseInternal(source);
    }

    @Specialization(assumptions = "getLanguage().getSingleContextAssumption()")
    Object doSingleContext(@Cached("create(parse(getContext()))") DirectCallNode call) {
        return call.call();
    }

    @Specialization(replaces = "doSingleContext", guards = "call.getCallTarget() == code")
    Object doCached(@Bind("parse(getContext())") CallTarget code,
                    @Cached("create(code)") DirectCallNode call) {
        assert call.getCallTarget() == code;
        return call.call();
    }

    @Specialization(replaces = "doCached")
    Object generic(@Cached IndirectCallNode call) {
        return call.call(parse(getContext()));
    }
}
