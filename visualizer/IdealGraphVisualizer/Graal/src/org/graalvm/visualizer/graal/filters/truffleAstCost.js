// Truffle - AST cost coloring
var gray = new Color(220, 220, 220);
var lightGreen = new Color(173, 221, 142);
var yellow = new Color(255, 237, 160);
var red = new Color(240, 59, 32);
var black = new Color(50, 50, 50);
colorize("cost", "NodeCost.NONE", gray);
colorize("cost", "NodeCost.MONOMORPHIC", lightGreen);
colorize("cost", "NodeCost.POLYMORPHIC", yellow);
colorize("cost", "NodeCost.UNINITIALIZED", red);
colorize("cost", "NodeCost.MEGAMORPHIC", black);

var lightBlue = new Color(200, 200, 250);
colorize("label", "OptimizedDirectCall", lightBlue);

var darkBlue = new Color(10, 10, 250);
colorize("label", "Probe", darkBlue);
