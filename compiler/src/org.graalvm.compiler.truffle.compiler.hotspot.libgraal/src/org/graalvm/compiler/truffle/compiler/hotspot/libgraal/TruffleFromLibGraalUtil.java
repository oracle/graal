/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.FromLibGraalUtil;
import org.graalvm.libgraal.jni.JNI;

/**
 * Helpers for calling methods in {@value #FROM_LIBGRAAL_ENTRY_POINTS_CLASS_NAME} via JNI.
 */
final class TruffleFromLibGraalUtil extends FromLibGraalUtil<Id> {

    /**
     * Name of the class in the HotSpot heap to which the calls are made via JNI.
     */
    private static final String FROM_LIBGRAAL_ENTRY_POINTS_CLASS_NAME = "org.graalvm.compiler.truffle.runtime.hotspot.libgraal.TruffleFromLibGraalEntryPoints";

    private volatile JNI.JClass fromLibGraalEntryPointsPeer;

    static final TruffleFromLibGraalUtil INSTANCE = new TruffleFromLibGraalUtil();

    private TruffleFromLibGraalUtil() {
        super(Id.class);
    }

    @Override
    protected JNI.JClass peer(JNIEnv env) {
        if (fromLibGraalEntryPointsPeer.isNull()) {
            fromLibGraalEntryPointsPeer = getJNIClass(env, FROM_LIBGRAAL_ENTRY_POINTS_CLASS_NAME);
        }
        return fromLibGraalEntryPointsPeer;
    }
}
