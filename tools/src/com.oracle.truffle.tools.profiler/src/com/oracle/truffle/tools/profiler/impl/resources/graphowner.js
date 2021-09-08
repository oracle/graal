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

// highlighting and info text

function s(node) {        // show
    let info = title(node);
    flamegraph_details.nodeValue = "function: " + info;
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

function find_sample_in_tree(tree, id, parents, unrelated) {
    if (tree.id == id) {
        return tree;
    } else if (tree.hasOwnProperty('s')) {
        parents.push(tree)
        let children = tree.s;
        for (let i = 0; i < children.length; i++) {
            if (children[i].id == id) {
                for (let j = 0; j < i; j++) {
                    unrelated.push(children[j]);
                }
                for (let j = i + 1; j < children.length; j++) {
                    unrelated.push(children[j]);
                }
                return children[i];
            } else if(children[i].id > id) {
                for (let j = 0; j < i - 1; j++) {
                    unrelated.push(children[j]);
                }
                for (let j = i; j < children.length; j++) {
                    unrelated.push(children[j]);
                }
                return find_sample_in_tree(children[i - 1], id, parents, unrelated);
            }
        }
        for (let j = 0; j < children.length - 1; j++) {
            unrelated.push(children[j]);
        }
        return find_sample_in_tree(children[children.length - 1], id, parents, unrelated);
    }
    return null;
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
    let result =  [find_sample_in_tree(profileData, id, parents, unrelated), parents, unrelated];
    return result
}

function sample_for_id(id) {
    return find_sample_in_tree(profileData, id, [], []);
}

function depth_for_id_in_tree(tree, id) {
    if (tree.id == id) {
        return 0;
    } else if (tree.hasOwnProperty('s')) {
        let children = tree.s;
        for (let i = 0; i < children.length; i++) {
            if (children[i].id == id) {
                return 1;
            } else if(children[i].id > id) {
                return 1 + depth_for_id_in_tree(children[i - 1], id);
            }
        }
        return 1 + depth_for_id_in_tree(children[children.length - 1], id);
    }
    return -1;
}

function depth_for_sample(sample) {
    if (!sample.hasOwnProperty("depth")) {
        sample.depth = depth_for_id_in_tree(profileData, sample.id);
    }
    return sample.depth;
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
                if (result.hasOwnProperty("s")) {
                    let children = result.s.slice();
                    children.reverse();

                    for(const child of children) {
                        if (!child.hasOwnProperty("depth")) {
                            child.depth = rdepth + 1;
                        }
                        stack.push(child);
                    }
                }
                return {value: result, done: false}
            }
        }
    }
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

function name_for_sample(sample) {
    return profileNames[sample.n];
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
var help_state = false;

function graph_create_help() {
    let svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    let e = document.createElementNS("http://www.w3.org/2000/svg", "g");
    e.id = "help";

    let r = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    svg.y.baseVal.value = 50;
    r.className.baseVal = "popup";
    r.x.baseVal.value = 0;
    r.y.baseVal.value = 0;
    r.width.baseVal.value = 250;
    r.style.fill = "white";
    r.style.stroke = "black";
    r.style["stroke-width"] = 2;
    r.rx.baseVal.value = 2;
    r.ry.baseVal.vlaue = 2;

    let t = document.createElementNS("http://www.w3.org/2000/svg", "text");
    t.className.baseVal = "title";
    t.style.textAnchor = "middle";
    t.setAttribute("y", fg_frameheight * 2);
    t.style.fontSize = fontSize * 1.5;
    t.style.fontFamily = "Verdana";
    t.style.fill = "rgb(0, 0, 0)";
    t.textContent = "Keyboard shortcut";

    e.appendChild(r);
    e.appendChild(t);
    e.style.display = "none";
    svg.appendChild(e);

    let entry_count = 0;

    for (; entry_count < help_strings.length; entry_count++) {
        graph_help_entry(e, entry_count, help_strings[entry_count][0], help_strings[entry_count][1]);
    }

    svg.x.baseVal.value = document.firstElementChild.width.baseVal.value - 250 - xpad;
    t.setAttribute("x", 250 / 2);
    r.height.baseVal.value = (fg_frameheight * 2.5) + (entry_count + 1) * fg_frameheight * 1.5;
    document.firstElementChild.appendChild(svg);
    return e;

}

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
    let title = e.getElementsByClassName("title")[0];
    title.x.baseVal.value = max_label_end / 2;
    let popup = e.getElementsByClassName("popup")[0];
    popup.width.baseVal.value = max_label_end;
    if (right_justify) {
        e.parentElement.x.baseVal.value = fg_width - max_label_end - xpad;
    }
};

function graph_help() {
    let e = document.getElementById("help");
    if (e == null) {
        e = graph_create_help();
    }
    if (e != null) {
        if (e.style["display"] == "none") {
            e.style["display"] = "block";
            graph_popup_fix_width(e, true);
        } else {
            e.style["display"] = "none";
        }
    }
    help_state = !help_state;
}

function graph_register_handler(key, description, action) {
    window.addEventListener("keydown", function (e) {
        if (e.key == key && !e.isComposing && !e.altKey && !e.ctrlKey && !e.metaKey) {
            e.preventDefault();
            action();
        }
    });
    help_strings.push([key, description]);
}

graph_register_handler("?", "Display keyboard shortcuts", graph_help);
