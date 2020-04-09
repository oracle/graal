var counter = 0;

function ping() {
    console.log(`Ping ${++counter}`);
    setTimeout(ping, 5000);
}
setTimeout(ping, 5000);

// define GraalVM Insight script via curl anytime later:
function connect() {
    const script = 'insight.on("enter", (ctx, frame) => {' +
            '  console.log("observing ping at " + frame.counter);' +
            '  if (frame.counter >= 5) process.exit(5);' +
            '}';
    const cmd = `curl --data '${script}, { roots: true, rootNameFilter: (n) => n === "ping" });' -X POST http://localhost:9999/`;
    console.log('Attaching');
    const { exec } = require('child_process');
    exec(cmd);
}
setTimeout(connect, 12500);
