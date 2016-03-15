// $Id$
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

/*
 A set of page(s) of similar size (and other properties), which are collectively
 (sparsely, if necessary) rendered in order to provide a "merged" preview, a
 single visible pageset.  What a user resizes.
 Also (in solo-page form) used as the element by which each page of a
 file being ingested is considered for membership in such a set.
 */
public class PageCluster implements Comparable<PageCluster> {

	private static final int MERGE_VARIABILITY = 20;
	private static final int MAX_MERGE_PAGES = 15;

	private final List<Integer> memberPgNums;
	// Every memberPgNum shares the same <roundedPgWidth,roundedPgHeight> attribute.
	// The <pageWidth,PageHeight> attribute of each memberPgNum may not be identical to those of its mates.
	private final int roundedPgWidth;
	private final int roundedPgHeight;

	private static int roundPgDim( int dim ) {
		int tmp = dim / MERGE_VARIABILITY;
		return tmp * MERGE_VARIABILITY;
	}

	private final List<Float[]> cropRatiosList = new ArrayList<>();
	private boolean excluded = false;
	private ClusterImageData imageData;
	private final boolean evenPage;

	public PageCluster(final boolean isEvenPage, final int pageWidth, final int pageHeight,
			final boolean excluded, final int pageNumber) {
		super();
		this.roundedPgWidth  = roundPgDim( pageWidth  );
		this.roundedPgHeight = roundPgDim( pageHeight );
		this.evenPage = isEvenPage;
		this.excluded = excluded;
		this.previewPgNums = new ArrayList<>();
		this.memberPgNums = new ArrayList<>();
		this.memberPgNums.add(pageNumber);
	}

	public String createToolTipText() {
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append(isEvenPages() ? "Even " : "Odd ").append("page<br>");
		sb.append(getMemberPgNums().size()); sb.append(" pages: ");
		int pagecounter = 0;
		for (Integer pgNum : getMemberPgNums()) {
			sb.append(pgNum); sb.append(" ");
			if (pagecounter++ > 10) {
				pagecounter = 0;
				sb.append("<br>");
			}
		}
		sb.append("</html>");
		return sb.toString();
	}



	public final ClusterImageData getImageData() {
		if (imageData == null) {
			imageData = new ClusterImageData(roundedPgWidth, roundedPgHeight, previewPgNums.size());
		}
		return imageData;
	}

	/**
	 * Returns the ratio to crop the page.
	 * 
	 * returns the ratio to crop the page x1,y1,x2,y2, origin = bottom left x1:
	 * from left edge to left edge of crop rectange y1: from lower edge to lower
	 * edge of crop rectange x2: from right edge to right edge of crop rectange
	 * y2: from top edge to top edge of crop rectange
	 * 
	 * @return
	 */
	public final List<Float[]> getCropRatioList() {
		return cropRatiosList;
	}

	public final void setRatiosList(final List<Float[]> ratiosList) {
		clearRatios();
		ratiosList.forEach(this::addCropRatio);
	}

	public final void clearRatios() {
		cropRatiosList.clear();
	}

	public final void addCropRatio(final Float[] ratios) {
		// check if already in
		if (!cropRatiosList.contains(ratios)) {
			cropRatiosList.add(ratios);
		}
	}

	public final boolean isClusterNearlyEqual(final PageCluster other) {
		return evenPage == other.evenPage
		&&	!excluded
		&&	!other.excluded
		&&  roundedPgHeight == other.roundedPgHeight
		&&	roundedPgWidth  == other.roundedPgWidth
		;
	}

	public final void incorporate(final PageCluster other) {
		memberPgNums.addAll(other.getMemberPgNums());
	}

	public final boolean isEvenPages() {
		return evenPage;
	}

	private List<Integer> previewPgNums;
	public final void designatePreviewPages() {
		if (memberPgNums.size() < MAX_MERGE_PAGES) {
			// use all pages
			previewPgNums = memberPgNums;
		} else {
			// use an equal distribution
			float stepWidth = (float) memberPgNums.size() / MAX_MERGE_PAGES;
			float totalStepped = 0;
			for (int i = 0; i < MAX_MERGE_PAGES; i++) {
				previewPgNums.add(memberPgNums.get(new Double(Math.floor(totalStepped)).intValue()));
				totalStepped += stepWidth;
			}
		}
	}

	public final List<Integer> getMemberPgNums() {
		return memberPgNums;
	}

	public final List<Integer> getPreviewPgNums() {
		return previewPgNums;
	}

	private int getMinPage() {
		int small = Integer.MAX_VALUE;
		for (Integer tmp : memberPgNums) {
			small = Math.min(small, tmp);
		}
		return small;
	}

	public final int compareTo(final PageCluster that) {
		return this.getMinPage() - that.getMinPage();
	}
}
