/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

// highlighting and info text

function s(node) {        // show
    let sample = null;
    if (node.getAttribute("class") == "func_g") {
        sample = sample_for_id(node.getAttribute("id").substring(2));
    } else if (node.getAttribute("class") == "func_h") {
        sample = histogram_entry_for_id(node.getAttribute("id").substring(2));
    }
    let name = name_for_sample(sample)
    let details = name + " (" + languageNames[sample.l] + ") - ";
    if (sample.hasOwnProperty("h")) {
        if (fg_collapsed) {
            details = details + "(Self:" + (sample.ri + sample.rc) + " samples " +
                "Total: " + (sample.rh) + " samples)";
        } else {
            details = details + "(Self:" + (sample.i + sample.c) + " samples " +
                "Total: " + (sample.h) + " samples)";
        }
    } else {
        details = details + "(" + (sample.i + sample.c) + " samples)";
    }

    flamegraph_details.textContent = details;
}

function c(node) {            // clear
    flamegraph_details.nodeValue = ' ';
}

// Utility function

function child(parent, name) {
    for (let i=0; i<parent.children.length;i++) {
        if (parent.children[i].tagName == name)
            return parent.children[i];
    }
    return;
}

function save_attr(e, attr, val) {
    if (e.hasAttribute("_orig_"+attr)) return;
    if (!e.hasAttribute(attr)) return;
    e.setAttribute("_orig_"+attr, val != undefined ? val : e.getAttribute(attr));
}

function restore_attr(e, attr) {
    if (!e.hasAttribute("_orig_"+attr)) return;
    e.setAttribute(attr, e.getAttribute("_orig_"+attr));
    e.removeAttribute("_orig_"+attr);
}

function find_sample_in_tree(id, parents, unrelated) {
    let result = profileData[id];
    let sample = result;
    let parent = sample.p
    while (parent != null) {
        parents.push(profileData[parent]);
        for (const sibling of profileData[parent].s) {
            if (sibling != sample.id) {
                unrelated.push(profileData[sibling]);
            }
        }
        sample = profileData[parent];
        parent = sample.p;
    }
    return result;
}

function validate_no_overlap(sample, parents, unrelated) {
    let ary = [];
    for (const p of parents) {
        ary[p.id] = 1;
    }

    let iter = sample_and_children_depth_first(sample);
    let c = iter.next();
    while (!c.done) {
        if (ary[c.value.id] == undefined) {
            ary[c.value.id] = 1;
        } else {
            ary[c.value.id] = ary[c.value.id] + 1;
        }
        c = iter.next();
    }

    for (const u of unrelated) {
        iter = sample_and_children_depth_first(u);
        c = iter.next();
        while (!c.done) {
            if (ary[c.value.id] == undefined) {
                ary[c.value.id] = 1;
            } else {
                ary[c.value.id] = ary[c.value.id] + 1;
            }
            c = iter.next();
        }
    }

    let problems = 0;
    for (let i = 0; i < ary.length; i++) {
        if (ary[i] != 1) {
            console.log("Unexpected value of " + ary[i] + " for " + i + ".")
            problems++;
        }
    }

    if (problems > 0) {
        console.log("Problems found for sample " + sample.id + ".");
    }
}

function sample_parents_and_unrelated_for_id(id) {
    let parents = [];
    let unrelated = [];
    let result =  [find_sample_in_tree(id, parents, unrelated), parents, unrelated];
    return result
}

function sample_for_id(id) {
    return profileData[id];
}

function depth_for_sample(sample) {
    if (!sample.hasOwnProperty("depth")) {
        let parent = profileData[sample.p];
        let depth = 0;
        while (parent != null) {
            if (parent.hasOwnProperty("depth")) {
                depth += parent.depth + 1;
                break;
            } else {
                depth += 1;
            }
            parent = profileData[parent.p];
        }
        sample.depth = depth;
    }
    return sample.depth;
}

