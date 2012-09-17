///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2011 Assaf Urieli
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
package com.joliciel.jochre.boundaries;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import mockit.NonStrict;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.jochre.JochreServiceLocator;
import com.joliciel.jochre.doc.JochreDocument;
import com.joliciel.jochre.doc.JochrePage;
import com.joliciel.jochre.graphics.GraphicsService;
import com.joliciel.jochre.graphics.GroupOfShapes;
import com.joliciel.jochre.graphics.JochreImage;
import com.joliciel.jochre.graphics.Paragraph;
import com.joliciel.jochre.graphics.RowOfShapes;
import com.joliciel.jochre.graphics.Shape;

import org.junit.Test;

public class TrainingCorpusShapeSplitterTest {
	private static final Log LOG = LogFactory.getLog(TrainingCorpusShapeSplitterTest.class);
	private JochreServiceLocator locator = null;

	@Test
	public void testSplit(@NonStrict final Shape shape,
			@NonStrict final Shape shape1,
			@NonStrict final Shape shape2,
			@NonStrict final Shape shape3,
			@NonStrict final Shape shape4) throws IOException {
		locator = JochreServiceLocator.getInstance();
		locator.setDataSourcePropertiesResource("jdbc-live.properties");
		
		GraphicsService graphicsService = locator.getGraphicsServiceLocator().getGraphicsService();
		BoundaryServiceInternal boundaryService = (BoundaryServiceInternal) locator.getBoundaryServiceLocator().getBoundaryService();
		
		new NonStrictExpectations() {
			GroupOfShapes group;
			RowOfShapes row;
			Paragraph paragraph;
			JochreImage jochreImage;
			JochrePage jochrePage;
			JochreDocument jochreDocument;

			Iterator<Split> i;
			List<Split> splits;
			Split split1, split2, split3;

			{
				shape.getLetter(); returns("אָבּער");
				shape.getLeft(); returns(100);
				shape.getRight(); returns(200);
				shape.getTop(); returns(100);
				shape.getBottom(); returns(200);
				
				shape.getGroup(); returns(group);
				shape.getJochreImage(); returns(jochreImage);
				
				group.getRow(); returns(row);
				row.getParagraph(); returns(paragraph);
				paragraph.getImage(); returns(jochreImage);
				jochreImage.getPage(); returns(jochrePage);
				jochrePage.getDocument(); returns(jochreDocument);
				jochreDocument.getLocale(); returns(new Locale("yi"));
				
				shape.getSplits(); returns(splits);
				splits.iterator(); returns(i);
				
				i.hasNext(); returns(true, true, true, false);
				i.next(); returns(split1, split2, split3);
				
				split1.getPosition(); returns(35);
				split2.getPosition(); returns(59);
				split3.getPosition(); returns(82);

				jochreImage.getShape(100, 100, 135, 200); returns (shape1);
				jochreImage.getShape(136, 100, 159, 200); returns (shape2);
				jochreImage.getShape(160, 100, 182, 200); returns (shape3);
				jochreImage.getShape(183, 100, 200, 200); returns (shape4);
			}
		};

		LOG.debug(shape);
		LOG.debug(shape.getLetter());
		LOG.debug("Split into: ");
		TrainingCorpusShapeSplitter splitter = new TrainingCorpusShapeSplitter();
		splitter.setGraphicsService(graphicsService);
		splitter.setBoundaryServiceInternal(boundaryService);
		List<ShapeSequence> result = splitter.split(shape);
		ShapeSequence shapeSequence = result.get(0);
		assertEquals(4, shapeSequence.size());
		
		new Verifications() {
			{
				shape1.setLetter("אָ");
				shape2.setLetter("בּ");
				shape3.setLetter("ע");
				shape4.setLetter("ר");
			}
		};
	}


}
