/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import java.util.Collections;
import java.util.HashSet;

abstract class CVRootPackages {
    private static final String[] rootPackageNames = {
            /* substrate root packages */
            "com.oracle.graal.pointsto",
            "com.oracle.objectfile",
            "com.oracle.svm.agent",
            "com.oracle.svm.configure",
            "com.oracle.svm.core",
            "com.oracle.svm.core.genscavenge",
            "com.oracle.svm.core.graal",
            "com.oracle.svm.core.graal.aarch64",
            "com.oracle.svm.core.graal.amd64",
            "com.oracle.svm.core.graal.llvm",
            "com.oracle.svm.core.jdk11",
            "com.oracle.svm.core.jdk8",
            "com.oracle.svm.core.posix",
            "com.oracle.svm.core.posix.jdk11",
            "com.oracle.svm.core.windows",
            "com.oracle.svm.driver",
            "com.oracle.svm.graal",
            "com.oracle.svm.graal.hotspot.libgraal",
            "com.oracle.svm.hosted",
            "com.oracle.svm.jline",
            "com.oracle.svm.jni",
            "com.oracle.svm.junit",
            "com.oracle.svm.libffi",
            "com.oracle.svm.native.jvm.posix",
            "com.oracle.svm.native.jvm.windows",
            "com.oracle.svm.native.libchelper",
            "com.oracle.svm.native.strictmath",
            "com.oracle.svm.polyglot",
            "com.oracle.svm.reflect",
            "com.oracle.svm.test",
            "com.oracle.svm.test.jdk11",
            "com.oracle.svm.thirdparty",
            "com.oracle.svm.truffle",
            "com.oracle.svm.truffle.nfi",
            "com.oracle.svm.truffle.nfi.posix",
            "com.oracle.svm.truffle.nfi.windows",
            "com.oracle.svm.tutorial",
            "com.oracle.svm.util",
            "com.oracle.svm.util.jdk11",
            "org.graalvm.polyglot.nativeapi",
            /* compiler root packages */
            "jdk.tools.jaotc",
            "jdk.tools.jaotc.binformat",
            "jdk.tools.jaotc",
            "org.graalvm.compiler.api.directives",
            "org.graalvm.compiler.api.replacements",
            "org.graalvm.compiler.api.runtime",
            "org.graalvm.compiler.asm",
            "org.graalvm.compiler.asm.aarch64",
            "org.graalvm.compiler.asm.amd64",
            "org.graalvm.compiler.asm.sparc",
            "org.graalvm.compiler.bytecode",
            "org.graalvm.compiler.code",
            "org.graalvm.compiler.core",
            "org.graalvm.compiler.core.aarch64",
            "org.graalvm.compiler.core.amd64",
            "org.graalvm.compiler.core.common",
            "org.graalvm.compiler.core.llvm",
            "org.graalvm.compiler.core.match.processor",
            "org.graalvm.compiler.core.sparc",
            "org.graalvm.compiler.debug",
            "org.graalvm.compiler.graph",
            "org.graalvm.compiler.hotspot",
            "org.graalvm.compiler.hotspot.aarch64",
            "org.graalvm.compiler.hotspot.amd64",
            "org.graalvm.compiler.hotspot.jdk8",
            "org.graalvm.compiler.hotspot.management",
            "org.graalvm.compiler.hotspot.sparc",
            "org.graalvm.compiler.java",
            "org.graalvm.compiler.jtt",
            "org.graalvm.compiler.lir",
            "org.graalvm.compiler.lir.aarch64",
            "org.graalvm.compiler.lir.amd64",
            "org.graalvm.compiler.lir.jtt",
            "org.graalvm.compiler.lir.sparc",
            "org.graalvm.compiler.loop",
            "org.graalvm.compiler.loop.phases",
            "org.graalvm.compiler.microbenchmarks",
            "org.graalvm.compiler.nodeinfo",
            "org.graalvm.compiler.nodeinfo.processor",
            "org.graalvm.compiler.nodes",
            "org.graalvm.compiler.options",
            "org.graalvm.compiler.options.processor",
            "org.graalvm.compiler.phases",
            "org.graalvm.compiler.phases.common",
            "org.graalvm.compiler.printer",
            "org.graalvm.compiler.processor",
            "org.graalvm.compiler.replacements",
            "org.graalvm.compiler.replacements.aarch64",
            "org.graalvm.compiler.replacements.amd64",
            "org.graalvm.compiler.replacements.processor",
            "org.graalvm.compiler.replacements.sparc",
            "org.graalvm.compiler.runtime",
            "org.graalvm.compiler.serviceprovider",
            "org.graalvm.compiler.serviceprovider.jdk8",
            "org.graalvm.compiler.serviceprovider.processor",
            "org.graalvm.compiler.truffle.common",
            "org.graalvm.compiler.truffle.common.hotspot",
            "org.graalvm.compiler.truffle.common.hotspot.libgraal",
            "org.graalvm.compiler.truffle.common.processor",
            "org.graalvm.compiler.truffle.compiler",
            "org.graalvm.compiler.truffle.compiler.amd64",
            "org.graalvm.compiler.truffle.compiler.hotspot",
            "org.graalvm.compiler.truffle.compiler.hotspot.aarch64",
            "org.graalvm.compiler.truffle.compiler.hotspot.amd64",
            "org.graalvm.compiler.truffle.compiler.hotspot.libgraal",
            "org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor",
            "org.graalvm.compiler.truffle.compiler.hotspot.sparc",
            "org.graalvm.compiler.truffle.runtime",
            "org.graalvm.compiler.truffle.runtime.hotspot",
            "org.graalvm.compiler.truffle.runtime.hotspot.java",
            "org.graalvm.compiler.truffle.runtime.hotspot.jdk8+13",
            "org.graalvm.compiler.truffle.runtime.hotspot.libgraal",
            "org.graalvm.compiler.truffle.runtime.serviceprovider",
            "org.graalvm.compiler.truffle.runtime.serviceprovider.jdk8",
            "org.graalvm.compiler.virtual",
            "org.graalvm.compiler.virtual.bench",
            "org.graalvm.compiler.word",
            "org.graalvm.graphio",
            "org.graalvm.libgraal",
            "org.graalvm.libgraal.jdk8",
            "org.graalvm.micro.benchmarks",
            "org.graalvm.util",
    };

