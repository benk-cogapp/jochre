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
package com.joliciel.jochre.graphics.features;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.jochre.graphics.Shape;
import com.joliciel.jochre.graphics.ShapeWrapper;
import com.joliciel.talismane.utils.features.BooleanFeature;
import com.joliciel.talismane.utils.features.FeatureResult;

/**
 * Is there an opening on the bottom of the shape.
 * Useful for distinguishing Mem from Tes in the Hebrew alphabet.
 * @author Assaf Urieli
 *
 */
public class OpeningOnBottomFeature extends AbstractShapeFeature<Boolean> implements BooleanFeature<ShapeWrapper> {
	private static final Log LOG = LogFactory.getLog(OpeningOnBottomFeature.class);

	@Override
	public FeatureResult<Boolean> checkInternal(ShapeWrapper shapeWrapper) {
		Shape shape = shapeWrapper.getShape();
		int leftPoint = (int) ((double) shape.getWidth() * (1.0 / 20.0));
		int rightPoint = (int) ((double) shape.getWidth() * (7.0 / 8.0));
		int openingThreshold = shape.getHeight() - (shape.getHeight() / 4) - 1;
		int wallThreshold = shape.getHeight() - (shape.getHeight() / 7) - 1;
		
		if (LOG.isTraceEnabled()) {
			LOG.trace("leftPoint: " + leftPoint);
			LOG.trace("rightPoint: " + rightPoint);
			LOG.trace("openingThreshold: " + openingThreshold);
			LOG.trace("wallThreshold: " + wallThreshold);
		}
		boolean foundWall = false;
		boolean foundOpening = false;
		boolean foundAnotherWall = false;
		for (int x = rightPoint; x >= leftPoint; x--) {
			for (int y = shape.getHeight(); y >= openingThreshold; y--) {
				if (!foundWall && y < wallThreshold) {
					break;
				}
				else if (!foundWall && shape.isPixelBlack(x, y, shape.getJochreImage().getBlackThreshold())) {
					foundWall = true;
					if (LOG.isTraceEnabled())
						LOG.trace("foundWall x=" + x + ", y=" + y);
					break;
				}
				else if (foundWall && !foundOpening && shape.isPixelBlack(x, y, shape.getJochreImage().getBlackThreshold())) {
					break;
				}
				else if (foundWall && !foundOpening && y == openingThreshold) {
					foundOpening = true;
					if (LOG.isTraceEnabled())
						LOG.trace("foundOpening x=" + x + ", y=" + y);
					break;
				}
				else if (foundOpening && !foundAnotherWall && y<= wallThreshold) {
					break;
				}
				else if (foundOpening && !foundAnotherWall && shape.isPixelBlack(x, y, shape.getJochreImage().getBlackThreshold())) {
					foundAnotherWall = true;
					if (LOG.isTraceEnabled())
						LOG.trace("foundAnotherWall x=" + x + ", y=" + y);
					break;
				}
			}
			if (foundAnotherWall)
				break;
		}
		
		FeatureResult<Boolean> outcome = this.generateResult(foundAnotherWall);
		return outcome;
	}
}
