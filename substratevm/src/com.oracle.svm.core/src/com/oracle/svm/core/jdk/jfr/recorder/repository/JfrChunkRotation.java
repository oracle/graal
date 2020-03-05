/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jdk.jfr.recorder.repository;

public class JfrChunkRotation {
    private static boolean rotate = false;
    private static long threshold = 0;

    private static Object changeNotifier;

    public static boolean shouldRotate() {
        return rotate;
    }

    public static void onRotation() {
        rotate = false;
    }

    public static void setThreshold(long delta, Object notifier) {
        threshold = delta;
        changeNotifier = notifier;
    }

    public static void evaluate(JfrChunkWriter chunkWriter) {
        assert (threshold > 0);
        if (rotate) {
            return;
        }
        if (chunkWriter.getSizeWritten() > threshold) {
            rotate = true;
            notifyChange();
        }
    }

    private static void notifyChange() {
        // JFR.TODO
        // Need to verify if the object that is substituted via JfrSubstitutions
        // actually causes notifications to the Java layer
        if (changeNotifier != null) {
            synchronized (changeNotifier) {
                changeNotifier.notifyAll();
            }
        }
    }

}
