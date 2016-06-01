function loop(n, obj, name) {
  obj[name] = 0;  
  while (obj[name] < n) {  
    obj[name] = obj[name] + 1;  
  }  
  return obj[name];
}  

function main() {
  i = 0;
  while (i < 20) {
    loop(1000, new(), "prop");
    i = i + 1;
  }
  println(loop(1000, new(), "prop"));  
}  
