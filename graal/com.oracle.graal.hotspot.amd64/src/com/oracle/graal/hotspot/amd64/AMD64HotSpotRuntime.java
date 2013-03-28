/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.amd64.AMD64.*;
import static com.oracle.graal.compiler.amd64.AMD64LIRGenerator.*;
import static com.oracle.graal.hotspot.amd64.AMD64DeoptimizeOp.*;
import static com.oracle.graal.hotspot.amd64.AMD64HotSpotUnwindOp.*;
import static com.oracle.graal.hotspot.nodes.IdentityHashCodeStubCall.*;
import static com.oracle.graal.hotspot.nodes.MonitorEnterStubCall.*;
import static com.oracle.graal.hotspot.nodes.MonitorExitStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewArraySlowStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewArrayStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewInstanceSlowStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewInstanceStubCall.*;
import static com.oracle.graal.hotspot.nodes.NewMultiArrayStubCall.*;
import static com.oracle.graal.hotspot.nodes.ThreadIsInterruptedStubCall.*;
import static com.oracle.graal.hotspot.nodes.VMErrorNode.*;
import static com.oracle.graal.hotspot.nodes.VerifyOopStubCall.*;
import static com.oracle.graal.hotspot.replacements.AESCryptSubstitutions.DecryptBlockStubCall.*;
import static com.oracle.graal.hotspot.replacements.AESCryptSubstitutions.EncryptBlockStubCall.*;
import static com.oracle.graal.hotspot.replacements.CipherBlockChainingSubstitutions.DecryptAESCryptStubCall.*;
import static com.oracle.graal.hotspot.replacements.CipherBlockChainingSubstitutions.EncryptAESCryptStubCall.*;
import static com.oracle.graal.hotspot.nodes.WriteBarrierPostStubCall.*;
import static com.oracle.graal.hotspot.nodes.WriteBarrierPreStubCall.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.amd64.*;
import com.oracle.graal.replacements.*;

public class AMD64HotSpotRuntime extends HotSpotRuntime {

    public AMD64HotSpotRuntime(HotSpotVMConfig config, HotSpotGraalRuntime graalRuntime) {
        super(config, graalRuntime);

        Kind word = graalRuntime.getTarget().wordKind;

        // @formatter:off
        addRuntimeCall(UNWIND_EXCEPTION, config.unwindExceptionStub,
               /*           temps */ null,
               /*             ret */ ret(Kind.Void),
               /* arg0: exception */ rax.asValue(Kind.Object));

        addRuntimeCall(DEOPTIMIZE, config.deoptimizeStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Void));

