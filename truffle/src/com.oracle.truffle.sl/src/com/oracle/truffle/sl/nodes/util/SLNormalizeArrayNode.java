/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.util;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

/**
 * Normalizes an array of arguments. It speculates that no reallocation of the object array is
 * needed.
 */
@GenerateUncached
public abstract class SLNormalizeArrayNode extends Node {

    public abstract Object[] executeConvert(Object[] args);

    static class Uncached extends SLNormalizeArrayNode {

        static final Uncached INSTANCE = new Uncached();

        private Uncached() {
        }

        @Override
        public Object[] executeConvert(Object[] args) {
            for (int i = 0; i < args.length; i++) {
                args[i] = SLSimplifyNodeGen.getUncached().executeConvert(args[i]);
            }
            return args;
        }

    }

    static class Cached extends SLNormalizeArrayNode {
        @CompilationFinal(dimensions = 1) private volatile SLSimplifyNode[] normalizeNodes;
        @CompilationFinal private volatile boolean needsCopy = false;
        @CompilationFinal private volatile boolean generic = false;

        private Cached() {
        }

        @Override
        public Object[] executeConvert(Object[] args) {
            SLSimplifyNode[] nodes = this.normalizeNodes;
            if (nodes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nodes = new SLSimplifyNode[args.length];
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i] = SLSimplifyNodeGen.create();
                }
                normalizeNodes = nodes;
            }
            if (args.length == nodes.length) {
                // fast path
                if (nodes.length == 0) {
                    return args;
                } else {
                    Object[] newArgs = fastConvertUnroll(nodes, args);
                    return newArgs;
                }
            } else {
                if (!generic || nodes.length != 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    nodes = Arrays.copyOf(nodes, 1);
                    if (nodes[0] == null) {
                        nodes[0] = SLSimplifyNodeGen.create();
                    }
                    this.normalizeNodes = nodes;
                    this.generic = true;
                }
                if (args.length == 0) {
                    return args;
                }
                return fastConvertSingle(nodes[0], args);
            }
        }

        /*
         * Specialization for constant number of arguments. Uses a profile for each argument.
         */
        @ExplodeLoop
        private Object[] fastConvertUnroll(SLSimplifyNode[] nodes, Object[] args) {
            Object[] newArgs = needsCopy ? new Object[nodes.length] : args;
            for (int i = 0; i < nodes.length; i++) {
                Object arg = args[i];
                Object newArg = nodes[i].executeConvert(arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = new Object[nodes.length];
                    System.arraycopy(args, 0, newArgs, 0, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

        /*
         * Specialization that supports multiple argument lengths but uses a single profile for all
         * arguments.
         */
        private Object[] fastConvertSingle(SLSimplifyNode node, Object[] args) {
            assert normalizeNodes[0] != null;
            Object[] newArgs = needsCopy ? new Object[args.length] : args;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object newArg = node.executeConvert(arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = new Object[args.length];
                    System.arraycopy(args, 0, newArgs, 0, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

    }

    public static SLNormalizeArrayNode create() {
        return new Cached();
    }

    public static SLNormalizeArrayNode getUncached() {
        return Uncached.INSTANCE;
    }

}
