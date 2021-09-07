var searchbtn, matchedtxt;
// ctrl-F for search
graph_register_handler("f", "Search or clear highlighted entries", search_prompt);

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
