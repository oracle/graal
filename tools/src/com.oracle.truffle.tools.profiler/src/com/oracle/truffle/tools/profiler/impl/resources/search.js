var searchbtn, matchedtxt;
// ctrl-F for search
window.addEventListener("keydown",function (e) {
    if (e.keyCode === 114 || (e.ctrlKey && e.keyCode === 70)) {
        e.preventDefault();
        search_prompt();
    }
})

function search_prompt() {
    if (search_matches.length == 0) {
        var term = prompt("Enter a search term (regexp " +
                                                 "allowed, eg: ^ext4_)", "");
        if (term != null) {
            search(term);
        }
    } else {
        reset_search();
        searchbtn.style["opacity"] = "0.8";
        searchbtn.firstChild.nodeValue = "Search"
        matchedtxt.style["opacity"] = "0.0";
        matchedtxt.firstChild.nodeValue = ""
    }
}
