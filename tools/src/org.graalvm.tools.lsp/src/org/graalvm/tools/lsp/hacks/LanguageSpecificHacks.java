/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.hacks;

import java.util.Map;

import org.graalvm.tools.lsp.interop.ObjectStructures;

import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;

public final class LanguageSpecificHacks {
    public static boolean enableLanguageSpecificHacks = true;

    public static String getDocumentation(Object metaObject, String langId) {
        if (enableLanguageSpecificHacks) {
            if (metaObject != null) {
                if (langId.equals("js")) {
                    Map<Object, Object> map = ObjectStructures.asMap((TruffleObject) metaObject, new ObjectStructures.MessageNodes());
                    return "" + (map.containsKey("description") ? map.get("description") : "");
                }
            }
        }
        return null;
    }

    public static String normalizeSymbol(String definitionSearchSymbol) {
        if (enableLanguageSpecificHacks) {
            int idx = definitionSearchSymbol.indexOf('(');
            if (idx > -1) {
                return definitionSearchSymbol.substring(0, idx);
            }
        }
        return definitionSearchSymbol;
    }

    public static Class<?>[] getSupportedTags(LanguageInfo langInfo) {
        if (enableLanguageSpecificHacks) {
            if ("R".equals(langInfo.getId())) {
                // R supports no ExpressionTags in vm-1.0.0-rc7, but AnonymousBodyNode has a RootTag
                return new Class<?>[]{StandardTags.RootTag.class};
            }
        }
        return null;
    }
}
