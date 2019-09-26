/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import com.oracle.truffle.api.impl.DefaultCompilerOptions;

public class GraalCompilerOptions extends DefaultCompilerOptions {

    private int minInliningMaxCallerSize = 0;

    @Override
    public boolean supportsOption(String name) {
        switch (name) {
            case "MinInliningMaxCallerSize":
                return true;
            default:
                return false;
        }
    }

    @Override
    public void setOption(String name, Object value) {
        switch (name) {
            case "MinInliningMaxCallerSize":
                minInliningMaxCallerSize = getInteger(name, value);
                break;
            default:
                super.setOption(name, value);
                break;
        }
    }

    private static int getInteger(String name, Object value) {
        if (value instanceof Integer) {
            return (int) value;
        } else {
            throw new UnsupportedOperationException(String.format("Option %s expected an int value", name));
        }
    }

    public int getMinInliningMaxCallerSize() {
        return minInliningMaxCallerSize;
    }

}
