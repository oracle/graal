/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: allow reflection

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate.AbstractTemplates;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity;
import com.oracle.svm.core.util.Counter;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateTemplates extends AbstractTemplates {

    @Platforms(Platform.HOSTED_ONLY.class)
    protected SubstrateTemplates(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection) {
        super(options, factories, providers, snippetReflection, ConfigurationValues.getTarget());
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName, ResolvedJavaMethod original, Object receiver, LocationIdentity... privateLocations) {
        return snippet(declaringClass, methodName, original, receiver, (Object[]) privateLocations);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName, Object receiver, Object[] privateLocations) {
        return snippet(declaringClass, methodName, null, receiver, privateLocations);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName, Object[] privateLocations) {
        return snippet(declaringClass, methodName, null, null, privateLocations);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected SnippetInfo snippet(Class<? extends Snippets> declaringClass, String methodName, ResolvedJavaMethod original, Object receiver, Object[] privateLocations) {
        return super.snippet(declaringClass, methodName, original, receiver, toLocationIdentity(providers.getMetaAccess(), privateLocations));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static LocationIdentity[] toLocationIdentity(MetaAccessProvider metaAccess, Object[] objects) {
        List<LocationIdentity> locations = new ArrayList<>(objects.length + 1);
        for (Object object : objects) {
            addLocationIdentity(metaAccess, object, locations);
        }
        addLocationIdentity(metaAccess, Counter.VALUE_FIELD, locations);
        return locations.toArray(new LocationIdentity[locations.size()]);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void addLocationIdentity(MetaAccessProvider metaAccess, Object object, List<LocationIdentity> locations) {
        LocationIdentity location;
        if (object instanceof LocationIdentity) {
            location = (LocationIdentity) object;
        } else if (object instanceof Field) {
            location = new SubstrateFieldLocationIdentity(metaAccess.lookupJavaField((Field) object));
        } else {
            throw VMError.shouldNotReachHere("Cannot convert to LocationIdentity: " + object.getClass().getName());
        }

        if (location.isMutable()) {
            locations.add(location);
        }
    }
}
