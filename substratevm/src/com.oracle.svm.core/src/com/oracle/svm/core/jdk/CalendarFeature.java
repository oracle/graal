/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.GregorianCalendar;

import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalMethodProvider;

import jdk.vm.ci.meta.ResolvedJavaType;
import sun.util.calendar.JulianCalendar;

@AutomaticallyRegisteredFeature
public class CalendarFeature implements InternalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        initializeCalendarSystem(((InternalFeatureAccess) access));
        // GregorianCalendar might use JulianCalendar via reflection
        access.registerReachabilityHandler(_ -> {
            RuntimeReflection.register(JulianCalendar.class);
            RuntimeReflection.registerForReflectiveInstantiation(JulianCalendar.class);
        }, GregorianCalendar.class);
    }

    /**
     * Make sure {@code CalendarSystem} is initialized before analysis. Otherwise, the reachability
     * of its {@code initName} method is non-deterministic, because there is a race condition
     * between heap snapshotting and the first call to the {@code forName} method, which triggers
     * the initialization.
     */
    private static void initializeCalendarSystem(InternalFeatureAccess access) {
        ResolvedJavaType calendarSystem = access.findTypeByName("sun.util.calendar.CalendarSystem");
        var forName = JVMCIReflectionUtil.getUniqueDeclaredMethod(access.getMetaAccess(), calendarSystem, "forName", String.class);
        GraalAccess.getVMAccess().invoke(OriginalMethodProvider.getOriginalMethod(forName), null, GraalAccess.getOriginalSnippetReflection().forObject("julian"));
    }
}
