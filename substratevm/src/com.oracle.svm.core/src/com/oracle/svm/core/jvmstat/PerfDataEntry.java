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
 * This interface that is implemented by all performance data entries.
 *
 * There are three variability classifications of performance data
 * <ol>
 * <li>Constants - value is written to the PerfData memory once, on creation</li>
 * <li>Variables - value is modifiable, with no particular restrictions</li>
 * <li>Counters - value is monotonically changing (increasing or decreasing)</li>
 * </ol>
 *
 * Performance data are also described by a unit of measure, see {@link PerfUnit}. Units allow
 * client applications to make reasonable decisions on how to treat performance data generically,
 * preventing the need to hard-code the specifics of a particular data item in client applications.
 */
public interface PerfDataEntry {
}
