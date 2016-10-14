/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class PathOptionValue extends OptionValue<String> {

    static final long timeStamp = System.currentTimeMillis();

    private final AtomicInteger dumpId = new AtomicInteger();

    private final OptionValue<String> defaultPath;

    private final String extension;

    public PathOptionValue(String value, String extension, OptionValue<String> defaultPath) {
        super(value);
        this.defaultPath = defaultPath;
        this.extension = extension;
    }

    public PathOptionValue(String value, OptionValue<String> defaultPath) {
        this(value, null, defaultPath);
    }

    protected String getExtension() {
        return extension;
    }

    /**
     *
     * @return the output file path
     */
    public Path getPath() {
        String name = getValue() + "-" + timeStamp + "_" + dumpId.incrementAndGet() + getExtension();
        Path result = Paths.get(name);
        if (result.isAbsolute() || defaultPath == null) {
            return result;
        }
        return Paths.get(defaultPath.getValue(), name);
    }

}
