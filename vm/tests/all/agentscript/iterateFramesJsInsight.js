insight.on('enter', (ctx, frames) => {
  if (ctx.line !== 3) return;
  ctx.iterateFrames((at, locals) => {
      print(JSON.stringify(locals, null, 2));
  });
}, {
  statements: true,
  sourceFilter: (s) => s.name.indexOf('JsTypesMod') >= 0
});
