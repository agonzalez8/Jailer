/*
 * Copyright 2007 - 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sf.jailer.ui.pathfinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.sf.jailer.datamodel.Association;
import net.sf.jailer.datamodel.DataModel;
import net.sf.jailer.datamodel.Table;
import net.sf.jailer.util.Pair;

/**
 * Union of equi-length paths as directed graph.
 * 
 * @author Ralf Wisser
 */
public class PathGraph {

	private final Table source;
	private final Table destination;
	
	private Map<Table, Node> nodePerTable = new HashMap<Table, Node>();
	private Map<Pair<Node, Node>, EdgeType> edgeTypes = new HashMap<Pair<Node, Node>, EdgeType>();

	public enum EdgeType { PARENT, CHILD, ASSOCIATION };
	
	public class Node {
		Table table;
		int column;
		Set<Node> next = new HashSet<Node>();
		Set<Node> prev = new HashSet<Node>();

		public String toString() {
			return table.getName() + ":" + column;
		}

		public void collectPrevClosure(Set<Table> closure) {
			if (!closure.contains(table)) {
				closure.add(table);
				for (Node p: prev) {
					p.collectPrevClosure(closure);
				}
			}
		}

		public void collectNextClosure(Set<Table> closure) {
			if (!closure.contains(table)) {
				closure.add(table);
				for (Node p: next) {
					p.collectNextClosure(closure);
				}
			}
		}
	}

	/**
	 * Gets the node of a given table.
	 * 
	 * @param table the table
	 * @return node representing table
	 */
	public Node getNode(Table table) {
		return nodePerTable.get(table);
	}

	/**
	 * Gets all nodes on a given column.
	 * 
	 * @param column the column
	 * @return all nodes on a given column
	 */
	public List<Node> getNodes(int column) {
		List<Node> result = new ArrayList<Node>();
		for (Entry<Table, Node> e:nodePerTable.entrySet()) {
			if (e.getValue().column == column) {
				result.add(e.getValue());
			}
		}
		return result;
	}

	/**
	 * Gets edge-type of a given edge.
	 * 
	 * @param from left node of edge
	 * @param to right node of edge
	 * @return edge type 
	 */
	public EdgeType getEdgeType(Node from, Node to) {
		Pair<Node, Node> edgeKey = new Pair<Node, Node>(from, to);
		return edgeTypes.get(edgeKey);
	}

	/**
     * Resets the graph to an empty graph.
     */
	private void reset() {
		nodePerTable.clear();
		edgeTypes.clear();
	}
	
	/**
     * Returns <code>true</code> if this graph is empty.
     */
	public boolean isEmpty() {
		return nodePerTable.isEmpty();
	}
    
    /**
     * Creates new Graph.
     * @param excludedTables 
     * @param pathStations 
     */
    public PathGraph(DataModel dataModel, Table source, Table destination, Set<Table> excludedTables, List<Table> pathStations) {
    	this.source = source;
    	this.destination = destination;
    	
    	createGraph(excludedTables, pathStations);
    	Node destNode = nodePerTable.get(this.destination);
    	if (destNode == null) {
    		reset();
    		return;
    	}
    	Set<Table> destClosure = new HashSet<Table>();
    	destNode.collectPrevClosure(destClosure);
    	Set<Table> excluded = new HashSet<Table>(dataModel.getTables());
    	excluded.removeAll(destClosure);
    	excluded.addAll(excludedTables);
    	createGraph(excluded, pathStations);
    }

    private void createGraph(Set<Table> excluded, List<Table> pathStations) {
    	reset();

    	Set<Table> currentColumn = new HashSet<Table>();
    	currentColumn.add(source);
		int column = 0;
    	Node node = new Node();
    	node.table = source;
    	node.column = column;
		nodePerTable.put(source, node);
		List<Table> stations = new LinkedList<Table>(pathStations);
		stations.remove(source);
		stations.remove(destination);
		stations.removeAll(excluded);
    	do {
			if (!stations.isEmpty()) {
				Table nextStat = stations.get(0);
				if (currentColumn.contains(nextStat)) {
					currentColumn.clear();
					currentColumn.add(nextStat);
					stations.remove(0);
				}
			}
    		Set<Table> nextColumn = new HashSet<Table>();
    		for (Table table: currentColumn) {
    			node = nodePerTable.get(table);
				for (Association a: table.associations) {
					if (!a.isIgnored()) {
						Table dest = a.destination;
						if (excluded.contains(dest)) {
							continue;
						}
						Node newNode = nodePerTable.get(dest);
						if (newNode != null && newNode.column != column + 1) {
							continue;
						}
						nextColumn.add(dest);
						EdgeType type;
						if (a.isInsertDestinationBeforeSource()) {
							type = EdgeType.PARENT;
						} else if (a.isInsertSourceBeforeDestination()) {
							type = EdgeType.CHILD;
						} else {
							type = EdgeType.ASSOCIATION;
						}
						if (newNode == null) {
							newNode = new Node();
							newNode.table = dest;
							newNode.column = column + 1;
							nodePerTable.put(dest, newNode);
						}
						Pair<Node, Node> edgeKey = new Pair<Node, Node>(node, newNode);
						EdgeType oldType = edgeTypes.get(edgeKey);
						if (oldType != null && !type.equals(oldType)) {
							type = EdgeType.ASSOCIATION;
						}
						edgeTypes.put(edgeKey, type);
						node.next.add(newNode);
						newNode.prev.add(node);
					}
    			}
    		}
    		++column;
        	currentColumn = nextColumn;
    	} while (!currentColumn.isEmpty());
    }

}