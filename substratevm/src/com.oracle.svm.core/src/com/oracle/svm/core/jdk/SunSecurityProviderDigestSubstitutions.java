/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

import static org.graalvm.nativeimage.impl.InternalPlatform.NATIVE_ONLY;

import org.graalvm.nativeimage.Platform;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.MD5MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.MD5Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA1Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA256Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA3Node;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512MultiBlockNode;
import jdk.graal.compiler.replacements.nodes.MessageDigestNode.SHA512Node;
import jdk.vm.ci.meta.JavaKind;

@TargetClass(className = "sun.security.provider.DigestBase")
final class Target_sun_security_provider_DigestBase {
    @Alias protected int blockSize;

    @Alias
    native void implCompress(byte[] b, int ofs);

    @Substitute
    private int implCompressMultiBlock0(byte[] b, int ofs, int limit) {
        Object receiver = SubstrateUtil.cast(this, Object.class);

        if (DigestBaseSubstitutionSupport.useMD5Intrinsics() && receiver instanceof Target_sun_security_provider_MD5) {
            Target_sun_security_provider_MD5 md5 = SubstrateUtil.cast(this, Target_sun_security_provider_MD5.class);
            return DigestBaseSubstitutionSupport.md5ImplCompressMB(b, md5.state, ofs, limit);
        } else if (DigestBaseSubstitutionSupport.useSHA1Intrinsics() && receiver instanceof Target_sun_security_provider_SHA) {
            Target_sun_security_provider_SHA sha1 = SubstrateUtil.cast(this, Target_sun_security_provider_SHA.class);
            return DigestBaseSubstitutionSupport.sha1ImplCompressMB(b, sha1.state, ofs, limit);
        } else if (DigestBaseSubstitutionSupport.useSHA256Intrinsics() && receiver instanceof Target_sun_security_provider_SHA2) {
            Target_sun_security_provider_SHA2 sha256 = SubstrateUtil.cast(this, Target_sun_security_provider_SHA2.class);
            return DigestBaseSubstitutionSupport.sha256ImplCompressMB(b, sha256.state, ofs, limit);
        } else if (DigestBaseSubstitutionSupport.useSHA512Intrinsics() && receiver instanceof Target_sun_security_provider_SHA5) {
            Target_sun_security_provider_SHA5 sha512 = SubstrateUtil.cast(this, Target_sun_security_provider_SHA5.class);
            return DigestBaseSubstitutionSupport.sha512ImplCompressMB(b, sha512.state, ofs, limit);
        } else if (DigestBaseSubstitutionSupport.useSHA3Intrinsics() && receiver instanceof Target_sun_security_provider_SHA3) {
            Target_sun_security_provider_SHA3 sha3 = SubstrateUtil.cast(this, Target_sun_security_provider_SHA3.class);
            return DigestBaseSubstitutionSupport.sha3ImplCompressMB(b, sha3.state, blockSize, ofs, limit);
        } else {
            int nextOfs = ofs;
            for (; nextOfs <= limit; nextOfs += blockSize) {
                implCompress(b, nextOfs);
            }
            return nextOfs;
        }
    }

}

final class DigestBaseSubstitutionSupport {
    private DigestBaseSubstitutionSupport() {
    }

    @Fold
    static int arrayBaseOffset(JavaKind kind) {
        return ObjectLayout.singleton().getArrayBaseOffset(kind);
    }

    @Uninterruptible(reason = "Digest stubs access array storage through raw pointers.")
    static int md5ImplCompressMB(byte[] buf, int[] state, int ofs, int limit) {
        Pointer bufAddr = Word.objectToUntrackedPointer(buf).add(arrayBaseOffset(JavaKind.Byte) + ofs);
        Pointer stateAddr = Word.objectToUntrackedPointer(state).add(arrayBaseOffset(JavaKind.Int));
        return MD5MultiBlockNode.md5ImplCompressMB(bufAddr, stateAddr, ofs, limit);
    }

