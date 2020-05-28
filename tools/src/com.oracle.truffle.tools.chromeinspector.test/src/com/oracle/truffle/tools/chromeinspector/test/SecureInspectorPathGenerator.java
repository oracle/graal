/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.test;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * This class contains just static methods for for providing random token in tests.
 *
 * Even during testing, we need securely generated unpredictable path. Without that, an attacker
 * could connect to the debugger via a webpage using WebSocket, because same-origin policy is not
 * applied to WebSockets. As debuggers are quite privileged, this could lead even to remote code
 * execution, just when running tests while having a malicious webpage opened in a webbrowser.
 */
public class SecureInspectorPathGenerator {

    private static final String TOKEN = generateSecureToken();

    /**
     * Returns a token. Repeated calls within a single run return the same value within a single JVM
     * instance. However, the value is not related to values returned in other JVM instances.
     *
     * @return cryptographically secure constant token
     */
    public static String getToken() {
        return TOKEN;
    }

    // Methods are mostly copied from InspectorInstrument.java. I know, it is not ideal, but I am
    // not sure about a good place for those reused methods.

    private static String generateSecureToken() {
        final byte[] tokenRaw = generateSecureRawToken();
        // base64url (see https://tools.ietf.org/html/rfc4648 ) without padding
        // For a fixed-length token, there is no ambiguity in paddingless
        return Base64.getEncoder().withoutPadding().encodeToString(tokenRaw).replace('/', '_').replace('+', '-');
    }

    private static byte[] generateSecureRawToken() {
        // 256 bits of entropy ought to be enough for everybody
        final byte[] tokenRaw = new byte[32];
        new SecureRandom().nextBytes(tokenRaw);
        return tokenRaw;
    }

}
