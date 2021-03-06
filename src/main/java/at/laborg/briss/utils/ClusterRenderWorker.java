/**
 * 
 */
package at.laborg.briss.utils;

import java.io.File;

import org.jpedal.PdfDecoder;
import org.jpedal.exception.PdfException;

import at.laborg.briss.model.ClusterDefinition;
import at.laborg.briss.model.PageCluster;

public class ClusterRenderWorker extends Thread {

	public int workerUnitCounter = 1;
	private final File source;
	private final ClusterDefinition clusters;

	public ClusterRenderWorker(final File source, final ClusterDefinition clusters) {
		super();
		this.source = source;
		this.clusters = clusters;
	}

	@Override
	public final void run() {
		PdfDecoder pdfDecoder = new PdfDecoder();
		try {
			// System.out.format( "ClusterRenderWorker: pdfDecoder.openPdfFile %s\n", source.getAbsolutePath() );
			pdfDecoder.openPdfFile(source.getAbsolutePath());
			// System.out.format( "ClusterRenderWorker: pdfDecoder.openPdfFile %s DONE\n", source.getAbsolutePath() );
		} catch (PdfException e1) {
			e1.printStackTrace();
		}
		int cnum = 0;
		for (PageCluster cluster : clusters.getClusterList()) {
			for (Integer pgNum : cluster.getPreviewPgNums()) {
				// System.out.format( "ClusterRenderWorker: C%d P%d\n", cnum, pgNum );
				// TODO jpedal isn't able to render big images correctly,
				// so let's check if the image is big and throw it away
				try {
					if (cluster.getImageData().isRenderable()) {
						cluster.getImageData().addImageToPreview( pdfDecoder.getPageAsImage(pgNum) );
						workerUnitCounter++;
					}
				} catch (PdfException e) { // TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			++cnum;
		}
		pdfDecoder.closePdfFile();
	}
}