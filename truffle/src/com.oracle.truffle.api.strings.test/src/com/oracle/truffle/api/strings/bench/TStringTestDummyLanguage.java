/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.strings.bench;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;

@TruffleLanguage.Registration(name = TStringTestDummyLanguage.NAME, id = TStringTestDummyLanguage.ID, characterMimeTypes = TStringTestDummyLanguage.MIME_TYPE, version = "0.1", needsAllEncodings = true)
public class TStringTestDummyLanguage extends TruffleLanguage<TStringTestDummyLanguage.DummyLanguageContext> {

    public static final String NAME = "TRUFFLE_STRING_DUMMY_LANG";
    public static final String ID = "tStringDummyLang";
    public static final String MIME_TYPE = "application/tstringdummy";

    @Override
    protected CallTarget parse(ParsingRequest parsingRequest) {
        return getRootNode(parsingRequest.getSource().getCharacters().toString()).getCallTarget();
    }

    private RootNode getRootNode(String benchmarkName) {
        switch (benchmarkName) {
            case "rawIterateBytes":
                return new RootNode(this) {

                    @Child RawIterateBytesNode benchNode = TStringTestDummyLanguageFactory.RawIterateBytesNodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return benchNode.execute(frame.getArguments()[0]);
                    }
                };
            case "rawIterateTStringBytes":
                return new RootNode(this) {

                    @Child RawIterateTStringBytesNode benchNode = TStringTestDummyLanguageFactory.RawIterateTStringBytesNodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return benchNode.execute(frame.getArguments()[0]);
                    }
                };
            case "rawIterateTStringChars":
                return new RootNode(this) {

                    @Child RawIterateTStringCharsNode benchNode = TStringTestDummyLanguageFactory.RawIterateTStringCharsNodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return benchNode.execute(frame.getArguments()[0]);
                    }
                };
            case "compareTString":
                return new RootNode(this) {

                    @Child CompareTStringNode benchNode = TStringTestDummyLanguageFactory.CompareTStringNodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] args = frame.getArguments();
                        return benchNode.execute(args[0], args[1]);
                    }
                };
            case "byteIndexOfAnyByteTString":
                return new RootNode(this) {

                    @Child ByteIndexOfAnyByteTStringNode benchNode = TStringTestDummyLanguageFactory.ByteIndexOfAnyByteTStringNodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] args = frame.getArguments();
                        return benchNode.execute(args[0], args[1]);
                    }
                };
            case "calcStringAttributesUTF8":
                return new RootNode(this) {

                    @Child CalcStringAttributesUTF8Node benchNode = TStringTestDummyLanguageFactory.CalcStringAttributesUTF8NodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] args = frame.getArguments();
                        return benchNode.execute(args[0], (int) args[1]);
                    }
                };
            case "calcStringAttributesUTF16":
                return new RootNode(this) {

                    @Child CalcStringAttributesUTF16Node benchNode = TStringTestDummyLanguageFactory.CalcStringAttributesUTF16NodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] args = frame.getArguments();
                        return benchNode.execute(args[0], (int) args[1]);
                    }
                };
            case "fromByteArrayUTF16":
                return new RootNode(this) {

                    @Child FromByteArrayUTF16Node benchNode = TStringTestDummyLanguageFactory.FromByteArrayUTF16NodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] args = frame.getArguments();
                        return benchNode.execute(args[0], (int) args[1]);
                    }
                };
            case "fromByteArrayUTF32":
                return new RootNode(this) {

                    @Child FromByteArrayUTF32Node benchNode = TStringTestDummyLanguageFactory.FromByteArrayUTF32NodeGen.create();

                    @Override
                    public Object execute(VirtualFrame frame) {
                        Object[] args = frame.getArguments();
                        return benchNode.execute(args[0], (int) args[1]);
                    }
                };
            default:
                throw new UnsupportedOperationException();
        }
    }

    abstract static class RawIterateBytesNode extends Node {

        public abstract int execute(Object input);

        @Specialization
        int bench(Object hostObject) {
            byte[] input = (byte[]) DummyLanguageContext.get(this).getEnv().asHostObject(hostObject);
            int ret = 0;
            for (int i = 0; i < input.length; i++) {
                ret += Byte.toUnsignedInt(input[i]);
            }
            return ret;
        }
    }

    abstract static class RawIterateTStringBytesNode extends Node {

        abstract int execute(Object input);

        @Specialization
        int bench(TruffleString input,
                        @Cached TruffleString.MaterializeNode materializeNode,
                        @Cached TruffleString.ReadByteNode readRawNode) {
            materializeNode.execute(input, TruffleString.Encoding.UTF_8);
            int ret = 0;
            for (int i = 0; i < input.byteLength(TruffleString.Encoding.UTF_8); i++) {
                ret += readRawNode.execute(input, i, TruffleString.Encoding.UTF_8);
            }
            return ret;
        }
    }

    abstract static class RawIterateTStringCharsNode extends Node {

        abstract int execute(Object input);

        @Specialization
        int bench(TruffleString input,
                        @Cached TruffleString.MaterializeNode materializeNode,
                        @Cached TruffleString.ReadCharUTF16Node readRawNode) {
            materializeNode.execute(input, TruffleString.Encoding.UTF_16);
            int ret = 0;
            for (int i = 0; i < input.byteLength(TruffleString.Encoding.UTF_16) >> 1; i++) {
                ret += readRawNode.execute(input, i);
            }
            return ret;
        }
    }

    abstract static class CompareTStringNode extends Node {

        abstract int execute(Object a, Object b);

        @Specialization
        int bench(TruffleString a, TruffleString b,
                        @Cached TruffleString.CompareCharsUTF16Node compareNode) {
            return compareNode.execute(a, b);
        }

        @Specialization
        int bench(String a, String b) {
            return a.compareTo(b);
        }
    }

    abstract static class ByteIndexOfAnyByteTStringNode extends Node {

        abstract int execute(Object a, Object b);

        @Specialization
        int bench(TruffleString a, byte b,
                        @Cached TruffleString.ByteIndexOfAnyByteNode compareNode) {
            return compareNode.execute(a, 0, a.byteLength(TruffleString.Encoding.UTF_8), new byte[]{b}, TruffleString.Encoding.UTF_8);
        }
    }

    abstract static class CalcStringAttributesUTF8Node extends Node {

        abstract TruffleString execute(Object hostObject, int length);

        @Specialization
        TruffleString bench(Object hostObject, int length,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            byte[] input = (byte[]) DummyLanguageContext.get(this).getEnv().asHostObject(hostObject);
            return fromByteArrayNode.execute(input, 0, length, TruffleString.Encoding.UTF_8, false);
        }
    }

    abstract static class CalcStringAttributesUTF16Node extends Node {

        abstract TruffleString execute(Object hostObject, int length);

        @Specialization
        TruffleString bench(Object hostObject, int length,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            byte[] input = (byte[]) DummyLanguageContext.get(this).getEnv().asHostObject(hostObject);
            return fromByteArrayNode.execute(input, 0, length, TruffleString.Encoding.UTF_16, false);
        }
    }

    abstract static class FromByteArrayUTF16Node extends Node {

        abstract TruffleString execute(Object hostObject, int length);

        @Specialization
        TruffleString bench(Object hostObject, int length,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            byte[] input = (byte[]) DummyLanguageContext.get(this).getEnv().asHostObject(hostObject);
            return fromByteArrayNode.execute(input, 0, length, TruffleString.Encoding.UTF_16, true);
        }
    }

    abstract static class FromByteArrayUTF32Node extends Node {

        abstract TruffleString execute(Object hostObject, int length);

        @Specialization
        TruffleString bench(Object hostObject, int length,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode) {
            byte[] input = (byte[]) DummyLanguageContext.get(this).getEnv().asHostObject(hostObject);
            return fromByteArrayNode.execute(input, 0, length, TruffleString.Encoding.UTF_32, true);
        }
    }

    @Override
    protected DummyLanguageContext createContext(Env env) {
        return new DummyLanguageContext(env);
    }

    @Override
    protected boolean patchContext(DummyLanguageContext context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
    }

    public static final class DummyLanguageContext {

        private static final ContextReference<DummyLanguageContext> REFERENCE = ContextReference.create(TStringTestDummyLanguage.class);

        @CompilationFinal private Env env;

        DummyLanguageContext(Env env) {
            this.env = env;
        }

        void patchContext(Env patchedEnv) {
            this.env = patchedEnv;
        }

        public Env getEnv() {
            return env;
        }

        public static DummyLanguageContext get(Node node) {
            return REFERENCE.get(node);
        }
    }
}
