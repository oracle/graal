
var InputGraph = {};

/**
 * Returns maps of all nodes with incoming edges. Keys of the hash
 * are InputNodes, value of hash entry is a list of incoming node edges.
 * @returns {Object}
 */
InputGraph.prototype.findAllIngoingEdges = function() {
    var n = new InputNode();
    var e = [ new InputEdge() ];
    var m = {};
    m[n] = e;
    return m;
};

InputGraph.prototype.findAllOutgoingEdges = function() {};

/**
 * Finds "root" nodes of the graph, nodes with no incoming edges
 * @return {Array}
 */
InputGraph.prototype.findRootNodes = function() {
    var n = new InputNode();
    var a = [ n ];
    return a;
};

/**
 * Returns block that contains the nodeId. Returns undefined, if the
 * block does not exist.
 * @param {Number}
 * @returns {InputBlock}
 */
InputGraph.prototype.getBlock(nodeId) = function() {
};

