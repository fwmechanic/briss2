package at.laborg.briss.model;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.stream.IntStream;

public class ClusterImageData {

	private static final int MAX_PAGE_HEIGHT = 900;
	private static final int MAX_IMAGE_RENDER_SIZE = 2000 * 2000;

	private final boolean renderable;
	private BufferedImage outputImage = null;
	private int outputImageHeight = -1;
	private int outputImageWidth = -1;
	private short[][][] imgdata;
	private int imageCnt = 0;
	private final int totalImages;

	public ClusterImageData(final int pageWidth, final int pageHeight, final int nrOfImages) {
		this.renderable = pageWidth * pageHeight < MAX_IMAGE_RENDER_SIZE;
		totalImages = nrOfImages;
	}

	public final boolean isRenderable() {
		return renderable;
	}

	public final void addImageToPreview(final BufferedImage imageToAdd) {
		if (!renderable)
			return;
		if (outputImageHeight == -1) {
			initializeOutputImage(imageToAdd);
		}
		add(scaleImage(imageToAdd, outputImageWidth, outputImageHeight));
	}

	private void initializeOutputImage(final BufferedImage imageToAdd) {
		outputImageHeight = Math.min( imageToAdd.getHeight(), MAX_PAGE_HEIGHT );
		float scaleFactor = (float) outputImageHeight / imageToAdd.getHeight();
		outputImageWidth = (int) (imageToAdd.getWidth() * scaleFactor);
		imgdata = new short[outputImageWidth][outputImageHeight][totalImages];
	}

	private void add(final BufferedImage image) {
		int[] tmp = null; // redundant param to disambiguate getPixel() call
		int height = image.getHeight();
		int width = image.getWidth();
		IntStream.range(0,width).parallel().forEach(i -> {
			IntStream.range(0,height).parallel().forEach(j -> {
				imgdata[i][j][imageCnt] = (short) image.getRaster().getPixel(i, j, tmp)[0];
			});
		});
		imageCnt++;
	}

	public final BufferedImage getPreviewImage() {
		if (!renderable)
			return getUnrenderableImage();
		if (outputImage == null) {
			outputImage = renderOutputImage();
			imgdata = null;
		}
		return outputImage;
	}

	private BufferedImage renderOutputImage() {
		if ((outputImageWidth <=0) || (outputImageHeight <= 0)) {
			// we have no image data - jpedal was probably not able to provide us with the data
			// so we create an empty image
			BufferedImage im = new BufferedImage(100, 100, BufferedImage.TYPE_BYTE_BINARY);
			WritableRaster raster = im.getRaster();
			IntStream.range(0,100).parallel().forEach(h -> {
				IntStream.range(0,100).parallel().forEach(w -> {
					raster.setSample(w,h,0,1);
				});
			});
			addImageToPreview(im);
		}
		BufferedImage outputImage = new BufferedImage(outputImageWidth, outputImageHeight, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster raster = outputImage.getRaster().createCompatibleWritableRaster();
		if (totalImages == 1) {
			IntStream.range(0,outputImage.getWidth()).parallel().forEach(i -> {
				IntStream.range(0,outputImage.getHeight()).parallel().forEach(j -> {
					raster.setSample(i, j, 0, imgdata[i][j][0]);
				});
			});
			outputImage.setData(raster);
			return outputImage;
		}
		int[][] sdvalue = calculateSdOfImages(imgdata, imageCnt);
		IntStream.range(0,outputImage.getWidth()).parallel().forEach(i -> {
			IntStream.range(0,outputImage.getHeight()).parallel().forEach(j -> {
				raster.setSample(i, j, 0, sdvalue[i][j]);
			});
		});
		outputImage.setData(raster);
		return outputImage;
	}

	private static BufferedImage scaleImage(final BufferedImage bsrc, final int width, final int height) {
		BufferedImage bdest = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D g = bdest.createGraphics();
		AffineTransform at = AffineTransform.getScaleInstance(
				(double) bdest.getWidth()  / bsrc.getWidth()  ,
				(double) bdest.getHeight() / bsrc.getHeight());
		g.drawRenderedImage(bsrc, at);
		g.dispose();
		return bdest;
	}

	private static BufferedImage getUnrenderableImage() {
		int width = 200;
		int height = 200;
		// Create buffered image that does not support transparency
		BufferedImage bimage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = bimage.createGraphics();
		// Draw on the image
		g2d.setColor(Color.WHITE);
		g2d.drawRect(5, 5, 190, 190);
		Font font = new Font("Sansserif", Font.BOLD | Font.PLAIN, 22);
		g2d.setFont(font);
		g2d.setColor(Color.WHITE);
		g2d.drawString("Image too Big!", 10, 110);
		g2d.dispose();
		return bimage;
	}

	private static int[][] calculateSdOfImages(final short[][][] imgdata, final int imageCnt) {
		int width = imgdata.length;
		int height = imgdata[0].length;
		int[][] sum = new int[width][height];
		int[][] mean = new int[width][height];
		int[][] sd = new int[width][height];
		IntStream.range(0,width).parallel().forEach(i -> {
			IntStream.range(0,height).parallel().forEach(j -> {
				IntStream.range(0,imageCnt).forEach(k -> {
					sum[i][j] += imgdata[i][j][k];
				});
			});
		});
		IntStream.range(0,width).parallel().forEach(i -> {
			IntStream.range(0,height).parallel().forEach(j -> {
				mean[i][j] = sum[i][j] / imageCnt;
			});
		});
		IntStream.range(0,width).parallel().forEach(i -> {
			IntStream.range(0,height).parallel().forEach(j -> {
				sum[i][j] = 0;
				IntStream.range(0,imageCnt).forEach(k -> {
					sum[i][j] += (imgdata[i][j][k] - mean[i][j])
							   * (imgdata[i][j][k] - mean[i][j]);
				});
			});
		});
		IntStream.range(0,width).parallel().forEach(i -> {
			IntStream.range(0,height).parallel().forEach(j -> {
				sd[i][j] = 255 - (int) Math.sqrt(sum[i][j] / imageCnt);
			});
		});
		return sd;
	}
}
