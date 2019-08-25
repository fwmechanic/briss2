// $Id: SingleCluster.java 55 2011-02-22 21:45:59Z laborg $
/**
 * Copyright 2010 Gerhard Aigner
 * Copyright 2016 Kevin Goodwin
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
package at.laborg.briss.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import at.laborg.briss.model.ClusterDefinition;
import at.laborg.briss.model.PageCluster;
import at.laborg.briss.model.PageExcludes;

import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfReader;

public final class ClusterCreator {
	private ClusterCreator() {}

	public static ClusterDefinition clusterPages(final File source,
			final PageExcludes pageExcludes) throws IOException {
		PdfReader reader = new PdfReader(new FileInputStream(source.getAbsolutePath()));  // https://stackoverflow.com/questions/53301158/itext-java-11-illegal-reflective-access-by-com-itextpdf-io-source-bytebufferran
		ClusterDefinition clusters = new ClusterDefinition();
		for (int pgNum = 1; pgNum <= reader.getNumberOfPages(); pgNum++) {
			// System.out.format( "page %d\n", pgNum );
			Rectangle layoutBox = getLayoutBox(reader, pgNum);
			PageCluster tmpCluster = new PageCluster(pgNum % 2 == 0,
					(int) layoutBox.getWidth(),
					(int) layoutBox.getHeight(),
					(pageExcludes != null && pageExcludes.containsPage(pgNum)),
					pgNum);
			clusters.addOrMergeCluster(tmpCluster);
		}
		reader.close();
		clusters.designatePreviewPages();
		return clusters;
	}

	private static Rectangle getLayoutBox(final PdfReader reader, final int pgnum) {
		Rectangle layoutBox = reader.getBoxSize(pgnum, "crop");
		if (layoutBox == null) {
			layoutBox = reader.getBoxSize(pgnum, "media");
		}
		return layoutBox;
	}
}
