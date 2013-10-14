/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.replacements.*;

/**
 * Filters certain method substitutions based on whether there is underlying hardware support for
 * them.
 */
public class HotSpotReplacementsImpl extends ReplacementsImpl {

    private final HotSpotVMConfig config;

    public HotSpotReplacementsImpl(HotSpotRuntime runtime, Assumptions assumptions, TargetDescription target) {
        super(runtime, runtime, runtime, runtime, runtime, assumptions, target.wordKind);
        this.config = runtime.config;
    }

    @Override
    protected ResolvedJavaMethod registerMethodSubstitution(Member originalMethod, Method substituteMethod) {
        if (substituteMethod.getDeclaringClass() == IntegerSubstitutions.class || substituteMethod.getDeclaringClass() == LongSubstitutions.class) {
            if (substituteMethod.getName().equals("bitCount")) {
                if (!config.usePopCountInstruction) {
                    return null;
                }
            }
        } else if (substituteMethod.getDeclaringClass() == AESCryptSubstitutions.class || substituteMethod.getDeclaringClass() == CipherBlockChainingSubstitutions.class) {
            if (!config.useAESIntrinsics) {
                return null;
            }
            assert config.aescryptEncryptBlockStub != 0L;
            assert config.aescryptDecryptBlockStub != 0L;
            assert config.cipherBlockChainingEncryptAESCryptStub != 0L;
            assert config.cipherBlockChainingDecryptAESCryptStub != 0L;
        } else if (substituteMethod.getDeclaringClass() == CRC32Substitutions.class) {
            if (!config.useCRC32Intrinsics) {
                return null;
            }
        }
        return super.registerMethodSubstitution(originalMethod, substituteMethod);
    }

    @Override
    public Class<? extends FixedWithNextNode> getMacroSubstitution(ResolvedJavaMethod method) {
        HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
        int intrinsicId = hsMethod.intrinsicId();
        if (intrinsicId != 0) {
            if (intrinsicId == config.vmIntrinsicInvokeBasic) {
                return MethodHandleInvokeBasicNode.class;
            } else if (intrinsicId == config.vmIntrinsicLinkToInterface) {
                return MethodHandleLinkToInterfaceNode.class;
            } else if (intrinsicId == config.vmIntrinsicLinkToSpecial) {
                return MethodHandleLinkToSpecialNode.class;
            } else if (intrinsicId == config.vmIntrinsicLinkToStatic) {
                return MethodHandleLinkToStaticNode.class;
            } else if (intrinsicId == config.vmIntrinsicLinkToVirtual) {
                return MethodHandleLinkToVirtualNode.class;
            }
        }
        return super.getMacroSubstitution(method);
    }
}
