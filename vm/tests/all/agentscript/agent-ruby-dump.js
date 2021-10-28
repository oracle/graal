/* global insight */

insight.on('enter', (ctx, frame) => {
  print(`dumping state of Ruby memory when executing ${ctx.name}`);
  heap.dump({
      format: '1.0',
      events: [{
        stack: [
            {
                at: ctx,
                frame: frame
            }
        ]
      }],
      depth: 3
    });
}, {
  roots: true,
  statements: true,
  rootNameFilter: `.*welcome`
});
