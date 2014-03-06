function ret(a) { return a; } 
function dub(a) { return a * 2; } 
function inc(a) { return a + 1; } 
function dec(a) { return a - 1; } 
function call(f, v) { return f(v); }
 
function main() {  
  println(ret(42));
  println(dub(21));
  println(inc(41));
  println(dec(43));
  println(call(ret, 42));
  println(call(dub, 21));
  println(call(inc, 41));
  println(call(dec, 43));
}  
