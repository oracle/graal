insight.on("return", function(ctx, frame) {
  if (ctx.line != 8 || frame.n != 1) {
      return;
  }
  print("dumping locals");
  ctx.iterateFrames((at, vars) => {
      for (let p in vars) {
          print(`    at ${at.name} (${at.source.name}:${at.line}:${at.column}) ${p} has value ${vars[p]}`);
      }
  });
  print("end of locals");
}, {
  roots: true
});
