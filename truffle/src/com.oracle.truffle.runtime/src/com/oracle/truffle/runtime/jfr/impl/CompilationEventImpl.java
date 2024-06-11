/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime.jfr.impl;

import java.util.function.Supplier;

import com.oracle.truffle.runtime.jfr.CompilationEvent;

import jdk.jfr.BooleanFlag;
import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.MemoryAddress;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Unsigned;

@Name("org.graalvm.compiler.truffle.Compilation")
@Category("Truffle Compiler")
@Label("Compilation")
@Description("Truffe Compilation")
@StackTrace(false)
class CompilationEventImpl extends RootFunctionEventImpl implements CompilationEvent {

    @Label("Succeeded") @Description("Compilation Status") @BooleanFlag public boolean success;

    @Label("Compiled Code Size") @Description("Compiled Code Size") @DataAmount @Unsigned public int compiledCodeSize;

    @Label("Compiled Code Address") @Description("Compiled Code Address") @MemoryAddress public long compiledCodeAddress;

    @Label("Inlined Calls") @Description("Inlined Calls") @Unsigned public int inlinedCalls;

    @Label("Dispatched Calls") @Description("Dispatched Calls") @Unsigned public int dispatchedCalls;

    @Label("Graal Nodes") @Description("Graal Node Count") @Unsigned public int graalNodeCount;

    @Label("Truffle Nodes") @Description("Truffle Node Count") @Unsigned public int peNodeCount;

    @Label("Partial Evaluation Time") @Description("Partial Evaluation Time in Milliseconds") @Unsigned public long peTime;

    @Label("Tier") @Description("The Tier of the Truffle Compiler") public int truffleTier;

    private transient CompilationFailureEventImpl failure;

    @Override
    public void compilationStarted() {
        CompilationFailureEventImpl failureEvent = new CompilationFailureEventImpl(engineId, id, source, language, rootFunction);
        if (failureEvent.isEnabled()) {
            failureEvent.begin();
            failure = failureEvent;
        }
        begin();
    }

    @Override
    public void succeeded(int tier) {
        end();
        if (failure != null) {
            failure.end();
            failure = null;
        }
        truffleTier = tier;
        success = true;
    }

    @Override
    public void failed(int tier, boolean permanent, String reason, Supplier<String> lazyStackTrace) {
        end();
        if (failure != null) {
            failure.end();
            failure.setFailureData(tier, permanent, reason, lazyStackTrace == null ? null : lazyStackTrace.get());
        }
        truffleTier = tier;
        success = false;
    }

    @Override
    public void setCompiledCodeSize(int size) {
        this.compiledCodeSize = size;
    }

    @Override
    public void setCompiledCodeAddress(long addr) {
        this.compiledCodeAddress = addr;
    }

    @Override
    public void setInlinedCalls(int count) {
        this.inlinedCalls = count;
    }

    @Override
    public void setDispatchedCalls(int count) {
        this.dispatchedCalls = count;
    }

    @Override
    public void setGraalNodeCount(int count) {
        this.graalNodeCount = count;
    }

    @Override
    public void setPartialEvaluationNodeCount(int count) {
        this.peNodeCount = count;
    }

    @Override
    public void setPartialEvaluationTime(long time) {
        this.peTime = time;
    }

    @Override
    public void publish() {
        super.publish();
        if (failure != null) {
            failure.publish();
        }
    }
}