function collapsed_depth_for_sample(sample) {
    if (!sample.hasOwnProperty("rdepth")) {
        let parent = profileData[sample.rp];
        let depth = 0;
        while (parent != null) {
            if (parent.hasOwnProperty("rdepth")) {
                depth += parent.rdepth + 1;
                break;
            } else {
                depth += 1;
            }
            parent = profileData[parent.rp];
        }
        sample.rdepth = depth;
    }
    return sample.rdepth;
}

function sample_and_children_depth_first(sample) {
    let stack = [sample];
    return {
        next: function() {
            if (stack.length == 0) {
                return {value: null, done: true}
            } else {
                let result = stack.pop();

                let rdepth = depth_for_sample(result);
                for(const child of direct_children(result).reverse()) {
                    if (!child.hasOwnProperty("depth")) {
                        child.depth = rdepth + 1;
                    }
                    stack.push(child);
                }
                return {value: result, done: false}
            }
        }
    }
}

function collapsed_sample_and_children_depth_first(sample) {
    let stack = [sample];
    return {
        next: function() {
            if (stack.length == 0) {
                return {value: null, done: true}
            } else {
                let result = stack.pop();

                let rdepth = depth_for_sample(result);
                for(const child of collapsed_children(result).reverse()) {
                    if (!child.hasOwnProperty("depth")) {
                        child.depth = rdepth + 1;
                    }
                    stack.push(child);
                }
                return {value: result, done: false}
            }
        }
    }
}

function direct_children(sample) {
    let result = [];
    if (sample.hasOwnProperty("s")) {
        for (const childId of sample["s"]) {
            let child = sample_for_id(childId);
            result.push(child);
        }
    }
    return result;
}

function collapsed_children(sample) {
    let result = [];
    if (sample.hasOwnProperty("rs")) {
        for (const childId of sample["rs"]) {
            let child = sample_for_id(childId);
            result.push(child);
        }
    }
    return result;
}

function title(e) {
    if (e.getAttribute("class") == "func_g") {
        return name_for_sample(sample_for_id(e.getAttribute("id").substring(2)));
    } else if (e.getAttribute("class") == "func_h") {
        return name_for_sample(histogram_entry_for_id(e.getAttribute("id").substring(2)));
    } else {
        return "";
    }
}

function key_for_sample(sample) {
    return sample.k;
}

function name_for_sample(sample) {
    let key = sampleKeys[sample.k];
    return profileNames[key[0]];
}

function source_for_sample(sample) {
    let key = sampleKeys[sample.k];
    return sourceNames[key[1]];
}

function source_line_for_sample(sample) {
    let key = sampleKeys[sample.k];
    return key[2];
}

function function_name(e) {
    return title(e);
}

function update_text(e) {
    var r = child(e, "rect");
    var t = child(e, "text");
    var w = parseFloat(r.attributes["width"].value) -3;
    var txt = function_name(e);
    t.setAttribute("x", parseFloat(r.getAttribute("x")) +3);

    update_text_parts(e, r, t, w, txt);
}

function update_text_parts(e, r, t, w, txt) {
    // Smaller than this size won't fit anything
    if (w < 2*fontSize*fontWidth) {
        t.style["display"] = "none";
        return;
    }

    let lengths = get_txt_lengths(txt);

    // Fit in full text width
    if (/^ *$/.test(txt) || substring_width(t, txt, txt.length, lengths) < w) {
        t.textContent = txt;
        t.style["display"] = "block";
        return;
    }

    let x = search_text_width(w, t, txt, lengths);

    if (x > 2) {
        t.style["display"] = "block";
        t.textContent = txt.substring(0,x - 2) + "..";
    } else {
        t.style["display"] = "none";
    }
}

function search_text_width(w, t, txt, lengths) {
    let x = Math.round(txt.length / 4);
    let good_x = 0;
    let bad_x = txt.length;

    while (x != good_x) {
        let w2 = substring_width(t, txt, x, lengths);
        if (w2 > w) {
            bad_x = x;
        } else {
            good_x = x;
        }
        x = Math.round((bad_x - good_x) / 4) + good_x;
    }
    return x;
}

