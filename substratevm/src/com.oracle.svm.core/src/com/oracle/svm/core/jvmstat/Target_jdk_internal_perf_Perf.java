/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.perf.Perf")
@SuppressWarnings({"unused", "static-method"})
public final class Target_jdk_internal_perf_Perf {
    @Substitute
    public ByteBuffer attach(String user, int lvmid, int mode) {
        return ImageSingletons.lookup(PerfDataSupport.class).attach(user, lvmid, mode);
    }

    @Substitute
    public void detach(ByteBuffer bb) {
        ImageSingletons.lookup(PerfDataSupport.class).detach(bb);
    }

    @Substitute
    public long highResCounter() {
        return ImageSingletons.lookup(PerfDataSupport.class).highResCounter();
    }

    @Substitute
    public long highResFrequency() {
        return ImageSingletons.lookup(PerfDataSupport.class).highResFrequency();
    }

    @Substitute
    public ByteBuffer createLong(String name, int variability, int units, long value) {
        return ImageSingletons.lookup(PerfDataSupport.class).createLong(name, variability, units, value);
    }

    @Substitute
    public ByteBuffer createByteArray(String name, int variability, int units, byte[] value, int maxLength) {
        return ImageSingletons.lookup(PerfDataSupport.class).createByteArray(name, variability, units, value, maxLength);
    }

    @Substitute
    private static void registerNatives() {
        // nothing to do
    }
}
