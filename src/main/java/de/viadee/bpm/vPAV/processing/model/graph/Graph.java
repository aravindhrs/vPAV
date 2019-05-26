/**
 * BSD 3-Clause License
 *
 * Copyright © 2019, viadee Unternehmensberatung AG
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.processing.model.graph;

import de.viadee.bpm.vPAV.constants.BpmnConstants;
import de.viadee.bpm.vPAV.processing.code.flow.BpmnElement;
import de.viadee.bpm.vPAV.processing.model.data.Anomaly;
import de.viadee.bpm.vPAV.processing.model.data.AnomalyContainer;
import de.viadee.bpm.vPAV.processing.model.data.InOutState;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * A class for a directed graph. Implemented by an adjacency list representation
 * of a graph.
 */

public class Graph {

	private String processId;

	private LinkedHashMap<BpmnElement, List<Edge>> adjacencyListSuccessor; // [vertices] -> [edge]

	private LinkedHashMap<BpmnElement, List<Edge>> adjacencyListPredecessor; // [vertices] -> [edge]

	public LinkedHashMap<BpmnElement, VertexInfo> getVertexInfo() {
		return vertexInfo;
	}

	private LinkedHashMap<BpmnElement, VertexInfo> vertexInfo; // [vertex] -> [info]

	private Collection<BpmnElement> startNodes = new ArrayList<>();

	private Collection<BpmnElement> endNodes = new ArrayList<>();

	public Graph(final String processId) {
		this.processId = processId;
		this.adjacencyListSuccessor = new LinkedHashMap<>();
		this.adjacencyListPredecessor = new LinkedHashMap<>();
		this.vertexInfo = new LinkedHashMap<>();
	}

	public String getProcessId() {
		return processId;
	}

	public void addStartNode(final BpmnElement node) {
		startNodes.add(node);
	}

	public Collection<BpmnElement> getStartNodes() {
		return startNodes;
	}

	public void addEndNode(final BpmnElement node) {
		endNodes.add(node);
	}

	public Collection<BpmnElement> getEndNodes() {
		return endNodes;
	}

	public void addVertex(final BpmnElement v) {
		if (v == null) {
			throw new IllegalArgumentException("null");
		}

		adjacencyListSuccessor.put(v, new ArrayList<>());
		adjacencyListPredecessor.put(v, new ArrayList<>());
		vertexInfo.put(v, new VertexInfo(v));
	}

	public Collection<BpmnElement> getVertices() {
		return vertexInfo.keySet();
	}

	public Collection<List<Edge>> getEdges() {
		return adjacencyListSuccessor.values();
	}

	public LinkedHashMap<BpmnElement, List<Edge>> getAdjacencyListPredecessor() {
		return adjacencyListPredecessor;
	}

	public List<BpmnElement> getAdjacencyListPredecessor(final BpmnElement element) {
		return adjacencyListPredecessor.get(element).stream().map(Edge::getTo).collect(Collectors.toList());
	}

	public List<BpmnElement> getAdjacencyListSuccessor(final BpmnElement element) {
		return adjacencyListSuccessor.get(element).stream().map(Edge::getTo).collect(Collectors.toList());
	}

	public void addEdge(BpmnElement from, BpmnElement to, int weight) {
		// add successor
		List<Edge> edgeSuccessorList = adjacencyListSuccessor.get(from);
		if (edgeSuccessorList == null) {
			throw new IllegalArgumentException("source vertex not in graph");
		}

		Edge newSuccessorEdge = new Edge(from, to, weight);
		edgeSuccessorList.add(newSuccessorEdge);

		// add predecessor
		List<Edge> edgePredecessorList = adjacencyListPredecessor.get(to);
		if (edgePredecessorList == null) {
			throw new IllegalArgumentException("source vertex not in graph");
		}

		Edge newPredecessorEdge = new Edge(to, from, weight);
		edgePredecessorList.add(newPredecessorEdge);
	}

