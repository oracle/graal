/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.instrument;

import org.graalvm.options.OptionValues;

public final class KeyStoreOptions {

    private static final String KEY_STORE_FILE_PATH = "javax.net.ssl.keyStore";
    private static final String KEY_STORE_TYPE = "javax.net.ssl.keyStoreType";
    private static final String KEY_STORE_FILE_PASSWORD = "javax.net.ssl.keyStorePassword";
    private static final String KEY_STORE_KEY_RECOVER_PASSWORD = "javax.net.ssl.keyPassword";

    private final OptionValues options;

    KeyStoreOptions(OptionValues options) {
        this.options = options;
    }

    public String getKeyStore() {
        String ks = options.get(InspectorInstrument.KeyStore);
        if (ks.isEmpty()) {
            ks = System.getProperty(KEY_STORE_FILE_PATH);
        }
        return ks;
    }

    public String getKeyStoreType() {
        String kst = options.get(InspectorInstrument.KeyStoreType);
        if (kst.isEmpty()) {
            kst = System.getProperty(KEY_STORE_TYPE);
        }
        return kst;
    }

    public String getKeyStorePassword() {
        String ksp = options.get(InspectorInstrument.KeyStorePassword);
        if (ksp.isEmpty()) {
            ksp = System.getProperty(KEY_STORE_FILE_PASSWORD);
        }
        return ksp;
    }

    public String getKeyPassword() {
        String kp = options.get(InspectorInstrument.KeyPassword);
        if (kp.isEmpty()) {
            kp = System.getProperty(KEY_STORE_KEY_RECOVER_PASSWORD);
        }
        return kp;
    }
}
