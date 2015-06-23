///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2014 Assaf Urieli
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
package com.joliciel.jochre.search;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import com.joliciel.jochre.search.alto.AltoDocument;
import com.joliciel.jochre.search.alto.AltoPage;
import com.joliciel.jochre.search.alto.AltoReader;
import com.joliciel.jochre.search.alto.AltoService;
import com.joliciel.jochre.search.alto.AltoString;
import com.joliciel.jochre.search.alto.AltoTextBlock;
import com.joliciel.jochre.search.alto.AltoTextLine;
import com.joliciel.talismane.utils.LogUtils;


class JochreIndexBuilderImpl implements JochreIndexBuilder, TokenExtractor {
	private static final Log LOG = LogFactory.getLog(JochreIndexBuilderImpl.class);
	private File indexDir;
	private int wordsPerDoc=3000;
	private IndexWriter indexWriter;
	
	private SearchServiceInternal searchService;
	private AltoService altoService;
	private List<AltoString> currentStrings = null;
	
	public JochreIndexBuilderImpl(File indexDir) {
		this.indexDir = indexDir;
	}
	
	private void initialise() {
		if (this.indexWriter==null) {
			try {
				Directory directory = FSDirectory.open(this.indexDir);
				
				Analyzer analyzer = searchService.getJochreAnalyser(this);
				IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_46, analyzer);
				iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
				this.indexWriter = new IndexWriter(directory, iwc);
			} catch (IOException ioe) {
				LogUtils.logError(LOG, ioe);
				throw new RuntimeException(ioe);
			}
		}
	}
	
	public void updateIndex(File contentDir, boolean forceUpdate) {
		long startTime = System.currentTimeMillis();
		try {
			this.initialise();
			File[] subdirs = contentDir.listFiles(new FileFilter() {
				
				@Override
				public boolean accept(File pathname) {
					return pathname.isDirectory();
				}
			});
			
			for (File subdir : subdirs) {
				this.processDocument(subdir, forceUpdate);
			}
			
			indexWriter.commit();
			indexWriter.close();

		} catch (IOException e) {
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		} finally {
			long endTime = System.currentTimeMillis();
			long totalTime = endTime - startTime;
			LOG.info("Total time (ms): " + totalTime);
		}
	}

	@Override
	public void updateDocument(File documentDir) {
		long startTime = System.currentTimeMillis();
		try {
			this.initialise();
			this.updateDocumentInternal(documentDir);
			indexWriter.commit();
			indexWriter.close();
		} catch (IOException e) {
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		} finally {
			long endTime = System.currentTimeMillis();
			long totalTime = endTime - startTime;
			LOG.info("Total time (ms): " + totalTime);
		}
	}

	public void deleteDocument(File documentDir) {
		try {
			this.initialise();
			this.deleteDocumentInternal(documentDir);
			File lastIndexDateFile = new File(documentDir, "indexDate.txt");
			if (lastIndexDateFile.exists())
				lastIndexDateFile.delete();
			indexWriter.commit();
			indexWriter.close();
		} catch (IOException e) {
			LogUtils.logError(LOG, e);
			throw new RuntimeException(e);
		}
	}
	
	private void processDocument(File documentDir, boolean forceUpdate) {
		try {
			File instructionsFile = new File(documentDir, "instructions.txt");
			boolean updateIndex = false;
			if (instructionsFile.exists()) {
				String instructions = null;
				Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(instructionsFile), "UTF-8")));
				while (scanner.hasNextLine()) {
					instructions = scanner.nextLine();
					break;
				}
				scanner.close();
				
				LOG.info("Instructions: " + instructions + " for " + documentDir.getName());
				if (instructions.equals("delete")) {
					this.deleteDocumentInternal(documentDir);
					File lastIndexDateFile = new File(documentDir, "indexDate.txt");
					if (lastIndexDateFile.exists())
						lastIndexDateFile.delete();

					return;
				} else if (instructions.equals("skip")) {
					return;
				} else if (instructions.equals("update")) {
					updateIndex = true;
				} else {
					LOG.info("Unknown instructions.");
				}
			}
			
			File zipFile = new File(documentDir, documentDir.getName() + ".zip");
			if (!zipFile.exists()) {
				LOG.info("Nothing to index in " + documentDir.getName());
				return;
			}
			
			File metaDataFile = new File(documentDir, "metadata.txt");
			if (!metaDataFile.exists()) {
				LOG.info("Skipping: OCR analysis incomplete for " + documentDir.getName());
				return;
			}

			if (forceUpdate)
				updateIndex = true;
			
			if (!updateIndex) {
				
				LOG.debug("Checking last update date on " + documentDir.getName());
				long zipDate = zipFile.lastModified();
				
				File lastIndexDateFile = new File(documentDir, "indexDate.txt");
				
				long lastIndexDate = Long.MIN_VALUE;
				
				if (lastIndexDateFile.exists()) {
					Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(lastIndexDateFile), "UTF-8")));
					while (scanner.hasNextLine()) {
						lastIndexDate = Long.parseLong(scanner.nextLine());
						break;
					}
					scanner.close();
				}
				if (zipDate>lastIndexDate)
					updateIndex = true;
			}
			
			if (updateIndex) {
				this.updateDocumentInternal(documentDir);
			} else {
				LOG.info("Index for " + documentDir.getName() + "already up-to-date.");
			} // should update index?
			
		} catch (IOException ioe) {
			LogUtils.logError(LOG, ioe);
			throw new RuntimeException(ioe);
		}
	}
	
	private void updateDocumentInternal(File documentDir) {
		try {
			LOG.info("Updating index for " + documentDir.getName());
			
			File zipFile = new File(documentDir, documentDir.getName() + ".zip");
			if (!zipFile.exists()) {
				LOG.info("Nothing to index in " + documentDir.getName());
				return;
			}
			long zipDate = zipFile.lastModified();
	
			this.deleteDocumentInternal(documentDir);
			
			int i = 0;
			
			Map<String,String> fields = new TreeMap<String, String>();
			File metaDataFile = new File(documentDir, documentDir.getName() + "_metadata.txt");
			Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(metaDataFile), "UTF-8")));
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				String key = line.substring(0, line.indexOf('\t'));
				String value = line.substring(line.indexOf('\t'));
				fields.put(key, value);
			}
			scanner.close();
			
			AltoDocument altoDoc = this.altoService.newDocument(documentDir.getName());
			AltoReader reader = this.altoService.getAltoReader(altoDoc);
			
			ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
			ZipEntry ze = null;
		    while ((ze = zis.getNextEntry()) != null) {
				LOG.debug("Adding zipEntry " + i + ": " + ze.getName());
				String baseName = ze.getName().substring(0, ze.getName().lastIndexOf('.'));
				UnclosableInputStream uis = new UnclosableInputStream(zis);
				reader.parseFile(uis, baseName);
		    	i++;
		    }
		    zis.close();
			
			i = 0;
			int docCount = 0;
			int wordCount = 0;
			int cumulWordCount = 0;
			currentStrings = new ArrayList<AltoString>();
			List<AltoPage> currentPages = new ArrayList<AltoPage>();
			for (AltoPage page : altoDoc.getPages()) {
				int remainingWords = altoDoc.wordCount() - (cumulWordCount + wordCount);
				LOG.debug("Word count: " + wordCount + ", cumul word count: " + cumulWordCount + ", total xml words: " + altoDoc.wordCount() + ", remaining words: " + remainingWords);
				if (wordsPerDoc>0 && wordCount >= wordsPerDoc && remainingWords >= wordsPerDoc) {
					LOG.debug("Creating new index doc: " + docCount);
					JochreIndexDocument indexDoc = searchService.newJochreIndexDocument(documentDir, docCount, currentPages, fields);
					indexDoc.save(indexWriter);
					docCount++;
					
					cumulWordCount += wordCount;
					wordCount = 0;
					currentStrings = new ArrayList<AltoString>();
					currentPages = new ArrayList<AltoPage>();
				}
				currentPages.add(page);
				
				LOG.debug("Processing page: " + page.getPageIndex());
				
				for (AltoTextBlock textBlock : page.getTextBlocks()) {
					for (AltoTextLine textLine : textBlock.getTextLines()) {
						for (AltoString string : textLine.getStrings()) {
							currentStrings.add(string);
							wordCount++;
						}
					}
				}
				i++;
			}
			JochreIndexDocument indexDoc = searchService.newJochreIndexDocument(documentDir, docCount, currentPages, fields);
			indexDoc.save(indexWriter);
			
			File lastIndexDateFile = new File(documentDir, "indexDate.txt");
	
			Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(lastIndexDateFile, false),"UTF8"));
			writer.write("" + zipDate);
			writer.flush();
			
			writer.close();
		} catch (IOException ioe) {
			LogUtils.logError(LOG, ioe);
			throw new RuntimeException(ioe);
		}
	}

	private void deleteDocumentInternal(File documentDir) {
		try {
			Term term = new Term("name", documentDir.getName());
			indexWriter.deleteDocuments(term);
		} catch (IOException ioe) {
			LogUtils.logError(LOG, ioe);
			throw new RuntimeException(ioe);
		}
	}
	
	public SearchServiceInternal getSearchService() {
		return searchService;
	}

	public void setSearchService(SearchServiceInternal searchService) {
		this.searchService = searchService;
	}

	public int getWordsPerDoc() {
		return wordsPerDoc;
	}

	public void setWordsPerDoc(int wordsPerDoc) {
		this.wordsPerDoc = wordsPerDoc;
	}

	public AltoService getAltoService() {
		return altoService;
	}

	public void setAltoService(AltoService altoService) {
		this.altoService = altoService;
	}

	@Override
	public List<AltoString> findTokens(String fieldName, Reader input) {
		return currentStrings;
	}

	
	
}
