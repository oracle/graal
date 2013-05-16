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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@code com.sun.crypto.provider.AESCrypt} methods.
 */
@ClassSubstitution(className = "com.sun.crypto.provider.AESCrypt", optional = true)
public class AESCryptSubstitutions {

    static final long kOffset;
    static final Class<?> AESCryptClass;

    static {
        try {
            // Need to use launcher class path as com.sun.crypto.provider.AESCrypt
            // is normally not on the boot class path
            ClassLoader cl = Launcher.getLauncher().getClassLoader();
            AESCryptClass = Class.forName("com.sun.crypto.provider.AESCrypt", true, cl);
            kOffset = UnsafeAccess.unsafe.objectFieldOffset(AESCryptClass.getDeclaredField("K"));
        } catch (Exception ex) {
            throw new GraalInternalError(ex);
        }
    }

    @MethodSubstitution(isStatic = false)
    static void encryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, true);
    }

    @MethodSubstitution(isStatic = false)
    static void decryptBlock(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset) {
        crypt(rcvr, in, inOffset, out, outOffset, false);
    }

    private static void crypt(Object rcvr, byte[] in, int inOffset, byte[] out, int outOffset, boolean encrypt) {
        Word kAddr = Word.fromObject(rcvr).readWord(Word.unsigned(kOffset), ANY_LOCATION).add(arrayBaseOffset(Kind.Byte));
        Word inAddr = Word.unsigned(GetObjectAddressNode.get(in) + arrayBaseOffset(Kind.Byte) + inOffset);
        Word outAddr = Word.unsigned(GetObjectAddressNode.get(out) + arrayBaseOffset(Kind.Byte) + outOffset);
        if (encrypt) {
            EncryptBlockStubCall.call(inAddr, outAddr, kAddr);
        } else {
            DecryptBlockStubCall.call(inAddr, outAddr, kAddr);
        }
    }

    abstract static class CryptBlockStubCall extends DeoptimizingStubCall implements LIRGenLowerable {

        @Input private ValueNode in;
        @Input private ValueNode out;
        @Input private ValueNode key;

        private final ForeignCallDescriptor descriptor;

        public CryptBlockStubCall(ValueNode in, ValueNode out, ValueNode key, ForeignCallDescriptor descriptor) {
            super(StampFactory.forVoid());
            this.in = in;
            this.out = out;
            this.key = key;
            this.descriptor = descriptor;
        }

        @Override
        public void generate(LIRGenerator gen) {
            ForeignCallLinkage linkage = gen.getRuntime().lookupForeignCall(descriptor);
            gen.emitForeignCall(linkage, null, gen.operand(in), gen.operand(out), gen.operand(key));
        }
    }

    public static class EncryptBlockStubCall extends CryptBlockStubCall {

        public static final ForeignCallDescriptor ENCRYPT_BLOCK = new ForeignCallDescriptor("encrypt_block", void.class, Word.class, Word.class, Word.class);

        public EncryptBlockStubCall(ValueNode in, ValueNode out, ValueNode key) {
            super(in, out, key, ENCRYPT_BLOCK);
        }

        @NodeIntrinsic
        public static native void call(Word in, Word out, Word key);
    }

    public static class DecryptBlockStubCall extends CryptBlockStubCall {

        public static final ForeignCallDescriptor DECRYPT_BLOCK = new ForeignCallDescriptor("decrypt_block", void.class, Word.class, Word.class, Word.class);

        public DecryptBlockStubCall(ValueNode in, ValueNode out, ValueNode key) {
            super(in, out, key, DECRYPT_BLOCK);
        }

        @NodeIntrinsic
        public static native void call(Word in, Word out, Word key);
    }
}
