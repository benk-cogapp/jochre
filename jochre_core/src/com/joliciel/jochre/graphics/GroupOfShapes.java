///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2012 Assaf Urieli
//
//This file is part of Jochre.
//
//Jochre is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//Jochre is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Jochre.  If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.jochre.graphics;

import java.util.List;

import com.joliciel.jochre.Entity;

/**
 * A group of shapes within a row, corresponding a single orthographic word.
 * @author Assaf Urieli
 *
 */
public interface GroupOfShapes extends Entity {
	/**
	 * The shapes contained on this group.
	 */
	public List<Shape> getShapes();
	
	/**
	 * Add a shape to this group's shapes.
	 * @param shape
	 */
	public void addShape(Shape shape);
	
	/**
	 * The index of this group, from 0 (first word on row, left-most on left-to-right languages) to n.
	 * @return
	 */
	public int getIndex();
	public void setIndex(int index);
	
	public int getRowId();
	
	/**
	 * The Row containing this group.
	 * @return
	 */
	public RowOfShapes getRow();
	

	/**
	 * The leftmost x coordinate of this group (based on the shapes it contains).
	 */
	public int getLeft();
	
	/**
	 * The leftmost y coordinate of this group (based on the shapes it contains).
	 */
	public int getTop();

	/**
	 * The rightmost x coordinate of this group (based on the shapes it contains).
	 */
	public int getRight();
	
	/**
	 * The bottom-most y coordinate of this group (based on the shapes it contains).
	 */
	public int getBottom();
	
	/**
	 * The letters of the shapes comprising this group combined into a single word.
	 * @return
	 */
	public String getWord();
	
	/**
	 * Recalculate the various statistical measurements for this group.
	 * Should be called after the group has had any shapes added or removed.
	 */
	public void recalculate();
	
	
	/**
	 * Returns the meanline for this row
	 * in the form {left, top, right, bottom}
	 * @return
	 */
	public int[] getMeanLine();
	
	/**
	 * Returns the baseline for this row
	 * in the form {left, top, right, bottom}
	 * @return
	 */
	public int[] getBaseLine();

	/**
	 * Distance from base-line to mean-line.
	 * @return
	 */
	public abstract int getXHeight();

	/**
	 * If the group is at the end of a row and ends with a hyphen,
	 * is this hyphen a hard hyphen (would be in the word whether or not it was at the end of a row)
	 * or a soft hyphen (would disappear if the word was in the middle of a row).
	 * @return
	 */
	public abstract boolean isHardHyphen();
	public abstract void setHardHyphen(boolean hardHyphen);
	
	/**
	 * Set to true if this group represents a broken word, which is only partially
	 * formed by the shapes visible on the page.
	 * Broken words will be ignored for measuring word frequency.
	 * @return
	 */
	public boolean isBrokenWord();
	public void setBrokenWord(boolean brokenWord);
	
	/**
	 * Does this group border with a segmentation problem that needs to be reviewed.
	 * @return
	 */
	public boolean isSegmentationProblem();
	public void setSegmentationProblem(boolean segmentationProblem);
}
