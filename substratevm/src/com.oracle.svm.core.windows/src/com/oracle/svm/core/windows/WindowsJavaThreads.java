/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.ParkEvent;
import com.oracle.svm.core.thread.ParkEvent.ParkEventFactory;

public final class WindowsJavaThreads extends JavaThreads {

    @Fold
    public static WindowsJavaThreads singleton() {
        return (WindowsJavaThreads) JavaThreads.singleton();
    }

    @Platforms(HOSTED_ONLY.class)
    WindowsJavaThreads() {
    }

    @Override
    protected void start0(Thread thread, long stackSize) {
    }

    @Override
    protected void setNativeName(String name) {
    }

    @Override
    protected void yield() {
    }

}

class WindowsParkEvent extends ParkEvent {

    WindowsParkEvent() {
        /* Create a mutex. */
        /* Create a condition variable. */
    }

    @Override
    protected WaitResult condWait() {
        WaitResult result = WaitResult.UNPARKED;
        return result;
    }

    @Override
    protected WaitResult condTimedWait(long delayNanos) {
        WaitResult result = WaitResult.UNPARKED;
        return result;
    }

    @Override
    protected void unpark() {
    }
}

class WindowsParkEventFactory implements ParkEventFactory {
    @Override
    public ParkEvent create() {
        return new WindowsParkEvent();
    }
}

@AutomaticFeature
@Platforms(Platform.WINDOWS.class)
class WindowsThreadsFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JavaThreads.class, new WindowsJavaThreads());
        ImageSingletons.add(ParkEventFactory.class, new WindowsParkEventFactory());
    }
}