    @Uninterruptible(reason = "Digest stubs access array storage through raw pointers.")
    static int sha1ImplCompressMB(byte[] buf, int[] state, int ofs, int limit) {
        Pointer bufAddr = Word.objectToUntrackedPointer(buf).add(arrayBaseOffset(JavaKind.Byte) + ofs);
        Pointer stateAddr = Word.objectToUntrackedPointer(state).add(arrayBaseOffset(JavaKind.Int));
        return SHA1MultiBlockNode.sha1ImplCompressMB(bufAddr, stateAddr, ofs, limit);
    }

    @Uninterruptible(reason = "Digest stubs access array storage through raw pointers.")
    static int sha256ImplCompressMB(byte[] buf, int[] state, int ofs, int limit) {
        Pointer bufAddr = Word.objectToUntrackedPointer(buf).add(arrayBaseOffset(JavaKind.Byte) + ofs);
        Pointer stateAddr = Word.objectToUntrackedPointer(state).add(arrayBaseOffset(JavaKind.Int));
        return SHA256MultiBlockNode.sha256ImplCompressMB(bufAddr, stateAddr, ofs, limit);
    }

    @Uninterruptible(reason = "Digest stubs access array storage through raw pointers.")
    static int sha512ImplCompressMB(byte[] buf, long[] state, int ofs, int limit) {
        Pointer bufAddr = Word.objectToUntrackedPointer(buf).add(arrayBaseOffset(JavaKind.Byte) + ofs);
        Pointer stateAddr = Word.objectToUntrackedPointer(state).add(arrayBaseOffset(JavaKind.Long));
        return SHA512MultiBlockNode.sha512ImplCompressMB(bufAddr, stateAddr, ofs, limit);
    }

    @Uninterruptible(reason = "Digest stubs access array storage through raw pointers.")
    static int sha3ImplCompressMB(byte[] buf, long[] state, int blockSize, int ofs, int limit) {
        Pointer bufAddr = Word.objectToUntrackedPointer(buf).add(arrayBaseOffset(JavaKind.Byte) + ofs);
        Pointer stateAddr = Word.objectToUntrackedPointer(state).add(arrayBaseOffset(JavaKind.Long));
        return SHA3MultiBlockNode.sha3ImplCompressMB(bufAddr, stateAddr, blockSize, ofs, limit);
    }

    @Fold
    static boolean useMD5Intrinsics() {
        return supportsStubBasedIntrinsics() && MD5Node.isSupported(SubstrateTarget.getArchitecture());
    }

    @Fold
    static boolean useSHA1Intrinsics() {
        return supportsStubBasedIntrinsics() && SHA1Node.isSupported(SubstrateTarget.getArchitecture());
    }

    @Fold
    static boolean useSHA256Intrinsics() {
        return supportsStubBasedIntrinsics() && SHA256Node.isSupported(SubstrateTarget.getArchitecture());
    }

    @Fold
    static boolean useSHA512Intrinsics() {
        return supportsStubBasedIntrinsics() && SHA512Node.isSupported(SubstrateTarget.getArchitecture());
    }

    @Fold
    static boolean useSHA3Intrinsics() {
        return supportsStubBasedIntrinsics() && SHA3Node.isSupported(SubstrateTarget.getArchitecture());
    }

    @Fold
    static boolean supportsStubBasedIntrinsics() {
        return Platform.includedIn(NATIVE_ONLY.class) && !SubstrateOptions.useLLVMBackend();
    }
}

@TargetClass(className = "sun.security.provider.MD5")
final class Target_sun_security_provider_MD5 {
    @Alias int[] state;
}

@TargetClass(className = "sun.security.provider.SHA")
final class Target_sun_security_provider_SHA {
    @Alias int[] state;
}

@TargetClass(className = "sun.security.provider.SHA2")
final class Target_sun_security_provider_SHA2 {
    @Alias int[] state;
}

@TargetClass(className = "sun.security.provider.SHA3")
final class Target_sun_security_provider_SHA3 {
    @Alias long[] state;
}

@TargetClass(className = "sun.security.provider.SHA5")
final class Target_sun_security_provider_SHA5 {
    @Alias long[] state;
}
