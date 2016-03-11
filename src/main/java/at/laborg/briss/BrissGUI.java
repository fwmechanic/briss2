// $Id$
/**
 * Copyright 2010, 2011 Gerhard Aigner, Rastislav Wartiak
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
package at.laborg.briss;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.KeyStroke;

import org.jpedal.exception.PdfException;

import at.laborg.briss.exception.CropException;
import at.laborg.briss.gui.HelpDialog;
import at.laborg.briss.gui.MergedPanel;
import at.laborg.briss.gui.WrapLayout;
import at.laborg.briss.model.ClusterDefinition;
import at.laborg.briss.model.CropDefinition;
import at.laborg.briss.model.PageCluster;
import at.laborg.briss.model.PageExcludes;
import at.laborg.briss.model.WorkingSet;
import at.laborg.briss.utils.BrissFileHandling;
import at.laborg.briss.utils.ClusterCreator;
import at.laborg.briss.utils.ClusterRenderWorker;
import at.laborg.briss.utils.DesktopHelper;
import at.laborg.briss.utils.DocumentCropper;
import at.laborg.briss.utils.PDFFileFilter;
import at.laborg.briss.utils.PageNumberParser;
import at.laborg.briss.utils.CropParser;

import com.itextpdf.text.DocumentException;

/**
 * 
 * @author gerhard
 * 
 */
