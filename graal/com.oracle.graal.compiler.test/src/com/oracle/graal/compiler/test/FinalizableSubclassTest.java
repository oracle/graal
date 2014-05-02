/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Assumptions.Assumption;
import com.oracle.graal.api.code.Assumptions.NoFinalizableSubclass;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;

public class FinalizableSubclassTest extends GraalCompilerTest {

    /**
     * used as template to generate class files at runtime.
     */
    public static class NoFinalizerEverAAAA {
    }

    public static class NoFinalizerYetAAAA {
    }

    public static class WithFinalizerAAAA extends NoFinalizerYetAAAA {

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
        }
    }

    private StructuredGraph parseAndProcess(Class<?> cl, Assumptions assumptions) {
        Constructor<?>[] constructors = cl.getConstructors();
        Assert.assertTrue(constructors.length == 1);
        final ResolvedJavaMethod javaMethod = getMetaAccess().lookupJavaConstructor(constructors[0]);
        StructuredGraph graph = new StructuredGraph(javaMethod);

        GraphBuilderConfiguration conf = GraphBuilderConfiguration.getSnippetDefault();
        new GraphBuilderPhase.Instance(getMetaAccess(), conf, OptimisticOptimizations.ALL).apply(graph);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, context);
        return graph;
    }

    private void checkForRegisterFinalizeNode(Class<?> cl, boolean shouldContainFinalizer, boolean optimistic) {
        Assumptions assumptions = new Assumptions(optimistic);
        StructuredGraph graph = parseAndProcess(cl, assumptions);
        Assert.assertTrue(graph.getNodes().filter(RegisterFinalizerNode.class).count() == (shouldContainFinalizer ? 1 : 0));
        int noFinalizerAssumption = 0;
        for (Assumption a : assumptions) {
            if (a instanceof NoFinalizableSubclass) {
                noFinalizerAssumption++;
            }
        }
        Assert.assertTrue(noFinalizerAssumption == (shouldContainFinalizer ? 0 : 1));
    }

    /**
     * Use a custom class loader to generate classes, to make sure the given classes are loaded in
     * correct order.
     */
    @Test
    public void test1() throws ClassNotFoundException {
        for (int i = 0; i < 2; i++) {
            ClassTemplateLoader loader = new ClassTemplateLoader();
            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerEverAAAA"), true, false);
            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerEverAAAA"), false, true);

            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerYetAAAA"), false, true);

            checkForRegisterFinalizeNode(loader.findClass("WithFinalizerAAAA"), true, true);
            checkForRegisterFinalizeNode(loader.findClass("NoFinalizerYetAAAA"), true, true);
        }
    }

    private static class ClassTemplateLoader extends ClassLoader {

        private static int loaderInstance = 0;

        private final String replaceTo;
        private HashMap<String, Class<?>> cache = new HashMap<>();

        public ClassTemplateLoader() {
            loaderInstance++;
            replaceTo = String.format("%04d", loaderInstance);
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            String nameReplaced = name.replaceAll("AAAA", replaceTo);
            if (cache.containsKey(nameReplaced)) {
                return cache.get(nameReplaced);
            }

            // copy classfile to byte array
            byte[] classData = null;
            try {
                InputStream is = FinalizableSubclassTest.class.getResourceAsStream("FinalizableSubclassTest$" + name + ".class");
                assert is != null;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                byte[] buf = new byte[1024];
                int size;
                while ((size = is.read(buf, 0, buf.length)) != -1) {
                    baos.write(buf, 0, size);
                }
                baos.flush();
                classData = baos.toByteArray();
            } catch (IOException e) {
                Assert.fail("can't access class: " + name);
            }
            dumpStringsInByteArray(classData);

            // replace all occurrences of "AAAA" in classfile
            int index = -1;
            while ((index = indexOfAAAA(classData, index + 1)) != -1) {
                replaceAAAA(classData, index, replaceTo);
            }
            dumpStringsInByteArray(classData);

            Class<?> c = defineClass(null, classData, 0, classData.length);
            cache.put(nameReplaced, c);
            return c;
        }

        private static int indexOfAAAA(byte[] b, int index) {
            for (int i = index; i < b.length; i++) {
                boolean match = true;
                for (int j = i; j < i + 4; j++) {
                    if (b[j] != (byte) 'A') {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return i;
                }
            }
            return -1;
        }

        private static void replaceAAAA(byte[] b, int index, String replacer) {
            assert replacer.length() == 4;
            for (int i = index; i < index + 4; i++) {
                b[i] = (byte) replacer.charAt(i - index);
            }
        }

        private static void dumpStringsInByteArray(byte[] b) {
            boolean wasChar = true;
            StringBuilder sb = new StringBuilder();
            for (Byte x : b) {
                // check for [a-zA-Z0-9]
                if ((x >= 0x41 && x <= 0x7a) || (x >= 0x30 && x <= 0x39)) {
                    if (!wasChar) {
                        Debug.log(sb + "");
                        sb.setLength(0);
                    }
                    sb.append(String.format("%c", x));
                    wasChar = true;
                } else {
                    wasChar = false;
                }
            }
            Debug.log(sb + "");
        }
    }
}
