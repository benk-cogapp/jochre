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
package com.joliciel.jochre.doc;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.joliciel.jochre.doc.JochreDocument;
import com.joliciel.jochre.doc.SourceFileProcessor;
import com.joliciel.jochre.doc.JochrePage;
import com.joliciel.talismane.utils.util.LogUtils;
import com.joliciel.talismane.utils.util.Monitorable;
import com.joliciel.talismane.utils.util.MultiTaskProgressMonitor;
import com.joliciel.talismane.utils.util.ProgressMonitor;


class ImageDocumentExtractorImpl implements ImageDocumentExtractor  {
	private static final Log LOG = LogFactory.getLog(ImageDocumentExtractorImpl.class);
	SourceFileProcessor documentProcessor;
	MultiTaskProgressMonitor currentMonitor;
	File imageFile;

	public ImageDocumentExtractorImpl(File imageFile,
			SourceFileProcessor documentProcessor) {
		this.documentProcessor = documentProcessor;	
		this.imageFile = imageFile;
	}
	
	
	
	@Override
	public void run() {
		this.extractDocument();
	}

	/* (non-Javadoc)
	 * @see com.joliciel.jochre.doc.ImageDocumentExtractor#extractDocument(java.io.File, com.joliciel.jochre.doc.SourceFileProcessor)
	 */
	@Override
	public JochreDocument extractDocument() {
		try {
			JochreDocument doc = this.documentProcessor.onDocumentStart();
			JochrePage page = this.documentProcessor.onPageStart(1);
			
			BufferedImage image = ImageIO.read(imageFile);
			String imageName = imageFile.getName();
			if (currentMonitor!=null&&documentProcessor instanceof Monitorable) {
				ProgressMonitor monitor = ((Monitorable)documentProcessor).monitorTask();
				currentMonitor.startTask(monitor, 1);
			}
			documentProcessor.onImageFound(page, image, imageName, 0);
			if (currentMonitor!=null&&documentProcessor instanceof Monitorable) {
				currentMonitor.endTask();
			}
			
			this.documentProcessor.onPageComplete(page);
			this.documentProcessor.onDocumentComplete(doc);
			
			if (currentMonitor!=null)
				currentMonitor.setFinished(true);
			return doc;
		} catch (Exception e) {
			if (currentMonitor!=null)
				currentMonitor.setException(e);
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public ProgressMonitor monitorTask() {
		currentMonitor = new MultiTaskProgressMonitor();
		
		return currentMonitor;
	}

    
}
