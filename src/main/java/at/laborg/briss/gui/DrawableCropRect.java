/**
 * 
 */
package at.laborg.briss.gui;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class DrawableCropRect extends Rectangle {

	static final int CORNER_DIMENSION = 20;

	private boolean selected = false;

	public DrawableCropRect()                            { super(); }
	public DrawableCropRect(final DrawableCropRect crop) { super(crop.x, crop.y, crop.width, crop.height); } // copy ctor
	public DrawableCropRect(int x, int y, int w, int h)  { super(     x,      y,      w,          h     ); }
	
	public final List<DrawableCropRect> split (List<Integer> at, int olap2 /* half-overlap */, boolean onX) {
		List<DrawableCropRect> result = new ArrayList<>();
		at = new ArrayList<>(at);
		// first and last rectangle have extra overlap
		int prev = (onX ? x : y) + olap2;
		at.add((onX ? x + width : y + height) - olap2);
		for (int p : at) {
			DrawableCropRect r = new DrawableCropRect (this);
			int d = p - prev + 2 * olap2;
			prev -= olap2;
			if (onX) { r.x = prev; r.width  = d; }
			else     { r.y = prev; r.height = d; }
			prev = p;
			result.add (r);
		}
		return result;
	}

	public final boolean isSelected() {
		return selected;
	}

	public final void setSelected(final boolean selected) {
		this.selected = selected;
	}

	public final void setNewHotCornerUL(final Point p) {
		int xLR = (int) getMaxX();
		int yLR = (int) getMaxY();
		setSize(xLR - p.x, yLR - p.y);
		x = p.x;
		y = p.y;
	}

	public final void setNewHotCornerLR(final Point p) {
		setSize(p.x - x, p.y - y);
	}

	public final boolean containsInHotCornerUL(final Point p) {
		return (   (p.x > getX() && p.x <= getX() + CORNER_DIMENSION)
				&& (p.y > getY() && p.y <= getY() + CORNER_DIMENSION));
	}

	public final boolean containsInHotCornerLR(final Point p) {
		return (   (p.x < getMaxX() && p.x > getMaxX() - CORNER_DIMENSION)
				&& (p.y < getMaxY() && p.y > getMaxY() - CORNER_DIMENSION));
	}
}