/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.substitute.system;

import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.webimage.substitute.WebImageUtil;

import sun.security.provider.NativePRNG;

public class WebImageSecuritySubstitutions {
}

// Windows does not have a NativePRNG implementation
@TargetClass(value = NativePRNG.class, onlyWith = IsUnix.class)
@SuppressWarnings("all")
final class Target_sun_security_NativePRNG_Web {

    @Substitute
    void engineSetSeed(byte[] seed) {
        // We cannot set the seed in JavaScript.
    }

    @Substitute
    void engineNextBytes(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((WebImageUtil.random() * 256) - 128);
        }
    }

    @Substitute
    byte[] engineGenerateSeed(int numBytes) {
        byte[] bytes = new byte[numBytes];
        engineNextBytes(bytes);
        return bytes;
    }
}

@TargetClass(className = "sun.security.provider.NativeSeedGenerator", onlyWith = IsWindows.class)
final class Target_sun_security_provider_NativeSeedGenerator_Web {

    @Substitute
    private static boolean nativeGenerateSeed(byte[] result) {
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) ((WebImageUtil.random() * 256) - 128);
        }

        return true;
    }
}

/**
 * Windows-specific crypto implementations, available in the {@code jdk.crypto.mscapi} module.
 * <p>
 * We simply delete all of them. They are mostly SPIs and the runtime will just fall back to a
 * platform-independent implementation.
 */

@TargetClass(className = "sun.security.mscapi.CKey", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CKey_Web {
}

@TargetClass(className = "sun.security.mscapi.CKeyPair", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CKeyPair_Web {
}

@TargetClass(className = "sun.security.mscapi.CKeyPairGenerator", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CKeyPairGenerator_Web {
}

@TargetClass(className = "sun.security.mscapi.CKeyStore", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CKeyStore_Web {
}

@TargetClass(className = "sun.security.mscapi.CPrivateKey", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CPrivateKey_Web {
}

@TargetClass(className = "sun.security.mscapi.CPublicKey", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CPublicKey_Web {
}

@TargetClass(className = "sun.security.mscapi.CRSACipher", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CRSACipher_Web {
}

@TargetClass(className = "sun.security.mscapi.CSignature", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_CSignature_Web {
}

@TargetClass(className = "sun.security.mscapi.PRNG", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_PRNG_Web {
}

@TargetClass(className = "sun.security.mscapi.SunMSCAPI", onlyWith = MSCAPIIsEnabled.class)
@Delete
final class Target_sun_security_mscapi_SunMSCAPI_Web {
}

class MSCAPIIsEnabled implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return ModuleLayer.boot().findModule("jdk.crypto.mscapi").isPresent();
    }
}
