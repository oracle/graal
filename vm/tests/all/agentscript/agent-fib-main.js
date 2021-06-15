function minusOne(n) {
  return n - 1;
}
function minusTwo(n) {
  return n - 2;
}
function fib(n) {
  if (n < 1) return 0;
  else if (n < 2) return 1;
  else return fib(minusOne(n)) + fib(minusTwo(n));
}

function main() {
  var prefix = "Three is the result ";
  var fib4 = fib(4);
  print(prefix + fib4);
}
main();
