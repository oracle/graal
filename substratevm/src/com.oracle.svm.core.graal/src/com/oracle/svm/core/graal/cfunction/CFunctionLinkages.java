/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.cfunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.UnknownObjectField;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class CFunctionLinkages {

    @UnknownObjectField(types = PointerBase[].class, canBeNull = false)//
    PointerBase[] entryPoints;

    @Platforms(Platform.HOSTED_ONLY.class)//
    final ConcurrentMap<String, CFunctionLinkage> nameToFunction;

    @Platforms(Platform.HOSTED_ONLY.class)
    CFunctionLinkages() {
        nameToFunction = new ConcurrentHashMap<>();
    }

    public static CFunctionLinkages get() {
        return ImageSingletons.lookup(CFunctionLinkages.class);
    }

    public PointerBase[] getEntryPoints() {
        assert entryPoints != null;
        return entryPoints;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Collection<CFunctionLinkage> getLinkages() {
        assert entryPoints != null;
        return nameToFunction.values();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public CFunctionLinkage addOrLookupMethod(ResolvedJavaMethod method) {
        assert entryPoints == null;
        assert method.isNative();

        if (method.getAnnotation(NodeIntrinsic.class) != null || method.getAnnotation(Word.Operation.class) != null) {
            return null;
        }

        String linkageName = linkageName(method);
        CFunctionLinkage result = nameToFunction.get(linkageName);
        if (result == null) {
            CFunctionLinkage newValue = new CFunctionLinkage(linkageName);
            CFunctionLinkage oldValue = nameToFunction.putIfAbsent(linkageName, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static String linkageName(ResolvedJavaMethod method) {
        CFunction functionAnnotation = method.getAnnotation(CFunction.class);
        if (functionAnnotation != null && functionAnnotation.value().length() > 0) {
            return functionAnnotation.value();
        } else {
            return method.getName();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void finish() {
        assert entryPoints == null;
        entryPoints = new PointerBase[nameToFunction.size()];
        for (int i = 0; i < entryPoints.length; i++) {
            entryPoints[i] = WordFactory.nullPointer();
        }

        /*
         * The order does not matter, but we want the index to be stable. So we sort alphabetically.
         */
        List<CFunctionLinkage> sortedLinkages = new ArrayList<>(nameToFunction.values());
        sortedLinkages.sort((l1, l2) -> l1.getLinkageName().compareTo(l2.getLinkageName()));
        for (int i = 0; i < sortedLinkages.size(); i++) {
            sortedLinkages.get(i).index = i;
        }
    }
}

@AutomaticFeature
class CFunctionLinkagesFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CFunctionLinkages.class, new CFunctionLinkages());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        CFunctionLinkages.get().finish();
    }
}
