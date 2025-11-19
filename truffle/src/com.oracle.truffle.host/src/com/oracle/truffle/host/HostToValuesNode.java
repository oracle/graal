/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.host;

import org.graalvm.polyglot.Value;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline(true)
@GenerateCached(false)
@GenerateUncached
public abstract class HostToValuesNode extends Node {

    private static final Value[] EMPTY = new Value[0];

    abstract Value[] execute(Node node, HostContext context, Object[] args);

    @Specialization(guards = "args.length == 0")
    @SuppressWarnings("unused")
    static Value[] doZero(HostContext context, Object[] args) {
        return EMPTY;
    }

    /*
     * Specialization for constant number of arguments. Uses a profile for each argument.
     */
    @ExplodeLoop
    @Specialization(replaces = {"doZero"}, guards = "args.length == toValues.length", limit = "1")
    static Value[] doCached(HostContext context, Object[] args,
                    @Cached("createArray(args.length)") HostToValueNode[] toValues) {
        Value[] newArgs = new Value[toValues.length];
        for (int i = 0; i < toValues.length; i++) {
            newArgs[i] = toValues[i].execute(context, args[i]);
        }
        return newArgs;
    }

    /*
     * Specialization for constant number of arguments. Uses a profile for each argument.
     */
    @Specialization(replaces = {"doZero", "doCached"})
    static Value[] doGeneric(HostContext context, Object[] args,
                    @Cached HostToValueNode toValue) {
        Value[] newArgs = new Value[args.length];
        for (int i = 0; i < args.length; i++) {
            newArgs[i] = toValue.execute(context, args[i]);
        }
        return newArgs;
    }

    @NeverDefault
    static HostToValueNode[] createArray(int length) {
        HostToValueNode[] nodes = new HostToValueNode[length];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = HostToValueNodeGen.create();
        }
        return nodes;
    }

}
