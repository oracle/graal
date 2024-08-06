/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.modularized.test;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.modularized.test.adapter.Extensible;
import com.oracle.truffle.api.modularized.test.callbacks.NonExportedClass;
import com.oracle.truffle.api.modularized.test.separate.module.test.ExportedToTestModuleClass;
import com.oracle.truffle.api.nodes.RootNode;

public class HostAccessFromModuleTest {
    static final String TEST_REPLACE_CLASS_NAME = "HostClassLoadingTestClazz1";
    private static final String TEST_REPLACE_QUALIFIED_CLASS_NAME = HostClassLoadingTestClass1.class.getPackage().getName() + ".HostClassLoadingTestClazz1";

    @Test
    public void testInAModule() {
        Assume.assumeTrue(HostAccessFromModuleTest.class.getModule().isNamed());

        try (Context context = Context.newBuilder().allowHostClassLookup((c) -> true).allowHostClassLoading(true).allowHostAccess(
                        HostAccess.newBuilder(HostAccess.ALL).useModuleLookup(MethodHandles.lookup()).build()).build()) {
            accessHostObject(context, true);
            accessHostObject(context, true, true);
            accessHostObject(context, false);
        }
        try (Context context = Context.newBuilder().allowHostClassLookup((c) -> true).allowHostClassLoading(true).allowHostAccess(
                        HostAccess.ALL).build()) {
            accessHostObject(context, true);
            try {
                accessHostObject(context, true, true);
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!"Undefined property: storeMessage".equals(pe.getMessage())) {
                    throw pe;
                }
            }
            try {
                accessHostObject(context, false);
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!"Undefined property: storeMessage".equals(pe.getMessage())) {
                    throw pe;
                }
            }
        }
    }

    private static void accessHostObject(Context context, boolean exported) {
        accessHostObject(context, exported, false);
    }

    private static void accessHostObject(Context context, boolean exported, boolean exportredToThisModuleOnly) {
        Object hostObject = exported ? exportredToThisModuleOnly ? new ExportedToTestModuleClass() : new ExportedClass() : new NonExportedClass();
        context.eval("sl", """
                        function createNewObject() {
                          return new();
                        }

                        function storeMessage(obj, s) {
                          obj.printer.storeMessage(s);
                        }

                        function getCreationTime(obj) {
                          return obj.printer.creationTime;
                        }

                        function getLoadTime(className) {
                          return java(className).loadTime;
                        }
                        """);
        Value bindings = context.getBindings("sl");
        Value obj = bindings.getMember("createNewObject").execute();
        obj.putMember("printer", hostObject);
        bindings.getMember("storeMessage").execute(obj, "Hello world!");
        Assert.assertEquals("Hello world!",
                        exported ? exportredToThisModuleOnly ? ((ExportedToTestModuleClass) hostObject).message : ((ExportedClass) hostObject).message : ((NonExportedClass) hostObject).message);
        Assert.assertEquals(exported ? exportredToThisModuleOnly ? ((ExportedToTestModuleClass) hostObject).creationTime : ((ExportedClass) hostObject).creationTime
                        : ((NonExportedClass) hostObject).creationTime, bindings.getMember("getCreationTime").execute(obj).asString());
        Assert.assertEquals(exported ? exportredToThisModuleOnly ? ExportedToTestModuleClass.loadTime : ExportedClass.loadTime : NonExportedClass.loadTime, bindings.getMember("getLoadTime").execute(
                        exported ? exportredToThisModuleOnly ? ExportedToTestModuleClass.class.getName() : ExportedClass.class.getName() : NonExportedClass.class.getName()).asString());
    }

    @Registration(id = HostClassLoadingTestLanguage.ID)
    static class HostClassLoadingTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "hcltlang";
        static final ContextReference<Env> REFERENCE = ContextReference.create(HostClassLoadingTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    boundary((String) frame.getArguments()[0]);
                    return 42;
                }

                @TruffleBoundary
                private void boundary(String directory) {
                    Env env = REFERENCE.get(this);
                    env.addToHostClassPath(env.getPublicTruffleFile(directory));
                    Object symbol = env.lookupHostSymbol(TEST_REPLACE_QUALIFIED_CLASS_NAME);
                    assertHostSymbol(symbol, 42);
                }
            }.getCallTarget();
        }
    }

    @Test
    public void testClassLoading() throws IOException {
        Assume.assumeTrue(HostAccessFromModuleTest.class.getModule().isNamed());

        Path tempDir;
        tempDir = setupSimpleClassPath();

        try {
            try (Context context = Context.newBuilder().allowHostAccess(HostAccess.newBuilder(HostAccess.ALL).useModuleLookup(MethodHandles.lookup()).build()).allowHostClassLookup(
                            (c) -> true).allowHostClassLoading(true).allowIO(IOAccess.ALL).build()) {
                Value mainFunc = context.parse(HostClassLoadingTestLanguage.ID, "");
                mainFunc.execute(tempDir.toString());
            }

            try (Context context = Context.newBuilder().allowHostAccess(HostAccess.newBuilder(HostAccess.ALL).build()).allowHostClassLookup((c) -> true).allowHostClassLoading(true).allowIO(
                            IOAccess.ALL).build()) {
                Value mainFunc = context.parse(HostClassLoadingTestLanguage.ID, "");
                mainFunc.execute(tempDir.toString());
            }
        } finally {
            deleteDir(tempDir);
        }

    }

    @Registration(id = HostAdapterTestLanguage.ID)
    static class HostAdapterTestLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "hatlang";
        static final ContextReference<Env> REFERENCE = ContextReference.create(HostAdapterTestLanguage.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    boundary(frame.getArguments()[0], frame.getArguments()[1]);
                    return 42;
                }

                @TruffleBoundary
                private void boundary(Object hostType, Object impl) {
                    Env env = REFERENCE.get(this);
                    Object adapter = env.createHostAdapter(new Object[]{hostType});
                    try {
                        Object instance = InteropLibrary.getUncached().instantiate(adapter, impl);
                        assertEquals("override", InteropLibrary.getUncached().invokeMember(instance, "abstractMethod"));
                        assertEquals("base", InteropLibrary.getUncached().invokeMember(instance, "baseMethod"));
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException | UnknownIdentifierException e) {
                        throw new AssertionError(e);
                    }
                }
            }.getCallTarget();
        }
    }

    @Test
    public void testCreateHostAdapterFromClass() {
        Assume.assumeTrue(HostAccessFromModuleTest.class.getModule().isNamed());
        try (Context context = Context.newBuilder().allowHostAccess(
                        HostAccess.newBuilder(HostAccess.ALL).allowAllImplementations(true).allowAllClassImplementations(true).build()).allowHostClassLookup(
                                        (c) -> true).allowHostClassLoading(true).build()) {
            Value mainFunc = context.parse(HostAdapterTestLanguage.ID, "");
            mainFunc.execute(ExtensibleExported.class, ProxyObject.fromMap(Collections.singletonMap("abstractMethod", (ProxyExecutable) (args) -> "override")));
            try {
                mainFunc.execute(Extensible.class, ProxyObject.fromMap(Collections.singletonMap("abstractMethod", (ProxyExecutable) (args) -> "override")));
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!pe.getMessage().contains("superclass access check failed")) {
                    throw pe;
                }
            }
        }
        try (Context context = Context.newBuilder().allowHostAccess(
                        HostAccess.newBuilder(HostAccess.ALL).allowAllImplementations(true).allowAllClassImplementations(true).useModuleLookup(MethodHandles.lookup()).build()).allowHostClassLookup(
                                        (c) -> true).allowHostClassLoading(true).build()) {
            Value mainFunc = context.parse(HostAdapterTestLanguage.ID, "");
            mainFunc.execute(ExtensibleExported.class, ProxyObject.fromMap(Collections.singletonMap("abstractMethod", (ProxyExecutable) (args) -> "override")));
            try {
                mainFunc.execute(Extensible.class, ProxyObject.fromMap(Collections.singletonMap("abstractMethod", (ProxyExecutable) (args) -> "override")));
                Assert.fail();
            } catch (PolyglotException pe) {
                if (!pe.getMessage().contains("superclass access check failed")) {
                    throw pe;
                }
            }
        }
    }

    private static void assertHostSymbol(Object hostSymbol, int incrementValue) {
        assertEquals(incrementValue, read(hostSymbol, "staticField"));
        write(hostSymbol, "staticField", incrementValue + 1);
        assertEquals(incrementValue + 1, read(hostSymbol, "staticField"));

        Object hostInstance = newInstance(hostSymbol);
        assertEquals(42, read(hostInstance, "testField"));
        assertEquals(42, execute(read(hostInstance, "testMethod")));
    }

    private static Object execute(Object o, Object... args) {
        try {
            return InteropLibrary.getFactory().getUncached().execute(o, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Object read(Object o, String key) {
        try {
            return InteropLibrary.getFactory().getUncached().readMember(o, key);
        } catch (UnknownIdentifierException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static void write(Object o, String key, Object value) {
        try {
            InteropLibrary.getFactory().getUncached().writeMember(o, key, value);
        } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
            throw new AssertionError(e);
        }
    }

    private static Object newInstance(Object o, Object... args) {
        try {
            return InteropLibrary.getFactory().getUncached().instantiate(o, args);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    private static Path setupSimpleClassPath() throws IOException {
        final Class<?> hostClass = HostClassLoadingTestClass1.class;
        Path tempDir = renameHostClass(hostClass, TEST_REPLACE_CLASS_NAME);
        return tempDir;
    }

    static Path renameHostClass(final Class<?> hostClass, String newName) throws IOException {
        // create a temporary folder with the package directory structure
        String oldName = hostClass.getSimpleName();
        Path packagePath = Paths.get(hostClass.getPackage().getName().replace('.', '/'));
        Path classFilePath = packagePath.resolve(oldName + ".class");
        URL classFileLocation = hostClass.getResource("/" + pathToInternalName(classFilePath));

        Path tempDir = Files.createTempDirectory("testHostClassLoading");
        Path targetDir = tempDir.resolve(packagePath);
        Files.createDirectories(targetDir);

        // replace class file name in class file bytes
        byte[] bytes = read(classFileLocation);
        byte[] searchBytes = pathToInternalName(packagePath.resolve(oldName)).getBytes(StandardCharsets.UTF_8);
        byte[] newBytes = pathToInternalName(packagePath.resolve(newName)).getBytes(StandardCharsets.UTF_8);

        // need to encode to the same number of bytes. otherwise more difficult to rename
        assert newBytes.length == searchBytes.length;

        for (int index = 0; (index = indexOfByteArray(bytes, searchBytes)) != -1;) {
            for (int i = 0; i < newBytes.length; i++) {
                bytes[i + index] = newBytes[i];
            }
        }

        // create the new class name with the new name
        Files.copy(new ByteArrayInputStream(bytes), targetDir.resolve(newName + ".class"));
        return tempDir;
    }

    private static String pathToInternalName(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

    private static byte[] read(URL file) throws IOException {
        try (InputStream stream = file.openStream()) {
            return stream.readAllBytes();
        }
    }

    private static int indexOfByteArray(byte[] outerArray, byte[] smallerArray) {
        for (int i = 0; i < outerArray.length - smallerArray.length + 1; ++i) {
            boolean found = true;
            for (int j = 0; j < smallerArray.length; ++j) {
                if (outerArray[i + j] != smallerArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    static void deleteDir(Path p) throws IOException {
        Files.walk(p).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
}
