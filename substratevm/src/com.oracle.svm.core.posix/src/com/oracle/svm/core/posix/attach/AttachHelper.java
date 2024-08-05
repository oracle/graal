/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.attach;

import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;

/** C methods that are used to support the attach API. */
@CLibrary(value = "libchelper", requireStatic = true)
public class AttachHelper {
    @CFunction(value = "svm_attach_startup")
    public static native void startup(CCharPointer path);

    @CFunction(value = "svm_attach_listener_cleanup")
    public static native void listenerCleanup(int listenerSocket, CCharPointer path);

    /** Returns true if the socket file is valid. */
    @CFunction(value = "svm_attach_check_socket_file")
    public static native boolean checkSocketFile(CCharPointer path);

    @CFunction(value = "svm_attach_is_init_trigger")
    public static native boolean isInitTrigger(CCharPointer path);

    @CFunction(value = "svm_attach_create_listener")
    public static native int createListener(CCharPointer path);

    @CFunction(value = "svm_attach_wait_for_request")
    public static native int waitForRequest(int listenerSocket);

    @CFunction(value = "svm_attach_shutdown_socket")
    public static native void shutdownSocket(int socket);
}
