/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test.classfile;

import static org.junit.Assume.assumeTrue;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.api.replacements.ClassSubstitution;
import com.oracle.graal.api.replacements.MethodSubstitution;
import com.oracle.graal.bytecode.BytecodeProvider;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests that snippets/intrinsics are isolated from bytecode instrumentation.
 */
public class RedefineIntrinsicTest extends GraalCompilerTest {

    public static class Foo {

        // Intrinsified by FooIntrinsic.getName
        public static String getName() {
            return "foo";
        }
    }

    @ClassSubstitution(Foo.class)
    private static class FooIntrinsic {

        @MethodSubstitution
        public static String getName() {
            return "bar";
        }
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        BytecodeProvider replacementBytecodeProvider = getReplacements().getReplacementBytecodeProvider();
        Registration r = new Registration(invocationPlugins, Foo.class, replacementBytecodeProvider);
        r.registerMethodSubstitution(FooIntrinsic.class, "getName");
        return super.editGraphBuilderConfiguration(conf);
    }

    public static String callFooGetName() {
        // This call will be intrinsified when compiled by Graal
        return Foo.getName();
    }

    public static String callFooIntrinsicGetName() {
        // This call will *not* be intrinsified when compiled by Graal
        return FooIntrinsic.getName();
    }

    @Test
    public void test() throws Throwable {
        Object receiver = null;
        Object[] args = {};

        // Prior to redefinition, both Foo and FooIntrinsic
        // should behave as per their Java source code
        Assert.assertEquals("foo", Foo.getName());
        Assert.assertEquals("bar", FooIntrinsic.getName());

        ResolvedJavaMethod callFooGetName = getResolvedJavaMethod("callFooGetName");
        ResolvedJavaMethod callFooIntrinsicGetName = getResolvedJavaMethod("callFooIntrinsicGetName");

        // Expect intrinsification to change "foo" to "bar"
        testAgainstExpected(callFooGetName, new Result("bar", null), receiver, args);

        // Expect no intrinsification
        testAgainstExpected(callFooIntrinsicGetName, new Result("bar", null), receiver, args);

        // Apply redefinition of intrinsic bytecode
        redefineFooIntrinsic();

        // Expect redefinition to have no effect
        Assert.assertEquals("foo", Foo.getName());

        // Expect redefinition to change "bar" to "BAR"
        Assert.assertEquals("BAR", FooIntrinsic.getName());

        // Expect redefinition to have no effect on intrinsification (i.e.,
        // "foo" is still changed to "bar", not "BAR"
        testAgainstExpected(callFooGetName, new Result("bar", null), receiver, args);
    }

    /**
     * Adds the class file bytes for a given class to a JAR stream.
     */
    static void add(JarOutputStream jar, Class<?> c) throws IOException {
        String name = c.getName();
        String classAsPath = name.replace('.', '/') + ".class";
        jar.putNextEntry(new JarEntry(classAsPath));

        InputStream stream = c.getClassLoader().getResourceAsStream(classAsPath);

        int nRead;
        byte[] buf = new byte[1024];
        while ((nRead = stream.read(buf, 0, buf.length)) != -1) {
            jar.write(buf, 0, nRead);
        }

        jar.closeEntry();
    }

    static void redefineFooIntrinsic() throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.putValue("Agent-Class", MyAgent.class.getName());
        mainAttrs.putValue("Can-Redefine-Classes", "true");
        mainAttrs.putValue("Can-Retransform-Classes", "true");

        Path jar = Files.createTempFile("myagent", ".jar");
        try {
            JarOutputStream jarStream = new JarOutputStream(new FileOutputStream(jar.toFile()), manifest);
            add(jarStream, MyAgent.class);
            add(jarStream, MyTransformer.class);
            jarStream.close();

            loadAgent(jar);
        } finally {
            Files.deleteIfExists(jar);
        }
    }

    public static void loadAgent(Path agent) throws Exception {
        String vmName = ManagementFactory.getRuntimeMXBean().getName();
        int p = vmName.indexOf('@');
        assumeTrue("VM name not in <pid>@<host> format: " + vmName, p != -1);
        String pid = vmName.substring(0, p);
        ClassLoader cl = ToolProvider.getSystemToolClassLoader();
        Class<?> c = Class.forName("com.sun.tools.attach.VirtualMachine", true, cl);
        Method attach = c.getDeclaredMethod("attach", String.class);
        Method loadAgent = c.getDeclaredMethod("loadAgent", String.class, String.class);
        Method detach = c.getDeclaredMethod("detach");
        Object vm = attach.invoke(null, pid);
        loadAgent.invoke(vm, agent.toString(), "");
        detach.invoke(vm);
    }

    public static class MyAgent {

        public static void agentmain(@SuppressWarnings("unused") String args, Instrumentation inst) throws Exception {
            if (inst.isRedefineClassesSupported() && inst.isRetransformClassesSupported()) {
                inst.addTransformer(new MyTransformer(), true);
                Class<?>[] allClasses = inst.getAllLoadedClasses();
                for (int i = 0; i < allClasses.length; i++) {
                    Class<?> c = allClasses[i];
                    if (c == FooIntrinsic.class) {
                        inst.retransformClasses(new Class<?>[]{c});
                    }
                }
            }
        }
    }

    /**
     * This transformer replaces the first instance of the constant "bar" in the class file for
     * {@link FooIntrinsic} with "BAR".
     */
    static class MyTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader cl, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (FooIntrinsic.class.equals(classBeingRedefined)) {
                String cf = new String(classfileBuffer);
                int i = cf.indexOf("bar");
                Assert.assertTrue("cannot find \"bar\" constant in " + FooIntrinsic.class.getSimpleName() + "'s class file", i > 0);
                classfileBuffer[i] = 'B';
                classfileBuffer[i + 1] = 'A';
                classfileBuffer[i + 2] = 'R';
            }
            return classfileBuffer;
        }
    }
}
