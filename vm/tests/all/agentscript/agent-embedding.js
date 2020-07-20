insight.on('enter', function(ev, frame) {
    print(`calling ${ev.name} with ${frame.n}`);
}, {
    roots: true
});