	public void removeEdge(BpmnElement from, BpmnElement to) {
		final List<Edge> edgeSuccessorList = adjacencyListSuccessor.get(from);
		Edge foundEdge = null;
		for (final Edge e : edgeSuccessorList) {
			if (e.getFrom().toString().equals(from.toString()) && e.getTo().toString().equals(to.toString())) {
				// delete
				foundEdge = e;
			}
		}
		edgeSuccessorList.remove(foundEdge);

		final List<Edge> edgePredecessorList = adjacencyListPredecessor.get(to);
		foundEdge = null;
		for (final Edge e : edgePredecessorList) {
			if (e.getTo().toString().equals(from.toString()) && e.getFrom().toString().equals(to.toString())) {
				// delete
				foundEdge = e;
			}
		}
		edgePredecessorList.remove(foundEdge);
	}

	public boolean hasEdge(BpmnElement from, BpmnElement to) {
		return getEdge(from, to) != null;
	}

	public Edge getEdge(BpmnElement from, BpmnElement to) {
		List<Edge> edgeList = adjacencyListSuccessor.get(from);
		if (edgeList == null) {
			throw new IllegalArgumentException("source vertex not in graph");
		}

		for (Edge e : edgeList) {
			if (e.getTo().equals(to)) {
				return e;
			}
		}

		return null;
	}

	/**
	 * get nodes with data flow anomalies
	 */
	public Map<BpmnElement, List<AnomalyContainer>> getNodesWithAnomalies() {

		final Map<BpmnElement, List<AnomalyContainer>> anomalies = new HashMap<>();
		for (final BpmnElement node : adjacencyListSuccessor.keySet()) {
			anomalies.putAll(node.getAnomalies());
		}
		return anomalies;
	}

	/**
	 * search all paths with variables, which has not been set
	 *
	 * source:
	 * http://codereview.stackexchange.com/questions/45678/find-all-paths-from-source-to-destination
	 */
	public List<Path> getAllInvalidPaths(final BpmnElement source, final AnomalyContainer anomaly) {
		return getAllInvalidPathsRecursive(source, anomaly, new LinkedList<>());
	}

	/**
	 * search all paths with variables, which has not been set (backward)
	 *
	 * source:
	 * http://codereview.stackexchange.com/questions/45678/find-all-paths-from-source-to-destination
	 *
	 */
	private List<Path> getAllInvalidPathsRecursive(final BpmnElement startNode, final AnomalyContainer anomaly,
			final LinkedList<BpmnElement> currentPath) {

		final List<Path> invalidPaths = new ArrayList<>();

		currentPath.add(startNode);

		final List<Edge> edges = this.adjacencyListPredecessor.get(startNode);

		final Map<String, InOutState> in = startNode.getIn();
		final Map<String, InOutState> out = startNode.getOut();

		final List<Path> returnPathsUrAnomaly = exitConditionUrAnomaly(startNode, anomaly, currentPath, invalidPaths,
				in, out);
		final List<Path> returnPathsDdDuAnomaly = exitConditionDdDuAnomaly(startNode, anomaly, currentPath,
				invalidPaths, in);

		if (anomaly.getAnomaly() == Anomaly.UR && !in.containsKey(anomaly.getName())
				&& out.containsKey(anomaly.getName())) {
			return invalidPaths;
		} else if (returnPathsUrAnomaly != null) {
			return returnPathsUrAnomaly;
		} else if (returnPathsDdDuAnomaly != null) {
			return returnPathsDdDuAnomaly;
		}

		for (final Edge t : edges) {
			if (!currentPath.contains(t.getTo()) || t.getTo() == anomaly.getVariable().getElement()) {
				invalidPaths.addAll(getAllInvalidPathsRecursive(t.getTo(), anomaly, currentPath));
			}
		}

		currentPath.remove(startNode);

		return invalidPaths;
	}

	/**
	 * exit condition for path finding (ur anomaly)
	 *
	 */
	private List<Path> exitConditionUrAnomaly(final BpmnElement startNode, final AnomalyContainer anomaly,
			final LinkedList<BpmnElement> currentPath, final List<Path> invalidPaths, final Map<String, InOutState> in,
			final Map<String, InOutState> out) {

		// go back to the node, where the variable was deleted
		// or go back to the start
		if (anomaly.getAnomaly() == Anomaly.UR && (variableDeleted(anomaly, in, out)
				|| ((startNode.getBaseElement().getElementType().getTypeName().equals(BpmnConstants.START_EVENT)
						&& startNode.getBaseElement().getParentElement().getElementType().getTypeName()
								.equals(BpmnConstants.PROCESS))))) {

			final List<BpmnElement> newPath = new ArrayList<>(currentPath);
			invalidPaths.add(new Path(newPath));

			currentPath.remove(startNode);
			return invalidPaths;
		}
		return null;
	}

