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

package com.oracle.svm.hosted.webimage.codegen;

import java.io.PrintStream;

import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.codegen.heap.ConstantMap;
import com.oracle.svm.hosted.webimage.codegen.heap.WebImageObjectInspector;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;

public class WebImageJSProviders extends WebImageProviders {

    private final WebImageObjectInspector objectInspector;
    private final WebImageTypeControl typeControl;
    private final ClassLoader classLoader;

    @SuppressWarnings("this-escape")
    public WebImageJSProviders(CoreProviders underlyingProviders, PrintStream out, DebugContext debug) {
        super(underlyingProviders, out, debug);
        HostedUniverse hUniverse = ((HostedMetaAccess) underlyingProviders.getMetaAccess()).getUniverse();
        this.typeControl = new WebImageTypeControl(hUniverse, this, new ConstantMap(this), WebImageNamingConvention.getInstance());
        this.objectInspector = new WebImageObjectInspector(this);
        this.classLoader = hUniverse.hostVM().getClassLoader();
    }

    public WebImageTypeControl typeControl() {
        return typeControl;
    }

    public WebImageObjectInspector objectInspector() {
        return objectInspector;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }
}
