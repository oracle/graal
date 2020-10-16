/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.loadHub;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.Snippets;

import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.SubstrateIntrinsics.Any;
import com.oracle.svm.core.hub.DynamicHub;

public abstract class TypeSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static Any typeEqualityTestSnippet(Object object, Any trueValue, Any falseValue, @ConstantParameter boolean allowsNull, int fromTypeID) {
        if (object == null) {
            return allowsNull ? trueValue : falseValue;
        }
        Object objectNonNull = PiNode.piCastNonNull(object, SnippetAnchorNode.anchor());
        int typeCheckId = loadHub(objectNonNull).getTypeID();

        if (typeCheckId == fromTypeID) {
            return trueValue;
        }
        return falseValue;
    }

    @Snippet
    protected static Any typeEqualityTestDynamicSnippet(Object object, Any trueValue, Any falseValue, @ConstantParameter boolean allowsNull, DynamicHub exactType) {
        if (object == null) {
            return allowsNull ? trueValue : falseValue;
        }
        Object objectNonNull = PiNode.piCastNonNull(object, SnippetAnchorNode.anchor());
        int typeCheckId = loadHub(objectNonNull).getTypeID();

        if (typeCheckId == exactType.getTypeID()) {
            return trueValue;
        }
        return falseValue;
    }

    protected final RuntimeConfiguration runtimeConfig;

    protected TypeSnippets(OptionValues options, RuntimeConfiguration runtimeConfig, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection) {
        super(options, factories, providers, snippetReflection);
        this.runtimeConfig = runtimeConfig;
    }

}
