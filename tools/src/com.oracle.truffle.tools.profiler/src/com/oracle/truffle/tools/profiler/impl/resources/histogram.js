/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

var histogram;
var h_max;
var h_max_depth;
var h_search_regex;

function h_init(evt) {
    histogram = document.getElementById("histogram");
    let bar = histogramData[0];
    h_max = bar.c + bar.i
    h_search_regex = null;
}

// search
function h_search(term) {
    h_search_regex = new RegExp(term);
    h_update_search();
}

function h_update_search() {
    let re = h_search_regex;
    if (re == null) {
        return;
    }
    for (let i = 0; i < histogramData.length; i++) {
        let bar = histogramData[i];
        if (name_for_sample(bar).match(re)) {
            bar.searchMatch = true;
            let e = h_element_for_id(i);
            if (e != null) {
                let r = e.children[1];
                r.style.fill = searchColor;
            }
        }
    }
}

var hilight_element = null;

function h_reset_search() {
    for (let i = 0; i < histogramData.length; i++) {
        let bar = histogramData[i];
        if (bar.searchMatch = true) {
            bar.searchMatch = false;
            let e = h_element_for_id(i);
            if (e != null) {
                let color = bar.currentColor;
                if (color == undefined) {
                    color = fg_color_for_sample(color_type, bar);
                }
                let r = e.children[1];
                r.style.fill = color;
            }
        }
    }
    h_search_regex = null;
}

function h_highlight(e) {
    if (hilight_element == e) {
        hilight_element = null;
        reset_search();
    } else {
        let bar = histogram_entry_for_id(e.getAttribute("id").substring(2));
        reset_search();
        hilight_element = e;
        let name = name_for_sample(bar);
        // Ensure we escape anything that might cause a problem with the regexp.
        name = "^" + name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + "$"; // $& means the whole matched string
        search(name);
    }
}

function histogram_entry_for_id(id) {
    return histogramData[id];
}

function reposition_histogram(y) {
    let svg = histogram.parentNode;
    svg.setAttribute("y", y);
}

function h_element_for_id(id) {
    return document.getElementById("h_" + id);
}

function h_create_element_for_id(id, bar, width) {
    let y = h_top_padding + id * h_frameheight;
    let e = document.createElementNS("http://www.w3.org/2000/svg", "g");
    e.className.baseVal = "func_h";
    e.onmouseover = function(e) {s(this)};
    e.onmouseout = function(e) {c(this)};
    e.onclick = function(e) {h_highlight(this)};
    e.id =  "h_" + id;

    let title = document.createElementNS("http://www.w3.org/2000/svg", "title");
    title.textContent = "Blah";

    let r = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    r.x.baseVal.value = xpad;
    r.y.baseVal.value = y;
    r.width.baseVal.value = width;
    r.height.baseVal.value = fg_frameheight

    r.style.fill = fg_color_for_sample(color_type, bar);
    r.rx.baseVal.value = 2;
    r.ry.baseVal.vlaue = 2;

    let t = document.createElementNS("http://www.w3.org/2000/svg", "text");
    t.style.textAnchor = "left";
    t.setAttribute("x", xpad + 3);
    t.setAttribute("y", y - 5 + fg_frameheight);
    t.style.fontSize = fontSize;
    t.style.fontFamily = "Verdana";
    t.style.fill = "rgb(0, 0, 0)";

    e.appendChild(title);
    e.appendChild(r);
    e.appendChild(t);
    histogram.appendChild(e);
    return e;
}

