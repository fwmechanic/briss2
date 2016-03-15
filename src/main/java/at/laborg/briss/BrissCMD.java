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
package at.laborg.briss;

import java.io.File;
import java.io.IOException;
import java.util.List;

import at.laborg.briss.exception.CropException;
import at.laborg.briss.model.ClusterDefinition;
import at.laborg.briss.model.CropDefinition;
import at.laborg.briss.model.CropFinder;
import at.laborg.briss.model.PageCluster;
import at.laborg.briss.utils.BrissFileHandling;
import at.laborg.briss.utils.ClusterCreator;
import at.laborg.briss.utils.ClusterRenderWorker;
import at.laborg.briss.utils.DocumentCropper;
import at.laborg.briss.utils.CropParser;

import com.itextpdf.text.DocumentException;

public final class BrissCMD {
	private BrissCMD() {}

	public static void autoCrop(final String[] args) {
		CommandValues workDescription = CommandValues.parseToWorkDescription(args);
		if (!CommandValues.isValidJob(workDescription))
			return;
		System.out.println("Clustering PDF: " + workDescription.getSourceFile());
		ClusterDefinition clusterDefinition;
		try {
			clusterDefinition = ClusterCreator.clusterPages(workDescription.getSourceFile(), null);
		} catch (IOException e1) {
			System.out.println("Error occurred while clustering.");
			e1.printStackTrace(System.out);
			return;
		}
		System.out.println("Created " + clusterDefinition.getClusterList().size() + " clusters.");
		ClusterRenderWorker cRW = new ClusterRenderWorker(workDescription.getSourceFile(), clusterDefinition);
		cRW.start();
		System.out.print("Starting to render clusters.");
		while (cRW.isAlive()) {
			System.out.print(".");
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
		System.out.println("finished!");
		System.out.println("Calculating crop rectangles.");
		try {
			for (PageCluster cluster : clusterDefinition.getClusterList()) {
				Float[] auto = CropFinder.calcCropRatioOfImg(cluster.getImageData().getPreviewImage());
				cluster.addCropRatio(auto);
			}
			CropDefinition cropDefintion = CropDefinition.createCropDefinition(
					workDescription.getSourceFile(),
					workDescription.getDestFile(), clusterDefinition);
			System.out.println("Starting to crop files.");
			DocumentCropper.crop(cropDefintion);
			System.out.println("Successfully cropped to:" + workDescription.getDestFile().getAbsolutePath());
		} catch (IOException | IllegalArgumentException | CropException | DocumentException e) {
			e.printStackTrace();
		}
	}

	public static void customCrop(String[] args) {
		CommandValues workDescription = CommandValues.parseToWorkDescription(args);
		if (!CommandValues.isValidJob(workDescription))
			return;
		System.out.println("Clustering PDF: " + workDescription.getSourceFile());
		ClusterDefinition clusterDefinition;
		try {
			clusterDefinition = ClusterCreator.clusterPages(workDescription.getSourceFile(), null);
		} catch (IOException e1) {
			System.out.println("Error occurred while clustering.");
			e1.printStackTrace(System.out);
			return;
		}
		System.out.println("Created " + clusterDefinition.getClusterList().size() + " clusters.");
		if(workDescription.getCrop().size() != clusterDefinition.getClusterList().size()) {
			System.err.println("You need to specify a crop definition for ALL clusters!");
			return;
		}
		ClusterRenderWorker cRW = new ClusterRenderWorker(workDescription.getSourceFile(), clusterDefinition);
		cRW.start();
		System.out.print("Starting to render clusters.");
		while (cRW.isAlive()) {
			System.out.print(".");
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
			}
		}
		System.out.println("finished!");
		System.out.println("setting crop rectangles.");
		try {
			// the config cluster definition applies here
			for(int i = 0; i < clusterDefinition.getClusterList().size(); i++) {
				List<Float[]> cluserrat = workDescription.getCrop().get(i);
				for(Float[] cur : cluserrat) {
					clusterDefinition.getClusterList().get(i).addCropRatio(cur);
				}
			}
			CropDefinition cropDefinition = CropDefinition.createCropDefinition(
					workDescription.getSourceFile(),
					workDescription.getDestFile(), clusterDefinition);
			System.out.println("Starting to crop files.");
			DocumentCropper.crop(cropDefinition);
			System.out.println("Successfully cropped to:" + workDescription.getDestFile().getAbsolutePath());
		} catch (IOException | IllegalArgumentException | CropException | DocumentException e) {
			e.printStackTrace();
		}
	}

	private static class CommandValues {
		private static final String SOURCE_FILE_CMD = "-s";
		private static final String DEST_FILE_CMD = "-d";
		private static final String CROP_CMD = "-c";

		private File sourceFile;
		private File destFile;
		private List<List<Float[]>> crop = null;

		static CommandValues parseToWorkDescription(final String[] args) {
			CommandValues commandValues = new CommandValues();
			int i = 0;
			while (i < args.length) {
				if (args[i].trim().equalsIgnoreCase(SOURCE_FILE_CMD)) {
					if (i < (args.length - 1)) {
						commandValues.setSourceFile(new File(args[i + 1]));
					}
				} else if (args[i].trim().equalsIgnoreCase(DEST_FILE_CMD)) {
					if (i < (args.length - 1)) {
						commandValues.setDestFile(new File(args[i + 1]));
					}
				} else if (args[i].trim().equalsIgnoreCase(CROP_CMD)) {
					if (i < (args.length - 1)) {
						commandValues.setCropDefinition(args[i + 1]);
					}
				}
				i++;
			}
			return commandValues;
		}

		/**
		 * each page part: top/left/bottom/right
		 * part1_page1,part2_page1,...:part1_page2,part2_page2
		 * syntax: 0/0/0.5/0,0.5/0/0/0:0/0/0.5/0,0.5/0/0/0
		 * @param string the command parameter
		 */
		private void setCropDefinition(String string) {
			crop = CropParser.parse (string);
		}

		private static boolean isValidJob(final CommandValues job) {
			if (job.getSourceFile() == null) {
				System.out.println("No source file submitted: try \"java -jar Briss.0.0.13 -s filename.pdf\"");
				return false;
			}
			if (!job.getSourceFile().exists()) {
				System.out.println("File: " + job.getSourceFile() + " doesn't exist");
				return false;
			}
			if (job.getDestFile() == null) {
				File recommendedDest = BrissFileHandling.getRecommendedDestination(job.getSourceFile());
				job.setDestFile(recommendedDest);
				System.out.println("Since no destination was provided destination will be set to  : "
								+ recommendedDest.getAbsolutePath());
			}
			try {
				BrissFileHandling.checkValidStateAndCreate(job.getDestFile());
			} catch (IllegalArgumentException e) {
				System.out.println("Destination file couldn't be created!");
				return false;
			} catch (IOException e) {
				System.out.println("IO Error while creating destination file.");
				e.getStackTrace();
				return false;
			}
			return true;
		}

		public File getSourceFile() {
			return sourceFile;
		}

		public void setSourceFile(final File sourceFile) {
			this.sourceFile = sourceFile;
		}

		public File getDestFile() {
			return destFile;
		}

		public void setDestFile(final File destFile) {
			this.destFile = destFile;
		}

		/**
		 * @return the crop
		 */
		public List<List<Float[]>> getCrop() {
			return crop;
		}
	}
}
