/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management;

import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXFromLibGraal.Id.GetFactory;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXFromLibGraal.Id.SignalRegistrationRequest;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXFromLibGraal.Id.Unregister;

import org.graalvm.compiler.hotspot.management.LibGraalMBean.Factory;
import org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXFromLibGraal;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * Entry points in HotSpot for calls from libgraal.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class JMXFromLibGraalEntryPoints {

    private JMXFromLibGraalEntryPoints() {
    }

    /**
     * @see LibGraalMBean#getFactory()
     */
    @JMXFromLibGraal(GetFactory)
    static Object getFactory() {
        Factory factory = LibGraalMBean.getFactory();
        return factory;
    }

    /**
     * @see Factory#signalRegistrationRequest(long)
     */
    @JMXFromLibGraal(SignalRegistrationRequest)
    static void signalRegistrationRequest(Object factory, long isolate) {
        ((Factory) factory).signalRegistrationRequest(isolate);
    }

    /**
     * @see Factory#unregister(long, java.lang.String[])
     */
    @JMXFromLibGraal(Unregister)
    static void unregister(Object factory, long isolate, String[] objectIds) {
        ((Factory) factory).unregister(isolate, objectIds);
    }
}
