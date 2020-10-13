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
    let c = child(e, "title");
    if (c == null) {
        let id = e.getAttribute("id").substring(2);
        c = sample_for_id(id);
        return c.n;
    } else {
        return c.firstChild.nodeValue
    }
}

function function_name(e) {
    return title(e).replace(/\\([^(]*\\)$/,"");
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

    if (x > 2) {
        t.style["display"] = "block";
        t.textContent = txt.substring(0,x - 2) + "..";
    } else {
        t.style["display"] = "none";
    }
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
        t.textContent = txt;
        w = t.getSubStringLength(0, x);
        lengths[x - 1] = w;
    }
    return w;
}
