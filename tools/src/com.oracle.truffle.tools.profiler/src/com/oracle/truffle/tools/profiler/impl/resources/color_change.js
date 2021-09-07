// Cycle through grpah coloring.

var color_type = "fg";
let legend_state = false;

function color_cycle() {
    let legend_visible = legend_state
    if (legend_visible) {
        color_legend(); // Hide the old legend.
    }
    if (color_type == "fg") {
        color_type = "bl";
    } else if (color_type == "bl") {
        color_type = "bc";
    } else {
        color_type = "fg";
    }
    update_color(color_type);
    if (legend_visible) {
        color_legend(); // Show the old legend.
    }
}

function update_color(color_type) {
    fg_update_color(color_type);
    h_update_color(color_type);
}

function color_for_name(language_index, name) {
    return colorData[language_index][name];
}

function color_for_compilation(interpreted, compiled) {
    let total = compiled + interpreted;
    let h = total == 0 ? 0.0 : (2.0 / 3.0) * (interpreted / total);
    let h6 = h * 6;
    let s = 0.8;
    let v = 0.8;
    let c = s * v;
    let x = c * (1 - Math.abs((h6) % 2 - 1));
    let m = v - c;
    let rprime;
    let gprime;
    let bprime;

    if (h6 < 1) {
        rprime = c;
        gprime = x;
        bprime = 0;
    } else if (h6 < 2) {
        rprime = x;
        gprime = c;
        bprime = 0;
    } else if (h6 < 3) {
        rprime = 0;
        gprime = c;
        bprime = x;
    } else if (h6 < 4) {
        rprime = 0;
        gprime = x;
        bprime = c;
    } else if (h6 < 5) {
        rprime = x;
        gprime = 0;
        bprime = c;
    } else {
        rprime = c;
        gprime = 0;
        bprime = x;
    }

    let r = ((rprime + m) * 255);
    let g = ((gprime + m) * 255);
    let b = ((bprime + m) * 255);
    return "rgb(" + r.toFixed() + ", " + g.toFixed() + ", " + b.toFixed() + ")";
}

function color_create_legend() {
    let svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    let e = document.createElementNS("http://www.w3.org/2000/svg", "g");
    e.id = "legend_" + color_type;

    let r = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    svg.x.baseVal.value = xpad;
    svg.y.baseVal.value = 50;
    r.x.baseVal.value = 0;
    r.y.baseVal.value = 0;
    r.width.baseVal.value = 250;
    r.style.fill = "white";
    r.style.stroke = "black";
    r.style["stroke-width"] = 2;
    r.rx.baseVal.value = 2;
    r.ry.baseVal.vlaue = 2;

    let t = document.createElementNS("http://www.w3.org/2000/svg", "text");
    t.style.textAnchor = "middle";
    t.setAttribute("x", 125);
    t.setAttribute("y", fg_frameheight * 2);
    t.style.fontSize = fontSize * 1.5;
    t.style.fontFamily = "Verdana";
    t.style.fill = "rgb(0, 0, 0)";
    t.textContent = "Legend";

    e.appendChild(r);
    e.appendChild(t);
    e.style.display = "none";
    svg.appendChild(e);

    let entry_count = 0;
    if (color_type == "fg") {
        let color_name = Object.getOwnPropertyNames(colorData[0])[0];
        let color = colorData[0][color_name];
        color_legend_entry(e, entry_count, color, "Sample colors are unique to functions");

        entry_count++;
    } else if (color_type == "bl") {
        for (let i = 0; i < languageNames.length; i++) {
            if (languageNames[i] != "") {
                let color_name = Object.getOwnPropertyNames(colorData[i])[0];
                let color = colorData[i][color_name];
                color_legend_entry(e, entry_count, color, languageNames[i]);

                entry_count++;
            }
        }
    } else if (color_type == "bc") {
        for (let i = 0; i <= 10; i++) {
            let color = color_for_compilation(10 - i, i);
            let text = (i * 10) + "% samples were compiled.";

            color_legend_entry(e, i, color, text);
        }

        entry_count = 11;
    }

    r.height.baseVal.value = (fg_frameheight * 2.5) + (entry_count + 1) * fg_frameheight * 1.5;
    color_insert_legend(svg);
    return e;
}

function color_insert_legend(e) {
    let help = document.getElementById("help");
    if (help == null) {
        document.firstElementChild.appendChild(e);
    } else {
        document.firstElementChild.insertBefore(e, help.parentElement);
    }
}

function color_legend_entry(e, i, color, text) {
    let y = (fg_frameheight * 2) + (i + 1) * fg_frameheight * 1.5;

    let box = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    box.x.baseVal.value = xpad;
    box.y.baseVal.value = y;
    box.width.baseVal.value = fg_frameheight * 2;
    box.height.baseVal.value = fg_frameheight;
    box.style.fill = color;
    box.style.stroke = "black";
    box.style["stroke-width"] = 0.5;
    box.rx.baseVal.value = 2;
    box.ry.baseVal.vlaue = 2;

    let label = document.createElementNS("http://www.w3.org/2000/svg", "text");
    label.style.textAnchor = "left";
    label.setAttribute("x", xpad + fg_frameheight * 3);
    label.setAttribute("y", y - 5 + fg_frameheight);
    label.style.fontSize = fontSize;
    label.style.fontFamily = "Verdana";
    label.style.fill = "rgb(0, 0, 0)";
    label.textContent = text;

    e.appendChild(box);
    e.appendChild(label);
}

function color_legend() {
    let legend_id = "legend_" + color_type;
    let e = document.getElementById(legend_id);
    if (e == null) {
        e = color_create_legend();
    }
    if (e != null) {
        if (e.style["display"] == "none") {
            e.style["display"] = "block";
        } else {
            e.style["display"] = "none";
        }
    }
    legend_state = !legend_state;
}

// C for color cycle.
graph_register_handler("c", "Cycle through graph colorings", color_cycle);
graph_register_handler("l", "Toggle legend display", color_legend);
