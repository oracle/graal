insight.on('enter', (ctx, frames) => {
  if (ctx.line !== 3) return;
  let log = `at ${ctx.source.name}:${ctx.line} `;
  ctx.iterateFrames((at, locals) => {
      log += JSON.stringify(locals);
  });
  print(log);
}, {
  statements: true,
  sourceFilter: (s) => s.name.indexOf('JsTypesMod') >= 0
});
