/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.darwin;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.headers.Pthread;
import com.oracle.svm.core.posix.headers.darwin.DarwinPthread;
import com.oracle.svm.core.stack.StackOverflowCheck;

class DarwinStackOverflowSupport implements StackOverflowCheck.OSSupport {

    @Uninterruptible(reason = "Called while thread is being attached to the VM, i.e., when the thread state is not yet set up.")
    @Override
    public UnsignedWord lookupStackEnd() {
        Pthread.pthread_t self = Pthread.pthread_self();
        UnsignedWord stackaddr = DarwinPthread.pthread_get_stackaddr_np(self);
        UnsignedWord stacksize = DarwinPthread.pthread_get_stacksize_np(self);
        return stackaddr.subtract(stacksize);
    }
}

@AutomaticFeature
class DarwinStackOverflowSupportFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(StackOverflowCheck.OSSupport.class, new DarwinStackOverflowSupport());
    }
}
