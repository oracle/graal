/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

// @formatter:off

/*
 @ApiInfo(
 group="Tutorial"
 )
 */
/**
 * <h1>Truffle Tutorial: Implementing a Language with Truffle</h1>
 *
 * <div id="contents">
 *
 * For an excellent, in-depth presentation on how to implement your language with Truffle,
 * please have a look at a three hour walkthrough presented at a recent
 * Conference on Programming Language Design and Implementation
 * (<a href="http://conf.researchr.org/home/pldi-2016">PLDI 2016</a>).
 * <p>
 * <a href="https://youtu.be/FJY96_6Y3a4">Video recording</a>
 * <br>
 * <a href="https://lafo.ssw.uni-linz.ac.at/pub/papers/2016_PLDI_Truffle.pdf">Download Slides</a>
 * <p>
 * Related information:
 * </ul>
 * <li>{@link com.oracle.truffle.api.TruffleLanguage}: base class for Truffle language implementations.</li>
 * <li>{@link com.oracle.truffle.api.vm.PolyglotEngine}: execution environment for Truffle-implemented languages.</li>
 * <li>{@code com.oracle.truffle.sl.SLLanguage - link?}: Truffle implementation of the tutorial <em>Simple Language</em>.</li>
 * <li><a href=
 * "{@docRoot}/com/oracle/truffle/tutorial/package-summary.html">Other Truffle Tutorials</a></li>
 * </ul>
 *
 * </div>
<script>

window.onload = function () {
    function hide(tagname, cnt, clazz) {
        var elems = document.getElementsByTagName(tagname)
        for (var i = 0; cnt > 0; i++) {
            var e = elems[i];
            if (!e) {
                break;
            }
            if (!clazz || e.getAttribute("class") === clazz) {
                e.style.display = 'none';
                cnt--;
            }
        }
    }
    hide("h1", 1);
    hide("h2", 1);
    hide("p", 1);
    hide("div", 1, "docSummary");

    var toc = "";
    var level = 0;

    document.getElementById("contents").innerHTML =
        document.getElementById("contents").innerHTML.replace(
            /<h([\d])>([^<]+)<\/h([\d])>/gi,
            function (str, openLevel, titleText, closeLevel) {
                if (openLevel != closeLevel) {
                    return str;
                }

                if (openLevel > level) {
                    toc += (new Array(openLevel - level + 1)).join("<ul>");
                } else if (openLevel < level) {
                    toc += (new Array(level - openLevel + 1)).join("</ul>");
                }

                level = parseInt(openLevel);

                var anchor = titleText.replace(/ /g, "_");
                toc += "<li><a href=\"#" + anchor + "\">" + titleText
                    + "</a></li>";

                return "<h" + openLevel + "><a name=\"" + anchor + "\">"
                    + titleText + "</a></h" + closeLevel + ">";
            }
        );

    if (level) {
        toc += (new Array(level + 1)).join("</ul>");
    }

    document.getElementById("toc").innerHTML += toc;
};
</script>
 *
 * @since 0.25
 */
package com.oracle.truffle.tutorial.newlanguage;
