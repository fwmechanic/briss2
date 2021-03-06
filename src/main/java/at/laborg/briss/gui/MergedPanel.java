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

package at.laborg.briss.gui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.OptionalInt;
import java.util.stream.Collectors;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import at.laborg.briss.model.CropFinder;
import at.laborg.briss.model.PageCluster;
import at.laborg.briss.BrissGUI;

@SuppressWarnings("serial")
public class MergedPanel extends JPanel {
	/* GUI layer wrapping a PageCluster ?

	  */

	// last drawn rectangle. a "ghosting" rectangle will
	// help the user to create the two equally sized crop rectangles
	private static DrawableCropRect curCrop;
	private static Point lastDragPoint;
	private static Point cropStartPoint;
	private static Point popUpMenuPoint;
	private static Point relativeHotCornerGrabDistance;
	private static ActionState actionState = ActionState.NOTHING;

	private final static int SELECT_BORDER_WIDTH = 1;
	private final static Font BASE_FONT = new Font(null, Font.PLAIN, 10);
	private final static Composite SMOOTH_NORMAL = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .2f);
	private final static Composite SMOOTH_SELECT = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5f);
	private final static Composite XOR_COMPOSITE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .8f);
	private final static float[] DASH_PATTERN = { 25f, 25f };
	private final static BasicStroke SELECTED_STROKE = new BasicStroke(
			SELECT_BORDER_WIDTH, BasicStroke.CAP_SQUARE,
			BasicStroke.JOIN_BEVEL, 1.0f, DASH_PATTERN, 0f);

	private final PageCluster cluster;

	private final List<DrawableCropRect> crops = new ArrayList<>();
	private final BufferedImage img;

	private enum ActionState {
		NOTHING, DRAWING_NEW_CROP, RESIZING_HOTCORNER_UL, RESIZING_HOTCORNER_LR, MOVE_CROP
	}
        
	private final BrissGUI briss;

	public MergedPanel(PageCluster cluster_, BrissGUI briss_, boolean autoCrop) {
		super();
		briss = briss_;
		cluster = cluster_;
		img = cluster.getImageData().getPreviewImage();
		if (autoCrop) {
			cluster.addCropRatio( CropFinder.calcCropRatioOfImg(img) );
		}
		setPreferredSize( new Dimension(img.getWidth(), img.getHeight()) );
		setSize(          new Dimension(img.getWidth(), img.getHeight()) );
		if (cluster.getImageData().isRenderable()) {
			MergedPanelMouseAdapter mouseAdapter = new MergedPanelMouseAdapter();
			addMouseMotionListener(mouseAdapter);
			addMouseListener(mouseAdapter);
		}
		xlatCropRatiosToCropRects();
		setToolTipText( cluster.createToolTipText() );

		addKeyListener(new MergedPanelKeyAdapter());
		setFocusable(true);
		requestFocusInWindow();
		repaint();
	}

	private void xlatCropRatiosToCropRects() {
		crops.clear();
		for (Float[] ratios : cluster.getCropRatioList()) {
			DrawableCropRect rect = new DrawableCropRect();
			rect.x      = (int) (img.getWidth()  * ratios[0]);
			rect.y      = (int) (img.getHeight() * ratios[3]);
			rect.width  = (int) (img.getWidth()  * (1 - (ratios[0] + ratios[2])));
			rect.height = (int) (img.getHeight() * (1 - (ratios[1] + ratios[3])));
			// System.out.format( "ratio2rect=img(x=%d,y=%d), rect(x=%d,y=%d)", img.getWidth(), img.getHeight(), rect.width, rect.height );
			crops.add(rect);
		}
		// System.out.println( "" );
	}

	private void xlatCropRectsToCropRatios(List<DrawableCropRect> tmpCrops) {
		cluster.clearRatios();
		for (Rectangle crop : tmpCrops) {
			// System.out.format( "rect2ratio=(x=%d,y=%d)", img.getWidth(), img.getHeight() );
			cluster.addCropRatio( getCutRatiosForPdf(crop, img.getWidth(), img.getHeight()) );
		}
		// System.out.println( "" );
	}

	@Override
	public void paint(Graphics g) {
		update(g);
	}

	@Override
	public void update(Graphics g) {
		if (!isEnabled()) {
			return;
		}
		Graphics2D g2 = (Graphics2D) g;
		g2.drawImage(img, null, 0, 0);
		// draw previously created rectangles
		int cropCnt = 0;
		for (DrawableCropRect crop : crops) {
			drawNormalCropRectangle(g2, cropCnt, crop);
			if (crop.isSelected()) {
				drawSelectionOverlay(g2, crop);
			}
			cropCnt++;
		}
		g2.dispose();
	}

	private static Color rgba( int r, int g, int b, double a) {
		// to allow direct pasting from https://developer.mozilla.org/en-US/docs/Web/CSS/CSS_Colors/Color_picker_tool
		return new Color(r, g, b, (int)Math.ceil( 255*a ) );
	}

	private void drawNormalCropRectangle(Graphics2D g2, int cropCnt, DrawableCropRect crop) {
		g2.setComposite(SMOOTH_NORMAL);
		g2.setColor(
				//rgba(63, 252, 53, 0.5)
				//rgba(2, 201, 92, 0.2)
				rgba(35, 158, 239, 0.63)
		);
		// g2.setColor(Color.YELLOW);
		// g2.setColor(Color.BLUE);
		g2.fill(crop);
		g2.setColor(Color.BLACK);
		g2.setFont(scaleFont(String.valueOf(cropCnt + 1), crop));
		g2.drawString(String.valueOf(cropCnt + 1), crop.x, crop.y + crop.height);
		int cD = DrawableCropRect.CORNER_DIMENSION;
		g2.fillRect(crop.x, crop.y, cD, cD);
		g2.fillRect(crop.x + crop.width - cD - 1, crop.y + crop.height - cD - 1, cD, cD);
	}

	private void drawSelectionOverlay(Graphics2D g2, DrawableCropRect crop) {
		g2.setComposite(XOR_COMPOSITE);
		g2.setColor(Color.BLACK);
		g2.setStroke(SELECTED_STROKE);
		g2.drawRect(crop.x + SELECT_BORDER_WIDTH / 2,
					crop.y + SELECT_BORDER_WIDTH / 2,
					crop.width  - SELECT_BORDER_WIDTH,
					crop.height - SELECT_BORDER_WIDTH);
		// display crop size in mm
		int w = Math.round(25.4f * crop.width  / 72f);
		int h = Math.round(25.4f * crop.height / 72f);
		String size = Integer.toString(w) + "x" + Integer.toString(h);
		g2.setFont(scaleFont(size, crop));
		g2.setColor(Color.YELLOW);
		g2.setComposite(SMOOTH_SELECT);
		g2.drawString(size, crop.x +               SELECT_BORDER_WIDTH,
							crop.y + crop.height - SELECT_BORDER_WIDTH);
	}

	private void toggleCropSelectionUnderPt(Point p) {
		for (DrawableCropRect crop : crops) {
			if (crop.contains(p)) {
				crop.setSelected(!crop.isSelected());
				break;
			}
		}
		repaint();
	}

	public int selCropsGetMaxWidth() {
		OptionalInt max = crops.stream().filter(DrawableCropRect::isSelected).mapToInt(i -> i.width).max();
		return max.orElse(-1);
	}

	public int selCropsGetMaxHeight() {
		OptionalInt max = crops.stream().filter(DrawableCropRect::isSelected).mapToInt(i -> i.height).max();
		return max.orElse(-1);
	}

	public int selCropsGetLeftmost() {
		OptionalInt min = crops.stream().filter(DrawableCropRect::isSelected).mapToInt(i -> i.x).max();
		return min.orElse(Integer.MAX_VALUE);
	}

	public int selCropsGetUpmost() {
		OptionalInt min = crops.stream().filter(DrawableCropRect::isSelected).mapToInt(i -> i.y).max();
		return min.orElse(Integer.MAX_VALUE);
	}

	public Dimension allCropsGetMaxSizes() {
		int maxW = -1;
		int maxH = -1;
		for (DrawableCropRect crop : crops) {
			maxW = Math.max(maxW, crop.width);
			maxH = Math.max(maxH, crop.height);
		}
		return new Dimension(maxW, maxH);
	}

	public void selCropsSetWidth(int width) {
		crops.stream().filter(DrawableCropRect::isSelected).forEach(crop -> crop.setSize(width, crop.height));
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	public void selCropsSetHeight(int height) {
		crops.stream().filter(DrawableCropRect::isSelected).forEach(crop -> crop.setSize(crop.width, height));
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	public void selCropsSetSize(int width, int height) {
		crops.stream().filter(DrawableCropRect::isSelected).forEach(crop -> crop.setSize(width, height));
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	public void selCropsResize(int width, int height) {
		for (DrawableCropRect crop : crops) {
			if (crop.isSelected()) {
				if (((width < 0) && (crop.width <= -width)) ||
					((height < 0) && (crop.height <= -height))) {
					return;
				}
				crop.setSize(crop.width + width, crop.height + height);
			}
		}
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	public void allCropsSetSize(int width, int height) {
		crops.forEach( crop -> crop.setSize(width, height) );
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	public void selCropsMoveRelative(int x, int y) {
		crops.stream().filter(DrawableCropRect::isSelected).forEach(crop -> crop.translate(x, y));
		removeAnyCropEntirelyBeyondVisibleArea();
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	public void selCropsMoveAbsolute(int x, int y) {
		crops.stream().filter(DrawableCropRect::isSelected).forEach(crop -> crop.setLocation(x, y));
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	public void selectAllCrops(boolean select) {
		crops.forEach( crop -> crop.setSelected(select) );
		repaint();
	}

	/**
	 * creates the crop ratios from the user selection. 0 = left 1 = bottom 2 =
	 * right 3 = top
	 * 
	 * @param crop
	 * 
	 * @return the cropped ratios or null if to small
	 */
	private static Float[] getCutRatiosForPdf(Rectangle crop, int imgWidth, int imgHeight) {
		int x1 = crop.x;
		int y1 = crop.y;
		int x2 = x1 + crop.width;
		int y2 = y1 + crop.height;
		// constrain
		x1 = Math.max( x1, 0 );
		y1 = Math.max( y1, 0 );
		x2 = Math.min( x2, imgWidth );
		y2 = Math.min( y2, imgHeight );
		// recalc
		Float[] ratios = new Float[4];
		ratios[0] =      (float)              x1  / imgWidth  ; // left
		ratios[1] =      (float) (imgHeight - y2) / imgHeight ; // bottom
		ratios[2] = 1 - ((float)              x2  / imgWidth) ; // right
		ratios[3] = 1 - ((float) (imgHeight - y1) / imgHeight); // top
		return ratios;
	}

	private Font scaleFont(String text, Rectangle rect) {
		int size = BASE_FONT.getSize();
		int width = this.getFontMetrics(BASE_FONT).stringWidth(text);
		int height = this.getFontMetrics(BASE_FONT).getHeight();
		if (width == 0 || height == 0)
			return BASE_FONT;
		float scaleFactorWidth  = rect.width  / width;
		float scaleFactorHeight = rect.height / height;
		float scaledWidth  = (scaleFactorWidth  * size);
		float scaledHeight = (scaleFactorHeight * size);
		return BASE_FONT.deriveFont( (scaleFactorHeight > scaleFactorWidth)
						? scaledWidth
						: scaledHeight);
	}

	private void copyToClipBoard() {
		ClipBoard.getInstance().clear();
		crops.stream().filter(DrawableCropRect::isSelected).forEach(crop -> ClipBoard.getInstance().addCrop(crop));
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	private void pasteFromClipBoard() {
		ClipBoard.getInstance().getCrops().stream().filter(crop -> !crops.contains(crop)).forEach(crop -> crops.add(new DrawableCropRect(crop)));
		ClipBoard.getInstance().clear();
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	private void alignSelected(Point p) {
		for (DrawableCropRect crop : crops) {
			if (crop.contains(p)) {
				briss.alignSelRects(crop.x, crop.y, crop.width, crop.height);
				break;
			}
		}
	}

	private void deleteAllSelected() {
		crops.removeIf( crop -> crop.isSelected() );
		xlatCropRectsToCropRatios(crops);
		repaint();
	}

	private void removeAnyCropEntirelyBeyondVisibleArea() {
		/* KG 2016/3/1 new policy here:
			do not molest (clip) crops until they've been moved entirely out of
			the visible area at which point they are deleted.
			rationale:
			 	- if a crop moves (partially) outside the visible area,
				- user may have simply "overshot" during movement phase;
				- let him recover via reciprocal move,
				- but if crop sizes have been molested by the move, this becomes an onerous chore
				- so don't molest his crops' sizes until he has "driven them off the cliff"
		 */
		crops.removeIf(item -> {
			int ulcX = item.x;
			int ulcY = item.y;
			int lrcX = ulcX + item.width;
			int lrcY = ulcY + item.height;
			return	   lrcY <= 0
					|| lrcX <= 0
					|| ulcX >= getWidth()
					|| ulcY >= getHeight()
					;
		});
	}

	private void clipCropsToVisibleArea() {
		// clip to visible area
		for (Rectangle crop : crops) {
			if (crop.x < 0) {
				crop.width -= -crop.x;
				crop.x = 0;
			}
			if (crop.y < 0) {
				crop.height -= -crop.y;
				crop.y = 0;
			}
			if (crop.x + crop.width > getWidth()) {
				crop.width = getWidth() - crop.x;
			}
			if (crop.y + crop.height > getHeight()) {
				crop.height = getHeight() - crop.y;
			}
		}
	}

	private void removeTooSmallCrops() {
		// throw away all crops which are too small
		crops.removeIf(crop -> { return
			   crop.getWidth()  < 2 * DrawableCropRect.CORNER_DIMENSION
			|| crop.getHeight() < 2 * DrawableCropRect.CORNER_DIMENSION;
		} );
	}

	private class MergedPanelKeyAdapter extends KeyAdapter {

		@Override
		public void keyTyped(KeyEvent e) {}

		@Override
		public void keyReleased(KeyEvent e) {}

		@Override
		public void keyPressed(KeyEvent e) {
			switch (e.getKeyCode()) {
			case KeyEvent.VK_C:
				if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
					copyToClipBoard();
				} break;
			case KeyEvent.VK_V:
				if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
					pasteFromClipBoard();
				} break;
			case KeyEvent.VK_DELETE:	deleteAllSelected(); break;
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
				int x = 0;
				int y = 0;
				switch (e.getKeyCode()) {
					case KeyEvent.VK_LEFT:	x = -1; break;
					case KeyEvent.VK_RIGHT:	x =  1; break;
					case KeyEvent.VK_UP:	y = -1; break;
					case KeyEvent.VK_DOWN:	y =  1; break;
				}
				if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
					x *= 10;
					y *= 10;
				}
				if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
					briss.resizeSelectedRects(x, y);
				}
				else /* if ((e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) */ {
					briss.moveSelectedRects(x, y);
				}
				e.consume(); // prevent framework from taking further action on these events
				break;
			default:
		}
		}
	}

	private class MergedPanelMouseAdapter extends MouseAdapter implements ActionListener {

		@Override
		public void mouseMoved(MouseEvent e) {
			if (MergedPanel.this.contains(e.getPoint())) {
				MergedPanel.this.requestFocusInWindow();
			}
		}

		public void actionPerformed(ActionEvent e) {
			switch( e.getActionCommand() ) {
			case PopUpMenuForCropRectangles.DELETE: {
				crops.removeIf( crop -> crop.contains(popUpMenuPoint) );
				xlatCropRectsToCropRatios(crops);
				repaint();
				} break;
			case PopUpMenuForCropRectangles.SPLIT: {
				Point pt = popUpMenuPoint;
				String[] options;
				options = new String[]{"At point", "Equally"};
				int optionAtPoint = JOptionPane.showOptionDialog(MergedPanel.this, "Split Type", "Split Type", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
				if (optionAtPoint == JOptionPane.CLOSED_OPTION) return;
				options = new String[]{"Horizontal", "Vertical"};
				int optionHOrV = JOptionPane.showOptionDialog(MergedPanel.this, "Split Direction", "Split Direction", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
				if (optionHOrV == JOptionPane.CLOSED_OPTION) return;
				int n = 0;
				if (optionAtPoint == 1) {
					String nstr = JOptionPane.showInputDialog(MergedPanel.this, "Cells", "2");
					if (nstr == null) return;
					n = Integer.valueOf(nstr);
					if (n <= 1) return;
				}
				int overlap;
				{
					String nstr = JOptionPane.showInputDialog(MergedPanel.this, "Overlap", "0");
					if (nstr == null) return;
					overlap = Integer.valueOf(nstr);
					overlap = (int) Math.ceil(overlap / 2.0);
				}
				for (ListIterator<DrawableCropRect> iter = crops.listIterator(); iter.hasNext(); ) {
					DrawableCropRect crop = iter.next();
					if (crop.contains(pt)) {
						List<Integer> splits = new ArrayList<>();
						if (optionAtPoint == 0)
							splits.add(optionHOrV == 0 ? pt.x : pt.y);
						else {
							float[][] params = new float[][]{{crop.x, crop.width}, {crop.y, crop.height}};
							for (int i = 1; i < n; i++) {
								splits.add(Math.round((params[optionHOrV][0] + overlap) + i * (params[optionHOrV][1] - 2 * overlap) / n));
							}
						}
						List<DrawableCropRect> rs = crop.split(splits, overlap, optionHOrV == 0);
						iter.remove();
						rs.forEach(iter::add);
					}
				}
				xlatCropRectsToCropRatios(crops);
				repaint();
				} break;
			case PopUpMenuForCropRectangles.SELECT_DESELECT: toggleCropSelectionUnderPt(popUpMenuPoint); break;
			case PopUpMenuForCropRectangles.COPY:			copyToClipBoard(); break;
			case PopUpMenuForCropRectangles.PASTE: 			pasteFromClipBoard(); break;
			case PopUpMenuForCropRectangles.ALIGN_SELECTED: alignSelected(popUpMenuPoint); break;
		}
		}

		@Override
		public void mouseDragged(MouseEvent mE) {
			Point curPoint = mE.getPoint();
			switch (actionState) {
			case DRAWING_NEW_CROP:
				if (cropStartPoint == null) {
					cropStartPoint = curPoint;
				}
				curCrop.x      = Math.min(curPoint.x , cropStartPoint.x);
				curCrop.width  = Math.abs(curPoint.x - cropStartPoint.x);
				curCrop.y      = Math.min(curPoint.y , cropStartPoint.y);
				curCrop.height = Math.abs(curPoint.y - cropStartPoint.y);
				break;
			case MOVE_CROP:
				if (lastDragPoint == null) {
					lastDragPoint = curPoint;
				}
				if (mE.isShiftDown()) {
					briss.moveSelectedRects(curPoint.x - lastDragPoint.x,
											curPoint.y - lastDragPoint.y);
				}
				else {
					curCrop.translate(curPoint.x - lastDragPoint.x,
									  curPoint.y - lastDragPoint.y);
				}
				lastDragPoint = curPoint;
				break;
			case RESIZING_HOTCORNER_LR:
				if (lastDragPoint == null) {
					lastDragPoint = curPoint;
				}
				if (mE.isShiftDown()) {
					briss.resizeSelectedRects(curPoint.x - lastDragPoint.x,
											  curPoint.y - lastDragPoint.y);
				}
				else {
					curPoint.translate(relativeHotCornerGrabDistance.x,
								  	   relativeHotCornerGrabDistance.y);
					curCrop.setNewHotCornerLR(curPoint);
				}
				lastDragPoint = curPoint;
				break;
			case RESIZING_HOTCORNER_UL:
				if (lastDragPoint == null) {
					lastDragPoint = curPoint;
				}
				if (mE.isShiftDown()) {
					briss.resizeSelectedRects(lastDragPoint.x - curPoint.x,
							   				  lastDragPoint.y - curPoint.y);
					briss.moveSelectedRects(curPoint.x - lastDragPoint.x,
										    curPoint.y - lastDragPoint.y);
				}
				else {
					curPoint.translate(relativeHotCornerGrabDistance.x,
									   relativeHotCornerGrabDistance.y);
					curCrop.setNewHotCornerUL(curPoint);
				}
				lastDragPoint = curPoint;
				break;
			}
			repaint();
		}

		@Override
		public void mousePressed(MouseEvent mE) {
			Point p = mE.getPoint();
			if (mE.isPopupTrigger()) {
				showPopUpMenu(mE);
			}
			if (mE.isControlDown()) {
				toggleCropSelectionUnderPt(p);
				return;
			}
			if (SwingUtilities.isLeftMouseButton(mE)) {
				// check if any of the upper left hotcorners are used
				for (DrawableCropRect crop : crops) {
					if (crop.containsInHotCornerUL(p)) {
						actionState = ActionState.RESIZING_HOTCORNER_UL;
						relativeHotCornerGrabDistance = new Point(crop.x - p.x, crop.y - p.y);
						curCrop = crop;
						return;
					}
				}
				// check if any of the lower right hotcorners are used
				for (DrawableCropRect crop : crops) {
					if (crop.containsInHotCornerLR(p)) {
						actionState = ActionState.RESIZING_HOTCORNER_LR;
						relativeHotCornerGrabDistance = new Point(crop.x + crop.width - p.x, crop.y + crop.height - p.y);
						curCrop = crop;
						return;
					}
				}
				// check if the crop should be moved
				for (DrawableCropRect crop : crops) {
					if (crop.contains(p)) {
						actionState = ActionState.MOVE_CROP;
						curCrop = crop;
						return;
					}
				}
				// otherwise draw a new one
				actionState = ActionState.DRAWING_NEW_CROP;
				if (curCrop == null) {
					curCrop = new DrawableCropRect();
					crops.add(curCrop);
					cropStartPoint = p;
				}
			}
		}

		@Override
		public void mouseReleased(MouseEvent mE) {
			if (mE.isPopupTrigger()) {
				showPopUpMenu(mE);
			}
			clipCropsToVisibleArea();
			removeTooSmallCrops();
			xlatCropRectsToCropRatios(crops);
			actionState = ActionState.NOTHING;
			cropStartPoint = null;
			lastDragPoint = null;
			curCrop = null;
			repaint();
		}

		private void showPopUpMenu(MouseEvent e) {
			popUpMenuPoint = e.getPoint();
			new PopUpMenuForCropRectangles().show(e.getComponent(), e.getX(), e.getY());
		}

		private class PopUpMenuForCropRectangles extends JPopupMenu {
			public static final String DELETE = "Delete rectangle";
			public static final String SPLIT = "Split rectangle";
			public static final String SELECT_DESELECT = "Select/Deselect rectangle";
			public static final String COPY = "Copy Selected rectangles";
			public static final String PASTE = "Paste rectangles";
			public static final String ALIGN_SELECTED = "Align selected rectangles";

			private void addJMI( String st, boolean enable ) {
				JMenuItem jmi = new JMenuItem(st);
				jmi.addActionListener(MergedPanelMouseAdapter.this);
				jmi.setEnabled( enable );
				add(jmi);
			}

			public PopUpMenuForCropRectangles() {
				boolean isContainedInRectangle = false;
				for (DrawableCropRect crop : crops) {
					if (crop.contains(popUpMenuPoint)) {
						isContainedInRectangle = true;
					}
				}
				if (isContainedInRectangle) {
					addJMI( DELETE, false );
					addJMI( SPLIT , false );
					addJMI( SELECT_DESELECT, false );
				}
				boolean copyPossible = false;
				for (DrawableCropRect crop : crops) {
					if (crop.isSelected()) {
						copyPossible = true;
					}
				}
				addJMI( COPY , copyPossible );
				addJMI( PASTE, ClipBoard.getInstance().getAmountOfCropsInClipBoard() > 0 );
				addJMI( ALIGN_SELECTED, true );
			}
		}
	}
}
