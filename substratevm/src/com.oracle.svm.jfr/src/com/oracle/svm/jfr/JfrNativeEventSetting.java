/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import com.oracle.svm.core.annotate.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Stores the settings of a native JFR event. All instances of this class are guaranteed to live in
 * the image heap and don't contain any object references. So, accessing the data is always safe
 * (even during a GC).
 */
public class JfrNativeEventSetting {
    private long thresholdTicks;
    private long cutoffTicks;
    private boolean stackTrace;
    private boolean enabled;
    private boolean large;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrNativeEventSetting() {
    }

    public long getThresholdTicks() {
        return thresholdTicks;
    }

    public void setThresholdTicks(long thresholdTicks) {
        this.thresholdTicks = thresholdTicks;
    }

    public long getCutoffTicks() {
        return cutoffTicks;
    }

    public void setCutoffTicks(long cutoffTicks) {
        this.cutoffTicks = cutoffTicks;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean hasStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(boolean stackTrace) {
        this.stackTrace = stackTrace;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLarge() {
        return large;
    }

    public void setLarge(boolean large) {
        this.large = large;
    }
}
