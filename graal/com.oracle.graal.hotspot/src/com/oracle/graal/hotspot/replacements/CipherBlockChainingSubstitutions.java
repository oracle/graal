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
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@code com.sun.crypto.provider.CipherBlockChaining} methods.
 */
@ClassSubstitution(className = "com.sun.crypto.provider.CipherBlockChaining", optional = true)
public class CipherBlockChainingSubstitutions {

    private static final long embeddedCipherOffset;
    private static final long rOffset;
    static {
        try {
            // Need to use launcher class path as com.sun.crypto.provider.AESCrypt
            // is normally not on the boot class path
            ClassLoader cl = Launcher.getLauncher().getClassLoader();
            embeddedCipherOffset = UnsafeAccess.unsafe.objectFieldOffset(Class.forName("com.sun.crypto.provider.FeedbackCipher", true, cl).getDeclaredField("embeddedCipher"));
            rOffset = UnsafeAccess.unsafe.objectFieldOffset(Class.forName("com.sun.crypto.provider.CipherBlockChaining", true, cl).getDeclaredField("r"));
        } catch (Exception ex) {
            throw new GraalInternalError(ex);
        }
    }

    @Fold
    private static Class getAESCryptClass() {
        return AESCryptSubstitutions.AESCryptClass;
    }

    @MethodSubstitution(isStatic = false)
    static void encrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object embeddedCipher = Word.fromObject(rcvr).readObject(Word.unsigned(embeddedCipherOffset), ANY_LOCATION);
        if (getAESCryptClass().isInstance(embeddedCipher)) {
            crypt(rcvr, in, inOffset, inLength, out, outOffset, embeddedCipher, true);
        } else {
            encrypt(rcvr, in, inOffset, inLength, out, outOffset);
        }
    }

    @MethodSubstitution(isStatic = false)
    static void decrypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset) {
        Object embeddedCipher = Word.fromObject(rcvr).readObject(Word.unsigned(embeddedCipherOffset), ANY_LOCATION);
        if (in != out && getAESCryptClass().isInstance(embeddedCipher)) {
            crypt(rcvr, in, inOffset, inLength, out, outOffset, embeddedCipher, false);
        } else {
            decrypt(rcvr, in, inOffset, inLength, out, outOffset);
        }
    }

    private static void crypt(Object rcvr, byte[] in, int inOffset, int inLength, byte[] out, int outOffset, Object embeddedCipher, boolean encrypt) {
        Word kAddr = Word.fromObject(embeddedCipher).readWord(Word.unsigned(AESCryptSubstitutions.kOffset), ANY_LOCATION).add(arrayBaseOffset(Kind.Byte));
        Word rAddr = Word.unsigned(GetObjectAddressNode.get(rcvr)).readWord(Word.unsigned(rOffset), ANY_LOCATION).add(arrayBaseOffset(Kind.Byte));
        Word inAddr = Word.unsigned(GetObjectAddressNode.get(in) + arrayBaseOffset(Kind.Byte) + inOffset);
        Word outAddr = Word.unsigned(GetObjectAddressNode.get(out) + arrayBaseOffset(Kind.Byte) + outOffset);
        if (encrypt) {
            EncryptAESCryptStubCall.call(inAddr, outAddr, kAddr, rAddr, inLength);
        } else {
            DecryptAESCryptStubCall.call(inAddr, outAddr, kAddr, rAddr, inLength);
        }

    }

    abstract static class AESCryptStubCall extends DeoptimizingStubCall implements LIRGenLowerable {

        @Input private ValueNode in;
        @Input private ValueNode out;
        @Input private ValueNode key;
        @Input private ValueNode r;
        @Input private ValueNode inLength;

        private final ForeignCallDescriptor descriptor;

        public AESCryptStubCall(ValueNode in, ValueNode out, ValueNode key, ValueNode r, ValueNode inLength, ForeignCallDescriptor descriptor) {
            super(StampFactory.forVoid());
            this.in = in;
            this.out = out;
            this.key = key;
            this.r = r;
            this.inLength = inLength;
            this.descriptor = descriptor;
        }

        @Override
        public void generate(LIRGenerator gen) {
            ForeignCallLinkage linkage = gen.getRuntime().lookupForeignCall(descriptor);
            gen.emitForeignCall(linkage, null, gen.operand(in), gen.operand(out), gen.operand(key), gen.operand(r), gen.operand(inLength));
        }
    }

    public static class EncryptAESCryptStubCall extends AESCryptStubCall {

        public static final ForeignCallDescriptor ENCRYPT = new ForeignCallDescriptor("encrypt", void.class, Word.class, Word.class, Word.class, Word.class, int.class);

        public EncryptAESCryptStubCall(ValueNode in, ValueNode out, ValueNode key, ValueNode r, ValueNode inLength) {
            super(in, out, key, r, inLength, ENCRYPT);
        }

        @NodeIntrinsic
        public static native void call(Word in, Word out, Word key, Word r, int inLength);
    }

    public static class DecryptAESCryptStubCall extends AESCryptStubCall {

        public static final ForeignCallDescriptor DECRYPT = new ForeignCallDescriptor("decrypt", void.class, Word.class, Word.class, Word.class, Word.class, int.class);

        public DecryptAESCryptStubCall(ValueNode in, ValueNode out, ValueNode key, ValueNode r, ValueNode inLength) {
            super(in, out, key, r, inLength, DECRYPT);
        }

        @NodeIntrinsic
        public static native void call(Word in, Word out, Word key, Word r, int inLength);
    }
}
