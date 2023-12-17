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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * The test executor.
 */
public class Test {

    /**
     * @param args launcher and script file.
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            args = args[0].split(" ");
        }
        String graalLauncher = args[0];
        String scriptFile = args[1];
        String chromeBin;
        String chromeVersion;
        if (args.length > 2) {
            chromeBin = args[2];
            chromeVersion = args[3].substring(0, args[3].indexOf('.'));
        } else {
            String[] chrome = DownloadChromiumDev.download();
            chromeBin = chrome[0];
            chromeVersion = chrome[1];
        }

        WebDriver driver = createDriver(chromeBin, chromeVersion);
        List<String> goldenLines = Files.readAllLines(Paths.get(scriptFile + ".out"));
        OutputComparator outputComparator = new OutputComparator(goldenLines);
        String url = Launcher.launch(graalLauncher, scriptFile, true, outputComparator);
        driver.get(url);
        TestCDTBasics.test(driver, outputComparator);
        driver.close();
        driver.quit();
    }

    private static WebDriver createDriver(String chromeBin, String chromeVersion) {
        ChromeOptions options = new ChromeOptions();
        options.setBinary(chromeBin);
        options.setBrowserVersion(chromeVersion);
        options.addArguments("--headless=new");
        System.err.println("Creating driver using options: "+options);
        WebDriver driver;
        try {
            driver = new ChromeDriver(options);
        } catch (Throwable t) {
            System.err.println("LAUNCH: ERROR: "+t);
            t.printStackTrace(System.err);
            throw t;
        }
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(1000));
        return driver;
    }

    private static class OutputComparator implements Consumer<String> {

        private final List<String> goldenLines;
        private int index = 0;

        OutputComparator(List<String> goldenLines) {
            this.goldenLines = goldenLines;
        }

        @Override
        public void accept(String line) {
            String goldenLine;
            do {
                goldenLine = goldenLines.get(index++);
            } while (goldenLine.startsWith("#") || line.isBlank());
            line = line.trim();
            if (!goldenLine.equals(line)) {
                if (!Pattern.matches(goldenLine, line)) {
                    System.err.println("Output mismatch at line " + index + ". Expected: '" + goldenLine + "', actual: '" + line + "'.");
                    System.exit(1);
                }
            }
            System.out.println("[OUT] " + line);
        }
    }
}
