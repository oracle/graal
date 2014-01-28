function ret(a) { return a; } 
function dub(a) { return a * 2; } 
function inc(a) { return a + 1; } 
function dec(a) { return a - 1; } 
function call(f, v) { return f(v); }
 
function main() {  
  print(ret(42));
  print(dub(21));
  print(inc(41));
  print(dec(43));
  print(call(ret, 42));
  print(call(dub, 21));
  print(call(inc, 41));
  print(call(dec, 43));
}  
