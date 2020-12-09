insight.on("return", function(ctx, frame) {
  if (ctx.line != 8 || frame.n != 1) {
      return;
  }
  print("dumping locals");
  ctx.iterateFrames(frame, (frameCtx, frameVars) => {
      for (let p in frameVars) {
          print(`    at ${frameCtx.name} (${frameCtx.source.name}:${frameCtx.line}:${frameCtx.column}) ${p} has value ${frameVars[p]}`);
      }
  });
  print("end of locals");
}, {
  roots: true
});
