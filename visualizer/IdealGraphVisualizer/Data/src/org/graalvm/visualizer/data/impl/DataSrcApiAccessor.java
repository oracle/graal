/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.data.impl;

import org.graalvm.visualizer.data.src.LocationStackFrame;
import org.graalvm.visualizer.data.src.LocationStratum;
import java.util.Collections;
import java.util.List;
import org.graalvm.visualizer.data.serialization.BinaryReader;

/**
 *
 * @author sdedic
 */
public abstract class DataSrcApiAccessor {
    private static volatile DataSrcApiAccessor INSTANCE;
    
    protected DataSrcApiAccessor() {
        setAccessor(this);
    }
    
    public static DataSrcApiAccessor getInstance() {
        return INSTANCE;
    }
    
    public final List<LocationStratum> fileLineStratum(String fileName, int line) {
        return Collections.nCopies(1, createStratum(null, fileName, "Java", line, -1, -1));
    }
    public abstract LocationStratum createStratum(String uri, String file, String language, int line, int startOffset, int endOffset);
    public abstract LocationStackFrame createFrame(BinaryReader.Method method, int bci, List<LocationStratum> strata, LocationStackFrame parent);
    
    public abstract BinaryReader.Method getMethod(LocationStackFrame frame);
    
    private static void setAccessor(DataSrcApiAccessor accessor) {
        if (INSTANCE != null) {
            throw new IllegalStateException();
        }
        INSTANCE = accessor;
    }
}
