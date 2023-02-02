
/* global insight */

insight.on('enter', (ctx, frame) => {
    if (frame.node.name) {
        print(`text: ${frame.node.name.getText()} kind: ${frame.node.kind}`);
    } else {
        print(`kind: ${frame.node.kind}`);
    }
}, {
    roots: true,
    rootNameFilter: 'searchAst'
});

insight.on('enter', (ctx, frame) => {
    print(`Generating ${frame.name}`);
}, {
    roots: true,
    rootNameFilter: 'generateJavaSourceFile'
});


