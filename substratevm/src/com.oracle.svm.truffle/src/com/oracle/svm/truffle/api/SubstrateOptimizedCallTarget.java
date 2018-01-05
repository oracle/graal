/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.truffle.api;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.InvokeJavaFunctionPointer;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateOptimizedCallTarget extends OptimizedCallTarget implements SubstrateInstalledCode {

    public SubstrateOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        super(sourceCallTarget, rootNode);
    }

    @Override
    public long getStart() {
        throw shouldNotReachHere("No implementation in Substrate VM");
    }

    @Override
    public byte[] getCode() {
        throw shouldNotReachHere("No implementation in Substrate VM");
    }

    @SuppressWarnings("sync-override")
    @Override
    public SubstrateSpeculationLog getSpeculationLog() {
        return (SubstrateSpeculationLog) super.getSpeculationLog();
    }

    @Override
    public void setAddress(long address, ResolvedJavaMethod m) {
        this.address = address;
        this.entryPoint = address;
    }

    @Override
    public void clearAddress() {
        this.address = 0;
        this.entryPoint = 0;
    }

    @Override
    public Object doInvoke(Object[] args) {
        /*
         * We have to be very careful that the calling code is uninterruptible, i.e., has no
         * safepoint between the read of the compiled code address and the indirect call to this
         * address. Otherwise, the code can be invalidated concurrently and we invoke an address
         * that no longer contains executable code.
         */
        long start = address;
        if (start != 0) {
            CallBoundaryFunctionPointer target = WordFactory.pointer(start);
            return KnownIntrinsics.convertUnknownValue(target.invoke(this, args), Object.class);
        } else {
            return callBoundary(args);
        }
    }

    interface CallBoundaryFunctionPointer extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        Object invoke(OptimizedCallTarget receiver, Object[] args);
    }
}
