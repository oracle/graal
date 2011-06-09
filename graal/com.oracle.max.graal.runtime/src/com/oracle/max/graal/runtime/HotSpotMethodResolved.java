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

import java.lang.reflect.*;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Implementation of RiMethod for resolved HotSpot methods.
 */
public final class HotSpotMethodResolved extends HotSpotMethod {

    private final long vmId;

    // cached values
    private byte[] code;
    private int accessFlags = -1;
    private int maxLocals = -1;
    private int maxStackSize = -1;
    private int invocationCount = -1;
    private RiExceptionHandler[] exceptionHandlers;
    private RiSignature signature;
    private Boolean hasBalancedMonitors;

    public HotSpotMethodResolved(Compiler compiler, long vmId, String name) {
        super(compiler);
        this.vmId = vmId;
        this.name = name;
        this.holder = compiler.getVMEntries().RiMethod_holder(vmId);
    }

    @Override
    public int accessFlags() {
        if (accessFlags == -1) {
            accessFlags = compiler.getVMEntries().RiMethod_accessFlags(vmId);
        }
        return accessFlags;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return isLeafMethod() || Modifier.isStatic(accessFlags());
    }

    @Override
    public byte[] code() {
        if (code == null) {
            code = compiler.getVMEntries().RiMethod_code(vmId);
        }
        return code;
    }

    @Override
    public RiExceptionHandler[] exceptionHandlers() {
        if (exceptionHandlers == null) {
            exceptionHandlers = compiler.getVMEntries().RiMethod_exceptionHandlers(vmId);
        }
        return exceptionHandlers;
    }

    @Override
    public boolean hasBalancedMonitors() {
        if (hasBalancedMonitors == null) {
            hasBalancedMonitors = compiler.getVMEntries().RiMethod_hasBalancedMonitors(vmId);
        }
        return hasBalancedMonitors;
    }

    @Override
    public boolean isClassInitializer() {
        return "<clinit>".equals(name) && Modifier.isStatic(accessFlags());
    }

    @Override
    public boolean isConstructor() {
        return "<init>".equals(name) && !Modifier.isStatic(accessFlags());
    }

    @Override
    public boolean isLeafMethod() {
        return Modifier.isFinal(accessFlags()) || Modifier.isPrivate(accessFlags());
    }

    @Override
    public boolean isOverridden() {
        throw new UnsupportedOperationException("isOverridden");
    }

    @Override
    public boolean noSafepoints() {
        return false;
    }

    @Override
    public boolean isResolved() {
        return true;
    }

    @Override
    public String jniSymbol() {
        throw new UnsupportedOperationException("jniSymbol");
    }

    public CiBitMap[] livenessMap() {
        return null;
    }

    @Override
    public int maxLocals() {
        if (maxLocals == -1) {
            maxLocals = compiler.getVMEntries().RiMethod_maxLocals(vmId);
        }
        return maxLocals;
    }

    @Override
    public int maxStackSize() {
        if (maxStackSize == -1) {
            maxStackSize = compiler.getVMEntries().RiMethod_maxStackSize(vmId);
        }
        return maxStackSize;
    }

    @Override
    public RiMethodProfile methodData() {
        return null;
    }

    @Override
    public StackTraceElement toStackTraceElement(int bci) {
        return CiUtil.toStackTraceElement(this, bci);
    }

    public RiMethod uniqueConcreteMethod() {
        return compiler.getVMEntries().RiMethod_uniqueConcreteMethod(vmId);
    }

    @Override
    public RiSignature signature() {
        if (signature == null) {
            signature = new HotSpotSignature(compiler, compiler.getVMEntries().RiMethod_signature(vmId));
        }
        return signature;
    }

    @Override
    public String toString() {
        return "HotSpotMethod<" + holder().name() + ". " + name + ">";
    }

    public boolean hasCompiledCode() {
        // TODO: needs a VMEntries to go cache the result of that method.
        // This isn't used by GRAAL for now, so this is enough.
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

    @Override
    public boolean minimalDebugInfo() {
        return false;
    }

    public int invocationCount() {
        if (invocationCount == -1) {
            invocationCount = compiler.getVMEntries().RiMethod_invocationCount(vmId);
        }
        return invocationCount;
    }

    public RiTypeProfile typeProfile(int bci) {
        return compiler.getVMEntries().RiMethod_typeProfile(vmId, bci);
    }
}
