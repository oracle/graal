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
package com.oracle.svm.hosted;

import java.util.Objects;

import com.oracle.graal.pointsto.heap.HostedValuesProvider;
import com.oracle.svm.util.HeapObjectConstantReplacer;
import com.oracle.svm.util.HeapObjectReplacer;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Adapts a heap object replacer to the constant-based analysis pipeline.
 * Temporary solution until GR-78997: Migrate builder-side object replacers to JavaConstants or guest features
 */
final class HeapObjectReplacerAdapter extends HeapObjectConstantReplacer {
    private final HeapObjectReplacer replacer;
    private final HostedValuesProvider hostedValuesProvider;

    HeapObjectReplacerAdapter(HeapObjectReplacer replacer, HostedValuesProvider hostedValuesProvider) {
        this.replacer = Objects.requireNonNull(replacer);
        this.hostedValuesProvider = Objects.requireNonNull(hostedValuesProvider);
    }

    @Override
    public JavaConstant replaceConstant(JavaConstant constant, boolean reachable) {
        Object object = hostedValuesProvider.asObject(Object.class, constant);
        Object replacement = replacer.replaceObject(object, reachable);
        if (replacement == HeapObjectReplacer.CANNOT_REPLACE_MARKER) {
            return CANNOT_REPLACE_MARKER;
        }
        return hostedValuesProvider.forObjectReplacerResult(replacement);
    }
}
