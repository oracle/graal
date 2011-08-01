/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.runtime;

import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Implementation of RiMethod for unresolved HotSpot methods.
 */
public final class HotSpotMethodUnresolved extends HotSpotMethod {
    private final RiSignature signature;

    public HotSpotMethodUnresolved(Compiler compiler, String name, String signature, RiType holder) {
        super(compiler);
        this.name = name;
        this.holder = holder;
        this.signature = new HotSpotSignature(compiler, signature);
    }

    @Override
    public RiSignature signature() {
        return signature;
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public byte[] code() {
        throw unresolved("code");
    }

    @Override
    public int codeSize() {
        return 0;
    }

    @Override
    public RiMethodProfile methodData() {
        throw unresolved("methodData");
    }

    @Override
    public String jniSymbol() {
        throw unresolved("jniSymbol");
    }

    @Override
    public int maxLocals() {
        throw unresolved("maxLocals");
    }

    @Override
    public int maxStackSize() {
        throw unresolved("maxStackSize");
    }

    @Override
    public boolean hasBalancedMonitors() {
        throw unresolved("hasBalancedMonitors");
    }

    @Override
    public int accessFlags() {
        throw unresolved("accessFlags");
    }

    @Override
    public boolean isLeafMethod() {
        throw unresolved("isLeafMethod");
    }

    @Override
    public boolean isClassInitializer() {
        return "<clinit>".equals(name);
    }

    @Override
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    @Override
    public boolean isOverridden() {
        throw unresolved("isOverridden");
    }

    @Override
    public boolean noSafepoints() {
        return false;
    }

    @Override
    public BitMap[] livenessMap() {
        return null;
    }

    @Override
    public StackTraceElement toStackTraceElement(int bci) {
        return CiUtil.toStackTraceElement(this, bci);
    }

    @Override
    public boolean canBeStaticallyBound() {
        throw unresolved("canBeStaticallyBound");
    }

    @Override
    public RiExceptionHandler[] exceptionHandlers() {
        throw unresolved("exceptionHandlers");
    }

    @Override
    public boolean minimalDebugInfo() {
        throw unresolved("minimalDebugInfo");
    }

    private CiUnresolvedException unresolved(String operation) {
        return new CiUnresolvedException(operation + " not defined for unresolved method " + name);
    }

    @Override
    public String toString() {
        return "HotSpotMethod<" + holder.name() + ". " + name + ", unresolved>";
    }

    public boolean hasCompiledCode() {
        return false;
    }

    @Override
    public RiType accessor() {
        return null;
    }

    @Override
    public int intrinsic() {
        return 0;
    }

    public int invocationCount() {
        return -1;
    }

    public int exceptionProbability(int bci) {
        return -1;
    }

    public RiTypeProfile typeProfile(int bci) {
        return null;
    }

    public double branchProbability(int bci) {
        return -1;
    }

    public double[] switchProbability(int bci) {
        return null;
    }

    @Override
    public int compiledCodeSize() {
        return -1;
    }
}
