/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.hotspot.word.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.type.*;

public final class HotSpotLoadIndexedPlugin implements LoadIndexedPlugin {
    private final HotSpotWordTypes wordTypes;

    public HotSpotLoadIndexedPlugin(HotSpotWordTypes wordTypes) {
        this.wordTypes = wordTypes;
    }

    public boolean apply(GraphBuilderContext b, ValueNode array, ValueNode index, Kind elementKind) {
        if (b.parsingReplacement()) {
            ResolvedJavaType arrayType = StampTool.typeOrNull(array);
            /*
             * There are cases where the array does not have a known type yet, i.e., the type is
             * null. In that case we assume it is not a word type.
             */
            if (arrayType != null && wordTypes.isWord(arrayType.getComponentType()) && elementKind != wordTypes.getWordKind()) {
                /*
                 * The elementKind of the node is a final field, and other information such as the
                 * stamp depends on elementKind. Therefore, just create a new node and replace the
                 * old one.
                 */
                Stamp componentStamp = wordTypes.getWordStamp(arrayType.getComponentType());
                if (componentStamp instanceof MetaspacePointerStamp) {
                    b.push(elementKind, b.append(new LoadIndexedPointerNode(componentStamp, array, index)));
                } else {
                    b.push(elementKind, b.append(new LoadIndexedNode(array, index, wordTypes.getWordKind())));
                }
                return true;
            }
        }
        return false;
    }
}
