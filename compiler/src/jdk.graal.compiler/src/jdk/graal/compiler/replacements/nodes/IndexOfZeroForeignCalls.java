/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.debug.GraalError;

public class IndexOfZeroForeignCalls {

    private static ForeignCallDescriptor foreignCallDescriptor(String name) {
        return ForeignCalls.pureFunctionForeignCallDescriptor(name, long.class, long.class);
    }

    public static final ForeignCallDescriptor STUB_INDEX_OF_ZERO_S1 = foreignCallDescriptor("indexOfZeroS1");
    public static final ForeignCallDescriptor STUB_INDEX_OF_ZERO_S2 = foreignCallDescriptor("indexOfZeroS2");
    public static final ForeignCallDescriptor STUB_INDEX_OF_ZERO_S4 = foreignCallDescriptor("indexOfZeroS4");

    public static final ForeignCallDescriptor[] STUBS = {
                    STUB_INDEX_OF_ZERO_S1,
                    STUB_INDEX_OF_ZERO_S2,
                    STUB_INDEX_OF_ZERO_S4,
    };

    public static ForeignCallDescriptor getStub(IndexOfZeroNode indexOfNode) {
        return switch (indexOfNode.getStride()) {
            case S1 -> STUB_INDEX_OF_ZERO_S1;
            case S2 -> STUB_INDEX_OF_ZERO_S2;
            case S4 -> STUB_INDEX_OF_ZERO_S4;
            default -> throw GraalError.shouldNotReachHereUnexpectedValue(indexOfNode.getStride()); // ExcludeFromJacocoGeneratedReport
        };
    }
}
