/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.jfr.impl;

import jdk.jfr.BooleanFlag;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.DataAmount;
import jdk.jfr.Label;
import jdk.jfr.MemoryAddress;
import jdk.jfr.StackTrace;
import jdk.jfr.Unsigned;
import org.graalvm.compiler.truffle.jfr.CompilationEvent;

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

    private transient CompilationFailureEventImpl failure;

    @Override
    public void compilationStarted() {
        begin();
    }

    @Override
    public void succeeded() {
        end();
        this.success = true;
    }

    @Override
    public void failed(boolean permanent, CharSequence reason) {
        end();
        this.success = false;
        this.failure = new CompilationFailureEventImpl(source, rootFunction, permanent, reason);
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
    public void publish() {
        super.publish();
        if (failure != null) {
            failure.publish();
        }
    }
}
