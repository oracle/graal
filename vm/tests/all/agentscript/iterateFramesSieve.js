/* global insight */

function dumpStack(ctx, frame) {
    if (frame.number > 1289500) {
        print(`Iterating the ${ctx.name} with ${frame.number} stack from Insight`);
        ctx.iterateFrames((at, frame) => {
            print(`        at <${at.source.language}> ${at.name}(${at.source.name}:${at.line}:${at.charIndex}-${at.charEndIndex - 1})`);
        });
        insight.off('enter', dumpStack);
        throw `Is the printed stack comparable with thrown exception?`;
    }
}

insight.on('enter', dumpStack, {
  roots: true,
  rootNameFilter: `Filter`
});
