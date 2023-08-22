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
package com.oracle.truffle.api.debug.test;

import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.instrumentation.test.YieldResumeTest.YieldResumeLanguage;

import org.graalvm.polyglot.Source;

import org.junit.Assert;
import org.junit.Test;

public class DebugYieldResumeTest extends AbstractDebugTest {

    @Test
    public void testYieldResume() {
        final Source sourceYield = Source.create(YieldResumeLanguage.ID, "yield");
        final Source sourceStatement = Source.create(YieldResumeLanguage.ID, "statement");
        final Source sourceResume = Source.create(YieldResumeLanguage.ID, "resume");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(sourceYield);

            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("yield", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
            startEval(sourceStatement);
            expectDone();
            startEval(sourceResume);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("resume", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
        }
    }

    @Test
    public void testMultiYieldResume1() {
        final Source sourceYield = Source.create(YieldResumeLanguage.ID, "yield");
        final Source sourceStatement = Source.create(YieldResumeLanguage.ID, "statement");
        final Source sourceResume = Source.create(YieldResumeLanguage.ID, "resume");
        try (DebuggerSession session = startSession()) {
            session.suspendNextExecution();
            startEval(sourceYield);

            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("yield", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
            startEval(sourceYield);
            expectDone();
            startEval(sourceStatement);
            expectDone();
            startEval(sourceResume);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("resume", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
            startEval(sourceResume);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("resume", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
        }
    }

    @Test
    public void testMultiYieldResume2() {
        final Source sourceYield = Source.create(YieldResumeLanguage.ID, "yield");
        final Source sourceStatement = Source.create(YieldResumeLanguage.ID, "statement");
        final Source sourceResume = Source.create(YieldResumeLanguage.ID, "resume");
        try (DebuggerSession session = startSession()) {
            startEval(sourceYield);
            expectDone();
            session.suspendNextExecution();

            startEval(sourceYield);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("yield", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
            startEval(sourceStatement);
            expectDone();
            startEval(sourceResume);
            expectDone();
            startEval(sourceStatement);
            expectDone();
            startEval(sourceResume);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("resume", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
            startEval(sourceStatement);
            expectSuspended((SuspendedEvent event) -> {
                Assert.assertEquals("statement", event.getSourceSection().getCharacters());
                event.prepareStepOver(1);
            });
            expectDone();
        }
    }
}
