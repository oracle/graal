/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

/**
 * The sampler thread regularly published all mutable performance data.
 *
 * It would be more efficient if the places that update performance data could directly write into
 * the performance data memory (having a thread that publishes the data regularly doesn't scale
 * well). However, this is fairly tricky as the performance data memory is only reserved after the
 * heap was already started up. So, it could happen that the GC (especially G1) tries to write to
 * the performance data memory before it is reserved. This could probably be solved by porting the
 * code that maps the performance data memory to system Java so that the performance data memory
 * could be initialized after the image heap was mapped but before the GC is started up. Same
 * applies to the teardown. See GR-40601.
 */
public interface MutablePerfDataEntry extends PerfDataEntry {
    void publish();
}
