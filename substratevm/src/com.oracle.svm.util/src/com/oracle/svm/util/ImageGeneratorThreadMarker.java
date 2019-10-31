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
package com.oracle.svm.util;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Marker interface to identify threads that are only used by SubstateVM infrastructure and will not
 * be present in the image heap at run time. Each of such threads has a {@link #asTerminated() way}
 * to obtain the terminated replacement of itself.
 * <p>
 * Not intented to be implemented by customer threads. Allowing background threads that are not
 * terminated can lead to very hard to debug errors. If you have background threads running while
 * the static analysis is running, then bad race conditions can happen: the static analysis looks at
 * a heap snapshot at a "random" time from the point of view of the background thread. If the
 * background thread makes new objects available after the last heap snapshot, then the static
 * analysis misses objects of the image heap, which leads to either strange errors during image
 * generation (when the image heap is written), or even worse strange behavior at run time (when a
 * field is, e.g., constant folded to {@code null} because the static analysis only saw the
 * {@code null} value).
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface ImageGeneratorThreadMarker /* extends Thread */ {
    default Thread asTerminated() {
        return TerminatedThread.SINGLETON;
    }
}

final class TerminatedThread extends Thread {
    static final TerminatedThread SINGLETON;
    static {
        SINGLETON = new TerminatedThread("Terminated Infrastructure Thread");
        SINGLETON.start();
        try {
            SINGLETON.join();
        } catch (InterruptedException ex) {
            throw new IllegalStateException(ex);
        }
    }

    TerminatedThread(String name) {
        super(name);
    }
}