@SuppressWarnings("serial")
public class BrissGUI extends JFrame implements ActionListener,
		PropertyChangeListener, ComponentListener {

	private static final String EXCLUDE_PAGES_DESCRIPTION = "Enter pages to be excluded from merging (e.g.: \"1-4;6;9\").\n"
			+ "First page has number: 1\n"
			+ "If you don't know what you should do just press \"Cancel\"";
	private static final String SET_SIZE_DESCRIPTION = "Enter size in milimeters (width height)";
	private static final String SET_POSITION_DESCRIPTION = "Enter position in milimeters (x y)";
	private static final String LOAD = "Load File";
	private static final String SET_CROP = "Set Crop Definition";
	private static final String SHOW_CROP = "Show Crop Definition";
	private static final String CROP = "Crop PDF";
	private static final String EXIT = "Exit";
	private static final String MAXIMIZE_WIDTH = "Maximize to width";
	private static final String MAXIMIZE_HEIGHT = "Maximize to height";
	private static final String EXCLUDE_OTHER_PAGES = "Exclude other pages";
	private static final String PREVIEW = "Preview";
	private static final String DONATE = "Donate";
	private static final String HELP = "Show help";
	private static final String MAXIMIZE_SIZE = "Maximize to size (all)";
	private static final String SET_SIZE = "Set size (selected)";
	private static final String SET_POSITION = "Set position (selected)";
	private static final String MOVE_LEFT = "Move left (selected)";
	private static final String MOVE_RIGHT = "Move right (selected)";
	private static final String MOVE_UP = "Move up (selected)";
	private static final String MOVE_DOWN = "Move down (selected)";
	private static final String SELECT_ALL = "Select all";
	private static final String SELECT_NONE = "Select none";
	private static final String emCROP = "Error occurred while cropping";
	private static final String emLOAD = "Error occurred while loading";
	private static final String emRELOAD = "Error occurred while reloading";

	private static final String DONATION_URI = "http://sourceforge.net/project/project_donations.php?group_id=320676";
	private static final String RES_ICON_PATH = "/Briss_icon_032x032.gif";

	private JPanel previewPanel;
	private JProgressBar progressBar;
	private List<MergedPanel> mergedPanels = null;
	private List<JMenuItem> conditionalMenuItems = new ArrayList<JMenuItem>();

	private File lastOpenDir;

	private WorkingSet workingSet;

	public BrissGUI(String[] args) {
		super("BRISS - BRIght Snippet Sire");
		init();
		tryToLoadFileFromArgument(args);
	}

	private void tryToLoadFileFromArgument(String[] args) {
		if (args.length == 0)
			return;
		File fileArg = new File(args[0]);
		if (fileArg.exists()
				&& fileArg.isFile()
				&& fileArg.getName().toLowerCase().endsWith(".pdf")
				) {
			try {
				importNewPdfFile(fileArg);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), "Briss error", JOptionPane.ERROR_MESSAGE);
			} catch (PdfException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), "Briss error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private JMenuItem newJMI(String st, boolean enable, int keyCode, int modifiers ) {
		JMenuItem jmi = new JMenuItem(st, keyCode);
		jmi.addActionListener(this);
		jmi.setEnabled(enable);
		jmi.setAccelerator(KeyStroke.getKeyStroke(keyCode, modifiers));
		if( !enable ) {
			conditionalMenuItems.add(jmi);
		}
		return jmi;
	}

	private void init() {
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		this.setTransferHandler(new BrissTransferHandler(this));
		setUILook();
		loadIcon();

		// Create the menu bar.
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);
		JMenu rectangleMenu = new JMenu("Rectangle");
		rectangleMenu.setMnemonic(KeyEvent.VK_R);
		JMenu actionMenu = new JMenu("Action");
		actionMenu.setMnemonic(KeyEvent.VK_A);

		menuBar.add(fileMenu);
		menuBar.add(rectangleMenu);
		menuBar.add(actionMenu);

		fileMenu.add(newJMI(LOAD, true, KeyEvent.VK_L, 0));

		fileMenu.addSeparator();

		JMenuItem cropstrButton = new JMenuItem(SHOW_CROP);
		cropstrButton.addActionListener(this);
		cropstrButton.setEnabled(true);
		fileMenu.add(cropstrButton);

		cropstrButton = new JMenuItem(SET_CROP);
		cropstrButton.addActionListener(this);
		cropstrButton.setEnabled(true);
		fileMenu.add(cropstrButton);

		fileMenu.addSeparator();

		JMenuItem openDonationLinkButton = new JMenuItem(DONATE);
		openDonationLinkButton.addActionListener(this);
		fileMenu.add(openDonationLinkButton);
		fileMenu.add(newJMI(EXCLUDE_OTHER_PAGES, false, KeyEvent.VK_E, 0));

		JMenuItem showHelpButton = new JMenuItem(HELP);
		showHelpButton.addActionListener(this);
		fileMenu.add(showHelpButton);

		fileMenu.addSeparator();

		JMenuItem menuItem = new JMenuItem(EXIT, KeyEvent.VK_E);
		menuItem.addActionListener(this);
		fileMenu.add(menuItem);

		actionMenu.add(   newJMI(CROP           , false, KeyEvent.VK_C, 0));
		actionMenu.add(   newJMI(PREVIEW        , false, KeyEvent.VK_P, 0));

		rectangleMenu.add(newJMI(MAXIMIZE_WIDTH , false, KeyEvent.VK_W, 0));
		rectangleMenu.add(newJMI(MAXIMIZE_HEIGHT, false, KeyEvent.VK_H, 0));
		rectangleMenu.add(newJMI(MAXIMIZE_SIZE  , false, KeyEvent.VK_M, 0));
		rectangleMenu.add(newJMI(SET_SIZE       , false, KeyEvent.VK_S, 0));
		rectangleMenu.add(newJMI(SET_POSITION   , false, KeyEvent.VK_O, 0));
		rectangleMenu.addSeparator();
		rectangleMenu.add(newJMI(MOVE_LEFT      , false, KeyEvent.VK_LEFT , 0));
		rectangleMenu.add(newJMI(MOVE_RIGHT     , false, KeyEvent.VK_RIGHT, 0));
		rectangleMenu.add(newJMI(MOVE_UP        , false, KeyEvent.VK_UP   , 0));
		rectangleMenu.add(newJMI(MOVE_DOWN      , false, KeyEvent.VK_DOWN , 0));
		rectangleMenu.addSeparator();
		rectangleMenu.add(newJMI(SELECT_ALL     , false, KeyEvent.VK_A, 0));
		rectangleMenu.add(newJMI(SELECT_NONE    , false, KeyEvent.VK_N, 0));

		setJMenuBar(menuBar);

		previewPanel = new JPanel();
		previewPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 4, 4));
		previewPanel.setEnabled(true);
		previewPanel.setBackground(Color.BLACK);
		previewPanel.addComponentListener(this);

		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);
		progressBar.setPreferredSize(new Dimension(400, 30));
		progressBar.setEnabled(true);

		JScrollPane scrollPane = new JScrollPane(previewPanel,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getVerticalScrollBar().setUnitIncrement(30);
		add(scrollPane, BorderLayout.CENTER);
		add(progressBar, BorderLayout.PAGE_END);
		pack();
		setVisible(true);
	}

	private void setUILook() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (UnsupportedLookAndFeelException ex) {
			System.out.println("Unable to load native look and feel");
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		}
	}

	private void loadIcon() {
		InputStream is = getClass().getResourceAsStream(RES_ICON_PATH);
		byte[] buf = new byte[1024 * 100];
		try {
			int cnt = is.read(buf);
			byte[] imgBuf = Arrays.copyOf(buf, cnt);
			setIconImage(new ImageIcon(imgBuf).getImage());
		} catch (IOException e) {
		}
	}

	private static String lastUserExcludes = "";
	private static PageExcludes getExcludedPages() {
		String previousInput = lastUserExcludes;
		// repeat show_dialog until valid input or canceled
		while (true) {
			String input = JOptionPane.showInputDialog(EXCLUDE_PAGES_DESCRIPTION, previousInput);
			previousInput = input;
			if (input == null || input.equals(""))
				return null;
			try {
				PageExcludes rv = new PageExcludes(PageNumberParser.parsePageNumber(input));
				lastUserExcludes = input; // got past any ParseExecption, save for next time
				return rv;
			} catch (ParseException e) {
				JOptionPane.showMessageDialog(null, e.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private File getCropFileDestination(File sourceFile) {
		File recommendedFile = BrissFileHandling.getRecommendedDestination(sourceFile);
		JFileChooser fc = new JFileChooser(lastOpenDir);
		fc.setSelectedFile(recommendedFile);
		fc.setFileFilter(new PDFFileFilter());
		int retval = fc.showSaveDialog(this);
		if (retval == JFileChooser.APPROVE_OPTION) {
			return fc.getSelectedFile();
		}
		return null;
	}

	private File getNewFileToCrop() {
		JFileChooser fc = new JFileChooser(lastOpenDir);
		fc.setFileFilter(new PDFFileFilter());
		int retval = fc.showOpenDialog(this);
		if (retval == JFileChooser.APPROVE_OPTION) {
			return fc.getSelectedFile();
		}
		return null;
	}

	public void actionPerformed(ActionEvent action) {
		switch( action.getActionCommand() ) {
		case DONATE:
			try {
				DesktopHelper.openDonationLink(DONATION_URI);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emLOAD, JOptionPane.ERROR_MESSAGE);
			} break;
		case EXIT: System.exit(0);
		case HELP: new HelpDialog(this, "Briss Help", Dialog.ModalityType.MODELESS); break;
		case MAXIMIZE_HEIGHT: maximizeHeightInSelectedRects(); break;
		case MAXIMIZE_WIDTH:  maximizeWidthInSelectedRects(); break;
		case EXCLUDE_OTHER_PAGES:
			if (workingSet.getSourceFile() == null) {
				return;
			}
			setWorkingState("Exclude other pages");
			try {
				reloadWithOtherExcludes();
				setTitle("BRISS - " + workingSet.getSourceFile().getName());
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emRELOAD, JOptionPane.ERROR_MESSAGE);
			} catch (PdfException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emRELOAD, JOptionPane.ERROR_MESSAGE);
			} break;
		case LOAD:
			File inputFile = getNewFileToCrop();
			if (inputFile == null)
				return;
			try {
				importNewPdfFile(inputFile);
				setTitle("BRISS - " + inputFile.getName());
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emLOAD, JOptionPane.ERROR_MESSAGE);
			} catch (PdfException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emLOAD, JOptionPane.ERROR_MESSAGE);
			} break;
		case SHOW_CROP: {
			ClusterDefinition clusters = workingSet.getClusterDefinition();
			JOptionPane.showInputDialog(this, "Crop Option: ", CropParser.cropToString(clusters.getAllRatios()));
			} break;
		case SET_CROP: {
			ClusterDefinition clusters = workingSet.getClusterDefinition();
			String cropStr = JOptionPane.showInputDialog(this, "Crop Option: ", CropParser.cropToString(clusters.getAllRatios()));
			List<List<Float[]>> rLL = CropParser.parse(cropStr);
			for (int i = 0; i < rLL.size(); i++) {
				clusters.getClusterList().get(i).setRatiosList(rLL.get(i));
			}
			createMergedPanels(false);
			pack();
			repaint();
			} break;
		case CROP:
			try {
				setWorkingState("loading PDF");
				File result = createAndExecuteCropJob(workingSet.getSourceFile());
				if (result != null) {
					DesktopHelper.openFileWithDesktopApp(result);
					lastOpenDir = result.getParentFile();
				}
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emCROP, JOptionPane.ERROR_MESSAGE);
			} catch (DocumentException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emCROP, JOptionPane.ERROR_MESSAGE);
			} catch (CropException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emCROP, JOptionPane.ERROR_MESSAGE);
			} finally {
				setIdleState("");
			} break;
		case PREVIEW:
			try {
				setWorkingState("Creating and showing preview...");
				File result = createAndExecuteCropJobForPreview();
				DesktopHelper.openFileWithDesktopApp(result);
			} catch (IOException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emCROP, JOptionPane.ERROR_MESSAGE);
			} catch (DocumentException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emCROP, JOptionPane.ERROR_MESSAGE);
			} catch (CropException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), emCROP, JOptionPane.ERROR_MESSAGE);
			} finally {
				setIdleState("");
			} break;
		case MAXIMIZE_SIZE:	maximizeSizeInAllRects(); break;
		case SET_SIZE:		setDefinedSizeSelRects(); break;
		case SET_POSITION:	setPositionSelRects();    break;
		case SELECT_ALL:
			for (MergedPanel mp : mergedPanels)
				mp.selectAllCrops(true);
			break;
		case SELECT_NONE:
			for (MergedPanel mp : mergedPanels)
				mp.selectAllCrops(false);
			break;
		}
	}

	private File createAndExecuteCropJobForPreview() throws IOException, DocumentException, CropException {
		File tmpCropFileDestination = File.createTempFile("briss", ".pdf");
		CropDefinition cropDefinition = CropDefinition.createCropDefinition(
				workingSet.getSourceFile(), tmpCropFileDestination, workingSet.getClusterDefinition());
		return DocumentCropper.crop(cropDefinition);
	}

	private File createAndExecuteCropJob(File source) throws IOException, DocumentException, CropException {
		File cropDestinationFile = getCropFileDestination(workingSet.getSourceFile());
		if (cropDestinationFile == null)
			return null;
		CropDefinition cropDefinition = CropDefinition.createCropDefinition(
				workingSet.getSourceFile(), cropDestinationFile, workingSet.getClusterDefinition());
		return DocumentCropper.crop(cropDefinition);
	}

	private void setIdleState(String stateMessage) {
		progressBar.setValue(0);
		progressBar.setString(stateMessage);
		setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}

	private void setWorkingState(String stateMessage) {
		progressBar.setString(stateMessage);
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	}

	void importNewPdfFile(File loadFile) throws IOException, PdfException {
		// (ab)use of PdfException is admittedly hacky, but I want to preempt wasting
		// time carefully cropping a file only to find out that it CANNOT be cropped!
		if( DocumentCropper.isPasswordRequired(loadFile) ) {
			throw new PdfException("Password required to crop source file");
		}
		lastOpenDir = loadFile.getParentFile();
		previewPanel.removeAll();
		progressBar.setString("Loading new file - Creating merged previews");
		ClusterPagesTask clusterTask = new ClusterPagesTask(loadFile, getExcludedPages());
		clusterTask.addPropertyChangeListener(this);
		clusterTask.execute();
	}

	private void reloadWithOtherExcludes() throws IOException, PdfException {
		previewPanel.removeAll();
		progressBar.setString("Reloading file - Creating merged previews");
		ClusterPagesTask clusterTask = new ClusterPagesTask(workingSet.getSourceFile(), getExcludedPages());
		clusterTask.addPropertyChangeListener(this);
		clusterTask.execute();
	}

	private void maximizeWidthInSelectedRects() {
		// maximize to width
		// search for maximum width
		int maxWidth = -1;
		for (MergedPanel mp : mergedPanels) {
			maxWidth = Math.max(maxWidth , mp.selCropsGetMaxWidth());
		}
		// set maximum width to all rectangles
		if (maxWidth == -1)
			return;
		for (MergedPanel mp : mergedPanels) {
			mp.selCropsSetWidth(maxWidth);
		}
	}

	private void maximizeHeightInSelectedRects() {
		// maximize to height
		// search for maximum height
		int maxHeight = -1;
		for (MergedPanel mp : mergedPanels) {
			maxHeight = Math.max(maxHeight, mp.selCropsGetMaxHeight());
		}
		// set maximum height to all rectangles
		if (maxHeight == -1)
			return;
		for (MergedPanel mp : mergedPanels) {
			mp.selCropsSetHeight(maxHeight);
		}
	}

	private void maximizeSizeInAllRects() {
		// maximize to width and height for all rectangles
		// search for maximums
		int maxWidth = -1;
		int maxHeight = -1;
		for (MergedPanel mp : mergedPanels) {
			Dimension panelMaxSize = mp.allCropsGetMaxSizes();
			maxWidth  = Math.max(maxWidth , panelMaxSize.width );
			maxHeight = Math.max(maxHeight, panelMaxSize.height);
		}
		// set maximum size to all rectangles
		if ((maxWidth == -1) || (maxHeight == -1))
			return;
		for (MergedPanel mp : mergedPanels) {
			mp.allCropsSetSize(maxWidth, maxHeight);
		}
	}

	public void alignSelRects(int x, int y, int w, int h) {
		// set position and size of selected rectangles
		for (MergedPanel mp : mergedPanels) {
			mp.selCropsSetSize(w, h);
			mp.selCropsMoveAbsolute(x, y);
		}
	}

	public void moveSelectedRects(int x, int y) {
		// move selected rectangles
		// parameters are relative to current position
		for (MergedPanel mp : mergedPanels) {
			mp.selCropsMoveRelative(x, y);
		}
	}

	private static int mm_to_points( int mm  ) { return Math.round( mm  * 72f / 25.4f); }
	private static int points_to_mm( int pts ) { return Math.round( pts * 25.4f / 72f); }

	public void setDefinedSizeSelRects() {
		// set size of selected rectangles based on user input
		String defInput = "";
		// get maximum dimensions
		int maxWidth = -1;
		int maxHeight = -1;
		for (MergedPanel mp : mergedPanels) {
			maxWidth  = Math.max(maxWidth , mp.selCropsGetMaxWidth() );
			maxHeight = Math.max(maxHeight, mp.selCropsGetMaxHeight());
		}
		if ((maxWidth >= 0) && (maxHeight >= 0)) {
			maxWidth  = points_to_mm( maxWidth  );
			maxHeight = points_to_mm( maxHeight );
			defInput = Integer.toString(maxWidth) + " " + Integer.toString(maxHeight);
		}
		// get user input; maximums are used as a default
		String input = JOptionPane.showInputDialog(SET_SIZE_DESCRIPTION, defInput);
		if (input == null || input.equals("")) {
			return;
		}
		String[] dims = input.split(" ", 2);
		if (dims.length != 2) {
			return;
		}
		int w;
		int h;
		try {
			w = Integer.parseInt(dims[0]);
			h = Integer.parseInt(dims[1]);
		} catch (NumberFormatException e) {
			return;
		}
		w = mm_to_points( w );
		h = mm_to_points( h );
		for (MergedPanel mp : mergedPanels) {
			mp.selCropsSetSize(w,h);
		}
	}

	public void setPositionSelRects() {
		// set position of selected rectangles based on user input
		String defInput = "";
		// get minimums of positions
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		for (MergedPanel mp : mergedPanels) {
			minX = Math.min(minX, mp.selCropsGetLeftmost() );
			minY = Math.min(minY, mp.selCropsGetUpmost()   );
		}
		if ((minX < Integer.MAX_VALUE) && (minY < Integer.MAX_VALUE)) {
			minX = points_to_mm(minX);
			minY = points_to_mm(minY);
			defInput = Integer.toString(minX) + " " + Integer.toString(minY);
		}
		// get user input; minimums are used as a default
		String input = JOptionPane.showInputDialog(SET_POSITION_DESCRIPTION, defInput);
		if (input == null || input.equals("")) {
			return;
		}
		String[] dims = input.split(" ", 2);
		if (dims.length != 2) {
			return;
		}
		int x;
		int y;
		try {
			x = Integer.parseInt(dims[0]);
			y = Integer.parseInt(dims[1]);
		} catch (NumberFormatException e) {
			return;
		}
		x = mm_to_points(x);
		y = mm_to_points(y);
		for (MergedPanel mp : mergedPanels) {
			mp.selCropsMoveAbsolute(x, y);
		}
	}

	public void resizeSelectedRects(int w, int h) {
		// change size of selected rectangles (relative)
		for (MergedPanel mp : mergedPanels) {
			mp.selCropsResize(w, h);
		}
	}

	public void propertyChange(PropertyChangeEvent evt) {
		if ("progress".equals(evt.getPropertyName())) {
			progressBar.setValue((Integer) evt.getNewValue());
		}
	}

	private void createMergedPanels (boolean autoCrop) {
		previewPanel.removeAll();
		mergedPanels = new ArrayList<MergedPanel>();
		for (PageCluster cluster : workingSet.getClusterDefinition().getClusterList()) {
			MergedPanel mp = new MergedPanel(cluster, this, autoCrop);
			previewPanel.add(mp);
			mergedPanels.add(mp);
		}
		previewPanel.revalidate();
	}

	private void EnableConditionalGuiButtons() {
		for( JMenuItem jmi : conditionalMenuItems ) {
			jmi.setEnabled(true);
		}
	}

	private void setStateAfterClusteringFinished(ClusterDefinition newClusters, PageExcludes newPageExcludes, File newSource) {
		updateWorkingSet(newClusters, newPageExcludes, newSource);
		createMergedPanels (true);
		progressBar.setString("Clustering and Rendering finished");
		EnableConditionalGuiButtons();
		setIdleState("");
		setExtendedState(Frame.MAXIMIZED_BOTH);
		pack();
		repaint();
	}

	private void updateWorkingSet(ClusterDefinition newClusters, PageExcludes newPageExcludes, File newSource) {
		if (workingSet == null) {
			workingSet = new WorkingSet(newSource);
		} else if (workingSet.getSourceFile().equals(newSource)) {
			// just reload with other excludes
			copyCropsToClusters(workingSet.getClusterDefinition(), newClusters);
		}
		workingSet.setSourceFile(newSource);
		workingSet.setClusters(newClusters);
		workingSet.setPageExcludes(newPageExcludes);
	}

	private void copyCropsToClusters(ClusterDefinition oldClusters, ClusterDefinition newClusters) {
		for (PageCluster newCluster : newClusters.getClusterList()) {
			for (Integer pgNum : newCluster.getMemberPgNums()) {
				PageCluster oldCluster = oldClusters.getClusterContainingPage(pgNum);
				for (Float[] ratios : oldCluster.getCropRatioList()) {
					newCluster.addCropRatio(ratios);
				}
			}
		}
	}

	private class ClusterPagesTask extends SwingWorker<Void, Void> {
		private final File source;
		private final PageExcludes pageExcludes;
		private ClusterDefinition clusterDefinition = null;

		public ClusterPagesTask(File source, PageExcludes pageExcludes) {
			super();
			this.source = source;
			this.pageExcludes = pageExcludes;
		}

		@Override
		protected void done() {
			setStateAfterClusteringFinished(clusterDefinition, pageExcludes, source);
		}

		@Override
		protected Void doInBackground() {
			try {
				clusterDefinition = ClusterCreator.clusterPages(source, pageExcludes);
				// System.out.println( "ClusterCreator.clusterPages done" );
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return null;
			}
			int totalWorkUnits = clusterDefinition.getNrOfPagesToRender();
			// System.out.format( "clusterDefinition.getNrOfPagesToRender() = %d\n", totalWorkUnits );
			ClusterRenderWorker renderWorker = new ClusterRenderWorker(source, clusterDefinition);
			renderWorker.start();
			while (renderWorker.isAlive()) {
				int percent = (int) ((renderWorker.workerUnitCounter / (float) totalWorkUnits) * 100);
				setProgress(percent);
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
			}
			return null;
		}
	}

	public void componentResized(ComponentEvent e) {
		previewPanel.revalidate();
		for (Component component : previewPanel.getComponents()) {
			component.repaint();
		}
	}

	public void componentMoved(ComponentEvent e) {}
	public void componentShown(ComponentEvent e) {}
	public void componentHidden(ComponentEvent e) {}
}
