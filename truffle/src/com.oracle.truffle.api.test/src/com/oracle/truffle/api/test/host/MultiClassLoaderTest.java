/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.host;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.host.HostExceptionTest.CatcherObject;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

public class MultiClassLoaderTest {
    private Context context;
    private Env env;

    @Before
    public void before() {
        context = Context.newBuilder().allowAllAccess(true).build();
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected LanguageContext createContext(Env contextEnv) {
                env = contextEnv;
                return super.createContext(contextEnv);
            }

            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                String req = request.getSource().getCharacters().toString();
                if (req.startsWith("get:")) {
                    String name = req.substring(4);
                    RootCallTarget reader = Truffle.getRuntime().createCallTarget(new RootNode(ProxyLanguage.getCurrentLanguage()) {
                        @Override
                        public Object execute(VirtualFrame frame) {
                            Object obj = frame.getArguments()[0];
                            try {
                                Object hash = InteropLibrary.getFactory().getUncached(obj).readMember(obj, "hash");
                                return InteropLibrary.getFactory().getUncached(hash).readMember(hash, name);
                            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    });
                    return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(new CatcherObject(reader)));
                }
                throw new IllegalArgumentException();
            }
        });
        context.initialize(ProxyLanguage.ID);
        context.enter();
        assertNotNull(env);
    }

    @After
    public void after() {
        context.leave();
        context.close();
    }

    @Test
    public void testSameFieldDifferentClassLoaders() throws Exception {
        Value read = context.eval(ProxyLanguage.ID, "get:tidal");
        for (int i = 0; i < 2; i++) {
            Object options = fromOtherLoader();
            Value result = read.execute(options);
            assertTrue(result.isString());
            assertEquals("wave", result.asString());
        }
    }

    private static Object fromOtherLoader() throws Exception {
        Class<?> thisClass = MultiClassLoaderTest.class;
        final String packagePrefix = thisClass.getName().substring(0, thisClass.getName().lastIndexOf('.') + 1);
        final URL[] urls = new URL[]{requireNonNull(thisClass.getProtectionDomain().getCodeSource().getLocation())};
        final ClassLoader parent = thisClass.getClassLoader();
        class MyClassLoader extends URLClassLoader {
            MyClassLoader() {
                super(urls, parent);
            }

            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith(packagePrefix)) {
                    return super.findClass(name);
                }
                return super.loadClass(name);
            }
        }

        ClassLoader otherLoader = new MyClassLoader();
        Class<?> clazz = otherLoader.loadClass(ExampleOptions.class.getName());
        return clazz.getConstructor().newInstance();
    }
}