	/**
	 * is variable deleted
	 *
	 */
	private boolean variableDeleted(final AnomalyContainer anomaly, final Map<String, InOutState> in,
			final Map<String, InOutState> out) {

		return ((in.containsKey(anomaly.getName()) && in.get(anomaly.getName()) != InOutState.DELETED))
				&& (out.containsKey(anomaly.getName()) && out.get(anomaly.getName()) == InOutState.DELETED);
	}

	/**
	 * exit condition for path finding (du / dd anomaly)
	 *
	 */
	private List<Path> exitConditionDdDuAnomaly(final BpmnElement startNode, final AnomalyContainer anomaly,
			final LinkedList<BpmnElement> currentPath, final List<Path> invalidPaths, Map<String, InOutState> in) {

		// go back to the node where the element is defined
		// skip the startpoint
		if (startNode.defined().containsKey(anomaly.getName())
				&& (anomaly.getAnomaly() == Anomaly.DD || anomaly.getAnomaly() == Anomaly.DU)
				&& currentPath.size() > 1) {
			final List<BpmnElement> newPath = new ArrayList<>(currentPath);
			invalidPaths.add(new Path(newPath));

			currentPath.remove(startNode);
			return invalidPaths;
		}
		return null;
	}

	@Override
	public String toString() {
		Set<BpmnElement> keys = adjacencyListSuccessor.keySet();
		StringBuilder str = new StringBuilder("digraph G {\n");

		for (BpmnElement v : keys) {
			str.append(" ");

			List<Edge> edgeList = adjacencyListSuccessor.get(v);

			for (Edge edge : edgeList) {
				str.append(edge);
				str.append("\n");
			}
		}
		str.append("}");
		return str.toString();
	}

	protected final void clearVertexInfo() {
		for (VertexInfo info : this.vertexInfo.values()) {
			info.clear();
		}
	}

	/**
	 * generate intersection for variable maps and remind precedence rule for
	 * variable states
	 *
	 * @param mapA
	 *            mapA
	 * @param mapB
	 *            mapB
	 * @return intersection
	 */
	public static Map<String, InOutState> intersection(final Map<String, InOutState> mapA,
			final Map<String, InOutState> mapB) {
		final Map<String, InOutState> intersectionMap = new HashMap<>();
		final Set<String> variables = new HashSet<>();
		variables.addAll(mapA.keySet());
		variables.addAll(mapB.keySet());
		for (final String varName : variables) {
			if (mapA.containsKey(varName) && mapB.containsKey(varName)) {
				final InOutState state1 = mapA.get(varName);
				final InOutState state2 = mapB.get(varName);

				final InOutState intersectionElement = getStatePrecedence(state1, state2);
				intersectionMap.put(varName, intersectionElement);
			} else {
				mapA.remove(varName);
				mapB.remove(varName);
			}
		}
		return intersectionMap;
	}

	/**
	 * get union and remind precedence rule for variable states
	 *
	 * @param mapA
	 *            mapA
	 * @param mapB
	 *            mapB
	 * @return union
	 */
	public static Map<String, InOutState> unionWithStatePrecedence(final Map<String, InOutState> mapA,
			final Map<String, InOutState> mapB) {

		final Map<String, InOutState> unionMap = new HashMap<>();
		unionMap.putAll(mapA);
		unionMap.putAll(mapB);
		unionMap.putAll(intersection(mapA, mapB));

		return unionMap;

	}

	/**
	 * precedence rule for variable states
	 *
	 * 1) delete 2) read 3) define
	 *
	 */
	private static InOutState getStatePrecedence(final InOutState state1, final InOutState state2) {
		if (state1 == InOutState.DELETED || state2 == InOutState.DELETED) {
			return InOutState.DELETED;
		} else if (state1 == InOutState.READ || state2 == InOutState.READ) {
			return InOutState.READ;
		}
		return InOutState.DEFINED;
	}
}
