/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers.darwin;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.core.posix.headers.darwin.CoreFoundationDirectives;

// Checkstyle: stop

/**
 * Minimal CoreFoundation bindings for parking the Darwin main thread in a CFRunLoop, matching the
 * pattern used by OpenJDK libjli and {@code org.graalvm.launcher.native} {@code ParkEventLoop}.
 */
@CContext(CoreFoundationDirectives.class)
@CLibrary("-framework CoreFoundation")
public class CoreFoundation {

    public interface CFTypeRef extends PointerBase {
    }

    public interface CFAllocatorRef extends CFTypeRef {
    }

    public interface CFRunLoopRef extends CFTypeRef {
    }

    public interface CFRunLoopTimerRef extends CFTypeRef {
    }

    public interface CFStringRef extends CFTypeRef {
    }

    public interface CFRunLoopTimerCallBack extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(CFRunLoopTimerRef timer, VoidPointer info);
    }

    @CConstant
    public static native CFAllocatorRef kCFAllocatorDefault();

    @CConstant
    public static native CFStringRef kCFRunLoopDefaultMode();

    @CConstant
    public static native int kCFRunLoopRunFinished();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native CFRunLoopRef CFRunLoopGetCurrent();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native CFRunLoopTimerRef CFRunLoopTimerCreate(CFAllocatorRef allocator, double fireDate, double interval, long flags, long order,
                    CFRunLoopTimerCallBack callout, VoidPointer context);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void CFRunLoopAddTimer(CFRunLoopRef rl, CFRunLoopTimerRef timer, CFStringRef mode);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int CFRunLoopRunInMode(CFStringRef mode, double seconds, boolean returnAfterSourceHandled);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void CFRelease(CFTypeRef cf);
}
