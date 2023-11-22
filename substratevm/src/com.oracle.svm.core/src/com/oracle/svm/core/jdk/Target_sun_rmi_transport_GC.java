/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import java.util.function.BooleanSupplier;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.Heap;

/**
 * Note that sun.rmi.transport.GC is initialized at build-time to avoid including the rmi library,
 * which is not needed as it only implements the native maxObjectInspectionAge() method, which in
 * turn is {@link Target_sun_rmi_transport_GC#maxObjectInspectionAge substituted in here}.
 */
@TargetClass(className = "sun.rmi.transport.GC", onlyWith = JavaRMIModuleAvailable.class)
public final class Target_sun_rmi_transport_GC {
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static Thread daemon = null;

    @Substitute
    public static long maxObjectInspectionAge() {
        return Heap.getHeap().getMillisSinceLastWholeHeapExamined();
    }
}

class JavaRMIModuleAvailable implements BooleanSupplier {

    private static final boolean hasModule;

    static {
        var module = ModuleLayer.boot().findModule("java.rmi");
        if (module.isPresent()) {
            JavaRMIModuleAvailable.class.getModule().addReads(module.get());
        }
        hasModule = module.isPresent();
    }

    @Override
    public boolean getAsBoolean() {
        return hasModule;
    }
}
