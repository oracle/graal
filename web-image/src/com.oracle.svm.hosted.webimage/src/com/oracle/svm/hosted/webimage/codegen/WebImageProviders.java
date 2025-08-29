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

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.Labeler;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;

public abstract class WebImageProviders extends CoreProvidersDelegate {
    private final PrintStream out;
    private final DebugContext debug;
    private final Labeler labeler;

    @SuppressWarnings("this-escape")
    protected WebImageProviders(CoreProviders underlyingProviders, PrintStream out, DebugContext debug) {
        super(underlyingProviders);
        this.out = out;
        this.debug = debug;
        this.labeler = getLabelInjector();
    }

    public PrintStream stdout() {
        return out;
    }

    public DebugContext debug() {
        return debug;
    }

    public Labeler labeler() {
        return labeler;
    }

    private static Labeler getLabelInjector() {
        if (isLabelInjectionEnabled()) {
            return new Labeler();
        } else {
            return new Labeler.NoOpLabeler();
        }
    }

    public static boolean isLabelInjectionEnabled() {
        return WebImageOptions.ClosureCompiler.getValue() && WebImageOptions.ReportImageSizeBreakdown.getValue();
    }

    /**
     * Find a method on a hosted type by name.
     *
     * If there are multiple method of the same name, it will return the first one.
     */
    public static HostedMethod findMethod(HostedType type, String name) {
        for (HostedMethod meth : type.getAllDeclaredMethods()) {
            if (meth.getName().equals(name)) {
                return meth;
            }
        }
        throw VMError.shouldNotReachHere("method not found " + name);
    }
}
