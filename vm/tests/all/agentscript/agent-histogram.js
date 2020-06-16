var map = new Map();

function dumpHistogram() {
    print("==== Histogram ====");
    var digits = 3;
    Array.from(map.entries()).sort((one, two) => two[1] - one[1]).forEach(function (entry) {
        var number = entry[1].toString();
        if (number.length >= digits) {
            digits = number.length;
        } else {
            number = Array(digits - number.length + 1).join(' ') + number;
        }
        print(`${number} calls to ${entry[0]}`);
    });
    print("===================");
}

insight.on('enter', function(ev) {
    var cnt = map.get(ev.name);
    if (cnt) {
        cnt = cnt + 1;
    } else {
        cnt = 1;
    }
    map.set(ev.name, cnt);
}, {
    roots: true
});

insight.on('close', dumpHistogram);
