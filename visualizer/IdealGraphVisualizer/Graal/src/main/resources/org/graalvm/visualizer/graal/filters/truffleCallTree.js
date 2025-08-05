// Truffle - call tree coloring
var red = new Color(240, 59, 32);
var lightGreen = new Color(173, 221, 142);

colorize("state", ".*Cutoff", red);
colorize("state", ".*Inlined", lightGreen);
