/* global agent */

var previousCharacters;

insight.on('enter', function(ctx, frame) {

    if (ctx.line !== ctx.startLine) {
        throw `Unexpected lines ${ctx.line} vs. ${ctx.startLine}`;
    }

    if (ctx.column !== ctx.startColumn) {
        throw `Unexpected start column ${ctx.column} vs. ${ctx.startColumn}`;
    }

    if (ctx.line !== ctx.endLine) {
        throw `Only one-line expressions expected ${ctx.line} vs. ${ctx.endLine} for ${ctx.characters}`;
    }

    let length = ctx.endColumn - ctx.startColumn + 1;
    console.log(`-> ${ctx.name} contains ${ctx.characters} at ${ctx.line}:${ctx.column} length ${length}`);

    if (ctx.characters.length !== length) {
        console.log(ctx);
        throw `Unexpected length ${ctx.characters.length} vs. ${length} for location: ${ctx.characters}`;
    }

    if (ctx.source.name !== 'fib.js') {
        throw `Unexpected source name ${ctx.source.name}`;
    }

    if (previousCharacters && previousCharacters !== ctx.source.characters) {
        throw `Unexpected content in name ${ctx.source.name}: ${ctx.source.characters}`;
    }
    previousCharacters = ctx.source.characters;
}, {
    expressions: true,
    sourceFilter: (source) => source.name === 'fib.js'
});

insight.on('return', function(ctx, frame) {
    console.log(`<- ${ctx.characters}`);
    if (previousCharacters && previousCharacters !== ctx.source.characters) {
        throw `Unexpected content in name ${ctx.source.name}: ${ctx.source.characters}`;
    }
}, {
    expressions: true
});
