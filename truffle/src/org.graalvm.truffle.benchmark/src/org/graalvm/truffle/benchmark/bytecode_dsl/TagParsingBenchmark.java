/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode_dsl;

import java.util.Arrays;

import com.oracle.truffle.api.bytecode.test.ManyTagsRootNodeGen;
import com.oracle.truffle.api.bytecode.test.TagTest;
import org.graalvm.truffle.benchmark.TruffleBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import com.oracle.truffle.api.bytecode.BytecodeConfig;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class TagParsingBenchmark extends TruffleBenchmark {

    private static final int NUM_TAG_OPERATIONS = 1024;

    private static final Class<?>[] PROVIDED_TAGS = {
                    TagTest.TestTag1.class,
                    TagTest.TestTag2.class,
                    TagTest.TestTag3.class,
                    TagTest.TestTag4.class,
                    TagTest.TestTag5.class,
                    TagTest.TestTag6.class,
                    TagTest.TestTag7.class,
                    TagTest.TestTag8.class,
                    TagTest.TestTag9.class,
                    TagTest.TestTag10.class,
                    TagTest.TestTag11.class,
                    TagTest.TestTag12.class,
                    TagTest.TestTag13.class,
                    TagTest.TestTag14.class,
                    TagTest.TestTag15.class,
                    TagTest.TestTag16.class,
                    TagTest.TestTag17.class,
                    TagTest.TestTag18.class,
                    TagTest.TestTag19.class,
                    TagTest.TestTag20.class,
                    TagTest.TestTag21.class,
                    TagTest.TestTag22.class,
                    TagTest.TestTag23.class,
                    TagTest.TestTag24.class,
                    TagTest.TestTag25.class,
                    TagTest.TestTag26.class,
                    TagTest.TestTag27.class,
                    TagTest.TestTag28.class,
                    TagTest.TestTag29.class,
                    TagTest.TestTag30.class,
                    TagTest.TestTag31.class,
                    TagTest.TestTag32.class,
    };
    private static final int NUM_PROVIDED_TAGS = PROVIDED_TAGS.length;
    private static final Class<?>[][] TAG_GROUPS = createTagGroups();

    @Benchmark
    public Object parseWithTags() {
        return ManyTagsRootNodeGen.create(null, BytecodeConfig.COMPLETE, TagParsingBenchmark::parse);
    }

    @Benchmark
    public Object parseWithoutTags() {
        return ManyTagsRootNodeGen.create(null, BytecodeConfig.DEFAULT, TagParsingBenchmark::parse);
    }

    private static void parse(ManyTagsRootNodeGen.Builder b) {
        b.beginRoot();
        b.beginReturn();
        for (int i = 0; i < NUM_TAG_OPERATIONS; i++) {
            b.beginTag(TAG_GROUPS[i % NUM_PROVIDED_TAGS]);
        }
        b.emitLoadConstant(0);
        for (int i = NUM_TAG_OPERATIONS - 1; i >= 0; i--) {
            b.endTag(TAG_GROUPS[i % NUM_PROVIDED_TAGS]);
        }
        b.endReturn();
        b.endRoot();
    }

    private static Class<?>[][] createTagGroups() {
        Class<?>[][] tagGroups = new Class<?>[NUM_PROVIDED_TAGS][];
        for (int i = 0; i < NUM_PROVIDED_TAGS; i++) {
            tagGroups[i] = Arrays.copyOf(PROVIDED_TAGS, i + 1);
        }
        return tagGroups;
    }
}