    private static final String[] intrinsicClassNames = {
            "com.oracle.svm.core.genscavenge.AlignedHeapChunk",
            "com.oracle.svm.core.genscavenge.CardTable",
            "com.oracle.svm.core.genscavenge.ObjectHeaderImpl",
            "com.oracle.svm.core.genscavenge.graal.GenScavengeAllocationSnippets",
            "com.oracle.svm.core.genscavenge.graal.BarrierSnippets",
            "com.oracle.svm.core.snippets.KnownIntrinsics",
            "com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets",
            "com.oracle.svm.core.threadlocal.FastThreadLocalBytes",
            "org.graalvm.compiler.replacements.AllocationSnippets",
            "org.graalvm.compiler.nodes.PrefetchAllocateNode",
            "com.oracle.svm.core.os.CopyingImageHeapProvider"
    };

    private static final HashSet<String> rootPackageSet;
    private static final HashSet<String> intrinsicClassNameSet;

    static {
        rootPackageSet = new HashSet<>(rootPackageNames.length);
        Collections.addAll(rootPackageSet, rootPackageNames);
        intrinsicClassNameSet = new HashSet<>(intrinsicClassNames.length);
        Collections.addAll(intrinsicClassNameSet, intrinsicClassNames);
    }

    static boolean isGraalPackage(String pn) {
        return rootPackageSet.contains(pn);
    }

    private static String getPackagename(String className) {
        return className.contains(".") ? className.substring(0, className.lastIndexOf('.')) : className;
    }

    static boolean isGraalClass(String cn) {
        final String pn = getPackagename(cn);
        return isGraalPackage(pn);
    }

    /**
     * is class a Graal intrinsic class?
     *
     * @param cn class name of code
     * @return true if this is Graal intrinsic code
     */
    static boolean isGraalIntrinsic(String cn) {
        return intrinsicClassNameSet.contains(cn);
    }

    static boolean isJavaPackage(String pn) {
        return pn.startsWith("java.") || pn.startsWith("javax.") || pn.startsWith("sun.");
    }
/*
    static boolean isJavaFile(String pn) {
        return pn.startsWith("java/") || pn.startsWith("javax/") || pn.startsWith("sun/");
    }*/
}