function h_update_bar(id, bar) {
    let width = ((bar.c + bar.i) / h_max) * (h_width - 2 * xpad);
    let space_after = h_width - 2 * xpad - 3 - width; // Allows for space after the bar and not extending into the right margin.
    if (width >= h_minwidth && id < 100) {
        if ((id + 1) > h_max_depth) {
            h_max_depth = id + 1;
        }
        let e = h_element_for_id(id);
        if (e == undefined) {
            e = h_create_element_for_id(id, bar, width);
        }
        e.style.display = "block";
        let title = e.firstElementChild;
        let r = e.children[1];
        let t = e.lastElementChild;

        let name = name_for_sample(bar);

        title.textContent = "Function: " + name + "\n" +
            (bar.c + bar.i) + " samples (" + bar.i + " interpreted, " + bar.c + " compiled).\n" +
            (100 * (bar.c + bar.i) / (fg_xmax - fg_xmin)).toFixed(2) + "% of displayed samples.\n";

        r.width.baseVal.value = width;
        r.style.fill = fg_color_for_sample(color_type, bar);
        t.textContent = name;
        let t_width = t.textLength.baseVal.value;
        if (t_width < width - 6) {
            // If the text fits in the bar, put it there.
            t.x.baseVal[0].value = xpad + 3;
        } else if (t_width < space_after)  {
            // If the text fits after the bar put it there
            t.x.baseVal[0].value = xpad + 3 + width;
        } else {
            let w;
            if (width > space_after) {
                t.x.baseVal[0].value = xpad + 3;
                w = width;
            } else {
                t.x.baseVal[0].value = xpad + 3 + width;
                w = space_after;
            }
            let x = search_text_width(w, t, name_for_sample(bar), get_txt_lengths(name_for_sample(bar)));

            if (x > 2) {
                t.textContent = name_for_sample(bar).substring(0,x - 2) + "..";
            } else {
                t.textContent = "";
            }
        }
    }
}

function rebuild_histogram(sample) {
    h_max_depth = 0;
    let histogram = document.getElementById("histogram");
    for (const child of document.getElementsByClassName("func_h")) {
        child.style.display = "none";
    }
    let bars = {};
    calculate_histogram_bars(bars, sample);
    let data = [];
    for (const bar in bars) {
        data.push(bars[bar])
    }
    data.sort((a, b) => ((b["i"] + b["c"]) - (a["i"] + a["c"])));

    h_max = data[0].c + data[0].i;
    for (let i = 0; i < data.length; i++) {
        let bar = data[i];
        h_update_bar(i, bar);
    }

    histogramData = data;

    h_update_search();
    h_canvas_resize();
}

function h_canvas_resize() {
    let height = h_max_depth * h_frameheight + h_top_padding + h_bottom_padding;
    let svg = document.firstElementChild;
    let h_svg = histogram.parentElement;
    let old_height = h_svg.height.baseVal.value;
    h_svg.height.baseVal.value = height;
    h_svg.viewBox.baseVal.height = height;
    let h_canvas = document.getElementById("h_canvas");
    h_canvas.height.baseVal.value = height;
    svg.height.baseVal.value = svg.height.baseVal.value - old_height + height;
    svg.viewBox.baseVal.height = svg.height.baseVal.value;
}

function calculate_histogram_bars(bars, sample) {
    let bar;
    if (!bars.hasOwnProperty(sample["n"])) {
        bar = [];
        bar["id"] = sample["id"];
        bar["i"] = 0;
        bar["c"] = 0;
        bar["l"] = sample["l"];
        bar["n"] = sample["n"];
        bars[sample["n"]] = bar;
    } else {
        bar = bars[sample["n"]];
    }
    bar["i"] = bar["i"] + sample["i"];
    bar["c"] = bar["c"] + sample["c"];
    if (sample.hasOwnProperty("s")) {
        for (const child of sample["s"]) {
            calculate_histogram_bars(bars, child);
        }
    }
}

function h_update_color(color_type) {
    for (let i = 0; i < histogramData.length; i++) {
        let bar = histogramData[i];
        let color = fg_color_for_sample(color_type, bar);
        bar.currentColor = color;
        if (bar.searchMatch != true) {
            let e = h_element_for_id(i);
            if (e != null) {
                let r = e.children[1];
                r.style.fill = color;
            }
        }
    }
}

function h_resize(new_width) {
    h_width = new_width
    let h_svg = histogram.parentElement;
    h_svg.width.baseVal.value = new_width;
    let viewbox = h_svg.viewBox.baseVal;
    viewbox.width = new_width
    let h_canvas = document.getElementById("h_canvas");
    h_canvas.width.baseVal.value = new_width;
    rebuild_histogram(fg_zoomed_sample);
    document.getElementById("h_title").setAttribute("x", new_width / 2);
}
