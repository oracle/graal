/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.workflowtest;

import com.oracle.truffle.tools.chromeinspector.workflowtest.CDTLib.Actions;
import com.oracle.truffle.tools.chromeinspector.workflowtest.CDTLib.Actions.Action;
import com.oracle.truffle.tools.chromeinspector.workflowtest.CDTLib.CallFrames;
import com.oracle.truffle.tools.chromeinspector.workflowtest.CDTLib.EditorGutter;
import com.oracle.truffle.tools.chromeinspector.workflowtest.CDTLib.Scope;
import com.oracle.truffle.tools.chromeinspector.workflowtest.CDTLib.Sources;

import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.openqa.selenium.WebDriver;

/**
 * Test of GraalVM Inspector with CDT (Chrome DevTools) UI.
 */
public class TestCDTBasics extends AbstractTest {

    /**
     * Perform the test with the {@code driver} and provide the {@code output}.
     */
    public static void test(WebDriver driver, Consumer<String> output) throws TimeoutException {
        Sources sources = Sources.find(driver, TIMEOUT);
        sources.iterateOpenFiles(file -> output.accept("Opened file: " + file));

        Actions actions = Actions.find(driver, TIMEOUT);
        assertTrue(actions.isEnabled(Action.STEP), "Step not enabled.");

        CallFrames callFrames = CallFrames.find(driver, TIMEOUT);
        printStack(callFrames, 1, output);

        Scope scope = new Scope(driver);
        printScope(scope, output);

        actions.click(Action.STEP_OVER);
        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 1, output);
        printScope(scope, output);

        actions.click(Action.STEP_OVER);
        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 1, output);
        printScope(scope, output);

        actions.click(Action.STEP_INTO);
        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 2, output);
        printScope(scope, output);

        actions.click(Action.STEP);
        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 2, output);
        printScope(scope, output);

        actions.click(Action.STEP);
        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 2, output);
        printScope(scope, output);

        EditorGutter gutter = new EditorGutter(driver);
        gutter.clickAt(36); // Set breakpoint at line 36
        actions.click(Action.PAUSE_RESUME);

        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 2, output);
        printScope(scope, output);

        actions.click(Action.STEP);
        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 2, output);
        printScope(scope, output);

        actions.click(Action.DEACTIVATE_BREAKPOINTS);

        actions.click(Action.STEP_OUT);
        actions.waitTillEnabled(Action.STEP, true, TIMEOUT);
        printStack(callFrames, 1, output);
        printScope(scope, output);

        gutter.clickAt(36); // Remove breakpoint at line 36

        actions.click(Action.PAUSE_RESUME);

        // The program should finish, the pause/resume button will eventually be disabled.
        // But the pause/resume action is kept enabled right after the program finishes.
        //actions.waitTillEnabled(Action.PAUSE_RESUME, false, TIMEOUT);
        CDTLib.waitDisconnected(driver, TIMEOUT);
    }

    private static void printStack(CallFrames callFrames, int assertLength, Consumer<String> output) {
        int length = callFrames.getStackLength();
        assertEquals(assertLength, length, "Stack depth");
        output.accept("Stack (" + length + ")");
        output.accept(callFrames.getNewTopFrameText(TIMEOUT).replace('\n', ' '));
        for (int i = 1; i < length; i++) {
            output.accept(callFrames.getFrameText(i).replace('\n', ' '));
        }
    }

    private static void printScope(Scope scope, Consumer<String> output) {
        String[] elements = scope.getScope();
        output.accept("Scope (" + elements.length + ")");
        for (String s : elements) {
            String var = s.replace('\n', ' ');
            if (var.length() > 50) {
                var = var.substring(0, 50);
            }
            output.accept(var);
        }
    }
}
