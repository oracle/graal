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
package com.oracle.graal.hotspot.snippets;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.ClassSubstitution.MethodSubstitution;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@code com.sun.crypto.provider.AESCrypt} methods.
 */
@ClassSubstitution(className = "com.sun.crypto.provider.AESCrypt")
public class AESCryptSubstitutions {

    private static final long kOffset;
    static {
        try {
            // Need to use launcher class path as com.sun.crypto.provider.AESCrypt
            // is normally not on the boot class path
            ClassLoader cl = Launcher.getLauncher().getClassLoader();
            kOffset = UnsafeAccess.unsafe.objectFieldOffset(Class.forName("com.sun.crypto.provider.AESCrypt", true, cl).getDeclaredField("K"));
        } catch (Exception ex) {
            throw new GraalInternalError(ex);
        }
    }

    @MethodSubstitution(isStatic = false)
    static void encryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        Word kAddr = Word.unsigned(GetObjectAddressNode.get(rcvr) + kOffset);
        Word inAddr = Word.unsigned(GetObjectAddressNode.get(in) + inOffset);
        Word outAddr = Word.unsigned(GetObjectAddressNode.get(out) + outOffset);
        EncryptBlockStubCall.call(inAddr, outAddr, kAddr);
    }

    @MethodSubstitution(isStatic = false)
    static void decryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        Word kAddr = Word.unsigned(GetObjectAddressNode.get(rcvr) + kOffset);
        Word inAddr = Word.unsigned(GetObjectAddressNode.get(in) + inOffset);
        Word outAddr = Word.unsigned(GetObjectAddressNode.get(out) + outOffset);
        DecryptBlockStubCall.call(inAddr, outAddr, kAddr);
    }

    public static class EncryptBlockStubCall extends FixedWithNextNode implements LIRGenLowerable {

        @Input private final ValueNode in;
        @Input private final ValueNode out;
        @Input private final ValueNode key;

        public static final Descriptor ENCRYPT_BLOCK = new Descriptor("encrypt_block", false, void.class, Word.class, Word.class, Word.class);

        public EncryptBlockStubCall(ValueNode in, ValueNode out, ValueNode key) {
            super(StampFactory.forVoid());
            this.in = in;
            this.out = out;
            this.key = key;
        }

        @Override
        public void generate(LIRGenerator gen) {
            RuntimeCallTarget stub = gen.getRuntime().lookupRuntimeCall(ENCRYPT_BLOCK);
            gen.emitCall(stub, stub.getCallingConvention(), true, gen.operand(in), gen.operand(out), gen.operand(key));
        }

        @NodeIntrinsic
        public static native void call(Word in, Word out, Word key);
    }

    public static class DecryptBlockStubCall extends FixedWithNextNode implements LIRGenLowerable {

        @Input private final ValueNode in;
        @Input private final ValueNode out;
        @Input private final ValueNode key;

        public static final Descriptor DECRYPT_BLOCK = new Descriptor("decrypt_block", false, void.class, Word.class, Word.class, Word.class);

        public DecryptBlockStubCall(ValueNode in, ValueNode out, ValueNode key) {
            super(StampFactory.forVoid());
            this.in = in;
            this.out = out;
            this.key = key;
        }

        @Override
        public void generate(LIRGenerator gen) {
            RuntimeCallTarget stub = gen.getRuntime().lookupRuntimeCall(DECRYPT_BLOCK);
            gen.emitCall(stub, stub.getCallingConvention(), true, gen.operand(in), gen.operand(out), gen.operand(key));
        }

        @NodeIntrinsic
        public static native void call(Word in, Word out, Word key);
    }
}
