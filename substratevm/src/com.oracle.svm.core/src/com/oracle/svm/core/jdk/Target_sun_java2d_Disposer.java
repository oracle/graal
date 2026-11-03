/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Hashtable;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.util.JVMCIReflectionUtil;

/**
 * Allows the Java2D Disposer thread to exit during isolate tear-down. The JDK implementation
 * ignores interrupts and would otherwise keep the thread alive, blocking tear-down.
 */
@TargetClass(className = "sun.java2d.Disposer", onlyWith = JavaDesktopModuleAvailable.class)
final class Target_sun_java2d_Disposer {
    @Alias private static ReferenceQueue<Object> queue;
    @Alias private static Hashtable<Reference<Object>, Target_sun_java2d_DisposerRecord> records;

    @Alias
    private static native void safeDispose(Target_sun_java2d_DisposerRecord rec);

    @Alias
    private static native void clearDeferredRecords();

    @SuppressWarnings("static-method")
    @Substitute
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25.0.4.1.1+1/src/java.desktop/share/classes/sun/java2d/Disposer.java#L130-L144")
    public void run() {
        while (true) {
            try {
                Reference<?> obj = queue.remove();
                obj.clear();
                Target_sun_java2d_DisposerRecord rec = records.remove(obj);
                safeDispose(rec);
                obj = null;
                rec = null;
                clearDeferredRecords();
            } catch (Exception e) {
                /*
                 * The JDK's Disposer catches InterruptedException and retries queue.remove(), so
                 * it never detaches after isolate teardown interrupts it. Keep the JDK behavior
                 * for other exceptions and interrupts outside teardown.
                 */
                if (e instanceof InterruptedException && VMThreads.isTearingDown()) {
                    return;
                }
                System.out.println("Exception while removing reference.");
            }
        }
    }
}

@TargetClass(className = "sun.java2d.DisposerRecord", onlyWith = JavaDesktopModuleAvailable.class)
interface Target_sun_java2d_DisposerRecord {
}

class JavaDesktopModuleAvailable implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return JVMCIReflectionUtil.bootModuleLayer().findModule("java.desktop").isPresent();
    }
}
