// Cycle through grpah coloring.

var color_type = "fg";

function color_cycle() {
    if (color_type == "fg") {
        color_type = "bl";
    } else if (color_type == "bl") {
        color_type = "bc";
    } else {
        color_type = "fg";
    }
    update_color(color_type);
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

// ctrl-F for search
window.addEventListener("keydown",function (e) {
    if (e.key == "c") {
        e.preventDefault();
        color_cycle();
    }
})
