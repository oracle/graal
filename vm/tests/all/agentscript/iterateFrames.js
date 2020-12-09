insight.on("return", function(ctx, frame) {
  if (ctx.line != 8 || frame.n != 1) {
      return;
  }
  try {
      throw new Error("This is the stack:");
  } catch (e) {
      print(e.stack);
  }
  let depth = 0;
  ctx.iterateFrames(frame, (frameCtx, frameVars) => {
      depth++;
      for (let p in frameVars) {
          print(`    #${depth} frame: ${p} has value ${frameVars[p]}`);
      }
  });
  print("end of locals");
}, {
  roots: true
});
