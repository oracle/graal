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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchShadowRootException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Library for Selenium tests of CDT (Chrome DevTools).
 */
public final class CDTLib {

    private CDTLib() {
        throw new UnsupportedOperationException();
    }

    private static <T> T find(WebDriver driver, Duration timeout, Function<WebDriver, T> creator) throws NoSuchElementException {
        return new WebDriverWait(driver, timeout).until(creator);
    }

    private static void selectMainTab(WebDriver driver, String tabName) throws NoSuchElementException {
        WebElement mainTabbedPane = ExpectedConditions.presenceOfElementLocated(By.className("main-tabbed-pane")).apply(driver);
        WebElement tab = mainTabbedPane.getShadowRoot().findElement(By.id(tabName));
        if (!tab.isSelected()) {
            tab.click();
        }
    }

    public static void waitDisconnected(WebDriver driver, Duration timeout) {
        WebElement dimmedPane = new WebDriverWait(driver, timeout).until((d) -> ExpectedConditions.presenceOfElementLocated(By.className("dimmed-pane")).apply(d));
        // We have dimmed pane
    }

    public static final class Sources {

        private final WebElement sourcesView;

        public Sources(WebDriver driver) throws NoSuchElementException {
            sourcesView = ExpectedConditions.presenceOfElementLocated(By.id("sources-panel-sources-view")).apply(driver);
        }

        /**
         * Select the Sources tab.
         */
        public static void select(WebDriver driver) {
            selectMainTab(driver, "tab-sources");
        }

        public static Sources find(WebDriver driver, Duration timeout) throws NoSuchElementException {
            return CDTLib.find(driver, timeout, Sources::new);
        }

        public void iterateOpenFiles(Consumer<String> fileConsumer) {
            for (WebElement div : sourcesView.findElements(By.tagName("div"))) {
                try {
                    SearchContext shadow = div.getShadowRoot();
                    List<WebElement> fileTitles = shadow.findElements(By.className("tabbed-pane-header-tab-title"));
                    if (!fileTitles.isEmpty()) {
                        for (WebElement title : fileTitles) {
                            fileConsumer.accept(title.getText());
                        }
                        break;
                    }
                } catch (NoSuchShadowRootException ex) {
                    // This div does not contain shadow root, continue with the next one.
                } catch (InvalidArgumentException ex) {
                    // Invalid locator, no sources are displayed, most likely.
                    break;
                }
            }
        }
    }

    public static final class EditorGutter {

        private final WebElement gutter;

        public EditorGutter(WebDriver driver) {
            WebElement editor = ExpectedConditions.presenceOfElementLocated(By.tagName("devtools-text-editor")).apply(driver);
            gutter = editor.getShadowRoot().findElement(By.cssSelector(".cm-gutter.cm-lineNumbers"));
        }

        public void clickAt(int lineNumber) {
            List<WebElement> lines = gutter.findElements(By.className("cm-gutterElement"));
            lines.get(lineNumber).click();
        }
    }

    public static final class Actions {

        private final WebDriver driver;
        private final List<WebElement> toolbarButtons;

        public Actions(WebDriver driver) throws NoSuchElementException {
            this.driver = driver;
            WebElement toolbar = ExpectedConditions.presenceOfElementLocated(By.cssSelector("devtools-toolbar.scripts-debug-toolbar")).apply(driver);
            toolbarButtons = toolbar.findElements(By.cssSelector("devtools-button.toolbar-button"));
            if (toolbarButtons.size() < Action.values().length) {
                throw new NoSuchElementException("Insufficient numebr of toolbar buttons: " + toolbarButtons.size() + " expecting " + Action.values().length);
            }
        }

        public static Actions find(WebDriver driver, Duration timeout) throws NoSuchElementException {
            return CDTLib.find(driver, timeout, Actions::new);
        }

        public void click(Action a) {
            toolbarButtons.get(a.ordinal()).click();
        }

        public boolean isEnabled(Action a) {
            WebElement button = toolbarButtons.get(a.ordinal());
            return button.isEnabled() && !"true".equals(button.getAttribute("disabled"));
        }

        public void waitTillEnabled(Action a, boolean enabled, Duration timeout) throws TimeoutException {
            new WebDriverWait(driver, timeout).until((d) -> isEnabled(a) == enabled);
        }

        public String getName(Action a) {
            return toolbarButtons.get(a.ordinal()).getAttribute("aria-label");
        }

        public enum Action {
            PAUSE_RESUME,
            STEP_OVER,
            STEP_INTO,
            STEP_OUT,
            STEP,
            DEACTIVATE_BREAKPOINTS
        }
    }

    public static final class CallFrames {

        private static final String CALL_FRAME_ITEM_SELECTOR = "div.call-frame-item";

        private final WebDriver driver;
        private final SearchContext framesContext;
        private String lastTopFrameText = "";

        public CallFrames(WebDriver driver) throws NoSuchElementException {
            this.driver = driver;
            WebElement toolbarDrawer = ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.scripts-debug-toolbar-drawer")).apply(driver);
            JavascriptExecutor jsExec = (JavascriptExecutor) driver;
            WebElement toolbarDrawerParent = (WebElement) jsExec.executeScript("return arguments[0].parentElement", toolbarDrawer);

            SearchContext fContext = null;
            for (WebElement vbox : toolbarDrawerParent.findElements(By.className("vbox"))) {
                try {
                    SearchContext shadow = vbox.getShadowRoot();
                    List<WebElement> frameElements = shadow.findElements(By.cssSelector(CALL_FRAME_ITEM_SELECTOR));
                    if (!frameElements.isEmpty()) {
                        fContext = shadow;
                        break;
                    }
                } catch (NoSuchShadowRootException ex) {
                    // Some vbox elements do not have shadow roots, skip them.
                }
            }
            if (fContext == null) {
                throw new NoSuchElementException("Frame elements not found.");
            }
            framesContext = fContext;
        }

        public static CallFrames find(WebDriver driver, Duration timeout) throws NoSuchElementException {
            return CDTLib.find(driver, timeout, CallFrames::new);
        }

        public int getStackLength() {
            return framesContext.findElements(By.cssSelector(CALL_FRAME_ITEM_SELECTOR)).size();
        }

        public String getFrameText(int frameIndex) {
            return framesContext.findElements(By.cssSelector(CALL_FRAME_ITEM_SELECTOR)).get(frameIndex).getText();
        }

        public String getNewTopFrameText(Duration timeout) {
            return new WebDriverWait(driver, timeout).until((d) -> {
                String newText = getFrameText(0);
                if (!newText.equals(lastTopFrameText)) {
                    lastTopFrameText = newText;
                    return newText;
                } else {
                    return null;
                }
            });
        }
    }

    public static final class Scope {

        private final SearchContext scopeContext;

        public Scope(WebDriver driver) throws NoSuchElementException {
            WebElement toolbarDrawer = ExpectedConditions.presenceOfElementLocated(By.cssSelector("div.scripts-debug-toolbar-drawer")).apply(driver);
            WebElement widget = toolbarDrawer.findElement(By.xpath(".//following-sibling::div"));
            SearchContext shadowRoot = widget.findElement(By.xpath(".//div[4]/div")).getShadowRoot();
            scopeContext = shadowRoot.findElement(By.cssSelector("div.expanded")).getShadowRoot();
        }

        public String[] getScope() {
            List<WebElement> liElements = scopeContext.findElements(By.cssSelector("li"));
            int n = liElements.size();
            String[] result = new String[n];
            for (int i = 0; i < n; i++) {
                result[i] = liElements.get(i).getText();
            }
            return result;
        }
    }
}
