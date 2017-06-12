/**
 * Copyright 2010 Gerhard Aigner
 * 
 * This file is part of BRISS.
 * 
 * BRISS is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * BRISS is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * BRISS. If not, see http://www.gnu.org/licenses/.
 */
package at.laborg.briss.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClusterDefinition {
	/*
	   a list of PageClusters
	 */

	private final List<PageCluster> clusters = new ArrayList<>();

	public final PageCluster getClusterContainingPage(final int pgNum) {
		for (PageCluster cluster : clusters) {
			if (cluster.getMemberPgNums().contains(pgNum))
				return cluster;
		}
		return null;
	}

	public final List<PageCluster> getClusterList() {
		return clusters;
	}

	public final List<List<Float[]>> getAllRatios () {
		return getClusterList().stream().map(cluster -> new ArrayList<>(cluster.getCropRatioList())).collect(Collectors.toList());
	}

	public final void addOrMergeCluster(final PageCluster tmpCluster) {
		for (PageCluster cluster : clusters) {
			if (cluster.isClusterNearlyEqual(tmpCluster)) {
				cluster.incorporate(tmpCluster);
				return;
			}
		}
		clusters.add(tmpCluster);
	}

	public final void designatePreviewPages() {
		clusters.forEach(PageCluster::designatePreviewPages);
	}

	public final int getNrOfPagesToRender() {
		int size = 0;
		for (PageCluster cluster : clusters) {
			size += cluster.getPreviewPgNums().size();
		}
		return size;
	}
}