        addRuntimeCall(ARITHMETIC_FREM, config.arithmeticFremStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Float),
                /* arg0:         a */ javaCallingConvention(Kind.Float,
                /* arg1:         b */                       Kind.Float));

        addRuntimeCall(ARITHMETIC_DREM, config.arithmeticDremStub,
                /*           temps */ null,
                /*             ret */ ret(Kind.Double),
                /* arg0:         a */ javaCallingConvention(Kind.Double,
                /* arg1:         b */                       Kind.Double));

        addRuntimeCall(MONITORENTER, config.monitorEnterStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ javaCallingConvention(Kind.Object,
                /* arg1:   lock */                       word));

       addRuntimeCall(WBPRECALL, config.wbPreCallStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ javaCallingConvention(Kind.Object));

       addRuntimeCall(WBPOSTCALL, config.wbPostCallStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ javaCallingConvention(Kind.Object, word));

        addRuntimeCall(MONITOREXIT, config.monitorExitStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ javaCallingConvention(Kind.Object,
                /* arg1:   lock */                       word));

        addRuntimeCall(NEW_ARRAY, 0L,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rdx.asValue(word),
                /* arg1: length */ rbx.asValue(Kind.Int));

        addRuntimeCall(NEW_ARRAY_SLOW, config.newArrayStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rdx.asValue(word),
                /* arg1: length */ rbx.asValue(Kind.Int));

        addRuntimeCall(NEW_INSTANCE, 0L,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rdx.asValue(word));

        addRuntimeCall(NEW_INSTANCE_SLOW, config.newInstanceStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rdx.asValue(word));

        addRuntimeCall(NEW_MULTI_ARRAY, config.newMultiArrayStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Object),
                /* arg0:    hub */ rax.asValue(word),
                /* arg1:   rank */ rbx.asValue(Kind.Int),
                /* arg2:   dims */ rcx.asValue(word));

        addRuntimeCall(VERIFY_OOP, config.verifyOopStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0: object */ r13.asValue(Kind.Object));

        addRuntimeCall(VM_ERROR, config.vmErrorStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0:  where */ javaCallingConvention(Kind.Object,
                /* arg1: format */                       Kind.Object,
                /* arg2:  value */                       Kind.Long));

        addRuntimeCall(IDENTITY_HASHCODE, config.identityHashCodeStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Int),
                /* arg0:    obj */ javaCallingConvention(Kind.Object));

        addRuntimeCall(THREAD_IS_INTERRUPTED, config.threadIsInterruptedStub,
                /*        temps */ null,
                /*          ret */ rax.asValue(Kind.Boolean),
                /* arg0: thread */ javaCallingConvention(Kind.Object,
      /* arg1: clearInterrupted */                       Kind.Boolean));

        addRuntimeCall(ENCRYPT_BLOCK, config.aescryptEncryptBlockStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0:     in */ nativeCallingConvention(word,
                /* arg1:    out */                         word,
                /* arg2:    key */                         word));

        addRuntimeCall(DECRYPT_BLOCK, config.aescryptDecryptBlockStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0:     in */ nativeCallingConvention(word,
                /* arg1:    out */                         word,
                /* arg2:    key */                         word));

        addRuntimeCall(ENCRYPT, config.cipherBlockChainingEncryptAESCryptStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0:     in */ nativeCallingConvention(word,
                /* arg1:    out */                         word,
                /* arg2:    key */                         word,
                /* arg3:      r */                         word,
              /* arg4: inLength */                         Kind.Int));

        addRuntimeCall(DECRYPT, config.cipherBlockChainingDecryptAESCryptStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void),
                /* arg0:     in */ nativeCallingConvention(word,
                /* arg1:    out */                         word,
                /* arg2:    key */                         word,
                /* arg3:      r */                         word,
              /* arg4: inLength */                         Kind.Int));

        addRuntimeCall(AMD64HotSpotBackend.EXCEPTION_HANDLER, config.handleExceptionStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void));

        addRuntimeCall(AMD64HotSpotBackend.DEOPT_HANDLER, config.handleDeoptStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void));

        addRuntimeCall(AMD64HotSpotBackend.IC_MISS_HANDLER, config.inlineCacheMissStub,
                /*        temps */ null,
                /*          ret */ ret(Kind.Void));
        // @formatter:on

    }

    private AMD64ConvertSnippets.Templates convertSnippets;

    @Override
    public void installReplacements(Backend backend, ReplacementsInstaller installer, Assumptions assumptions) {
        installer.installSnippets(AMD64ConvertSnippets.class);
        convertSnippets = new AMD64ConvertSnippets.Templates(this, assumptions, graalRuntime.getTarget());
        super.installReplacements(backend, installer, assumptions);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof ConvertNode) {
            convertSnippets.lower((ConvertNode) n, tool);
        } else {
            super.lower(n, tool);
        }
    }

    @Override
    public Register threadRegister() {
        return r15;
    }

    @Override
    public Register stackPointerRegister() {
        return rsp;
    }

    @Override
    protected RegisterConfig createRegisterConfig(boolean globalStubConfig) {
        return new AMD64HotSpotRegisterConfig(config, globalStubConfig);
    }
}