var txt_length_cache = {}

function get_txt_lengths(txt) {
    let lengths = txt_length_cache[txt]
    if (lengths == undefined) {
        lengths = new Array(txt.length);
        lengths.fill(-1);
        txt_length_cache[txt] = lengths;
    }
    return lengths;
}

function substring_width(t, txt, x, lengths) {
    let w = lengths[x - 1];
    if (w == -1) {
        t.style["display"] = "block";
        if (t.textContent.length < (x + 2)) {
            t.textContent = txt.substring(0, x + 1);
        }
        w = t.getSubStringLength(0, x);
        lengths[x - 1] = w;
    }
    return w;
}

function owner_resize(new_width) {
    document.firstElementChild.width.baseVal.value = new_width;
    document.firstElementChild.viewBox.baseVal.width = new_width;

    let help = document.getElementById("help");
    if (help != null) {
        help.parentElement.x.baseVal.value = new_width - help.firstElementChild.width.baseVal.value - xpad
    }
}

var help_strings = [];

function graph_help_entry(e, i, key, description) {
    let y = (fg_frameheight * 2) + (i + 1) * fg_frameheight * 1.5;

    let box = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    box.x.baseVal.value = xpad;
    box.y.baseVal.value = y;
    box.width.baseVal.value = fg_frameheight * 2;
    box.height.baseVal.value = fg_frameheight;
    box.style.fill = "white";
    box.style.stroke = "black";
    box.style["stroke-width"] = 0.5;
    box.rx.baseVal.value = 2;
    box.ry.baseVal.vlaue = 2;

    let label = document.createElementNS("http://www.w3.org/2000/svg", "text");
    label.style.textAnchor = "middle";
    label.setAttribute("x", xpad + fg_frameheight);
    label.setAttribute("y", y - 5 + fg_frameheight);
    label.style.fontSize = fontSize;
    label.style.fontFamily = "Verdana";
    label.style.fill = "rgb(0, 0, 0)";
    label.textContent = key;

    let text = document.createElementNS("http://www.w3.org/2000/svg", "text");
    text.className.baseVal = "label";
    text.style.textAnchor = "left";
    text.setAttribute("x", xpad + fg_frameheight * 3);
    text.setAttribute("y", y - 5 + fg_frameheight);
    text.style.fontSize = fontSize;
    text.style.fontFamily = "Verdana";
    text.style.fill = "rgb(0, 0, 0)";
    text.textContent = description;

    e.appendChild(box);
    e.appendChild(label);
    e.appendChild(text);
}

function graph_popup_fix_width(e, right_justify) {
    let labels = e.getElementsByClassName("label");
    let max_label_end = 250;
    for (const label of labels) {
        let label_end = label.x.baseVal[0].value + label.getSubStringLength(0, label.textContent.length) + xpad;
        if (label_end > max_label_end) {
            max_label_end = label_end;
        }
    }
    let titles = e.getElementsByClassName("title");
    for (const title of titles) {
        title.x.baseVal.value = max_label_end / 2;
    }
    let popup = e.getElementsByClassName("popup")[0];
    popup.width.baseVal.value = max_label_end;
    if (right_justify) {
        e.parentElement.x.baseVal.value = fg_width - max_label_end - xpad;
    }
    graph_ensure_space();
};

function graph_register_handler(key, description, action) {
    window.addEventListener("keydown", function (e) {
        if (e.key == key && !e.isComposing && !e.altKey && !e.ctrlKey && !e.metaKey) {
            e.preventDefault();
            action();
        }
    });
    help_strings.push([key, description]);
}

function graph_ensure_space() {
    let svg = document.firstElementChild;
    let maxY = 0;
    for (const e of svg.getElementsByTagName("svg")) {
        if (svg.style["display"] != "none") {
            let y = e.y.baseVal.value + e.height.baseVal.value;
            if (y > maxY) {
                maxY = y;
            }
        }
    }
    svg.height.baseVal.value = maxY;
    svg.viewBox.baseVal.height = maxY;
}
