package com.joliciel.jochre.web;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.util.media.Media;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.ForwardEvent;
import org.zkoss.zk.ui.event.UploadEvent;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zul.Button;
import org.zkoss.zul.Div;
import org.zkoss.zul.Fileupload;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Groupbox;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.Html;
import org.zkoss.zul.Label;
import org.zkoss.zul.Panel;
import org.zkoss.zul.Progressmeter;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Timer;
import org.zkoss.zul.Window;

import com.joliciel.jochre.JochreSession;
import com.joliciel.jochre.doc.DocumentDao;
import com.joliciel.jochre.doc.DocumentObserver;
import com.joliciel.jochre.doc.ImageDocumentExtractor;
import com.joliciel.jochre.doc.JochreDocument;
import com.joliciel.jochre.doc.JochreDocumentGenerator;
import com.joliciel.jochre.doc.JochrePage;
import com.joliciel.jochre.graphics.GraphicsDao;
import com.joliciel.jochre.graphics.JochreImage;
import com.joliciel.jochre.lexicon.MostLikelyWordChooser;
import com.joliciel.jochre.output.TextGetter;
import com.joliciel.jochre.output.TextGetter.TextFormat;
import com.joliciel.jochre.pdf.PdfImageVisitor;
import com.joliciel.jochre.security.User;
import com.joliciel.talismane.utils.LogUtils;
import com.joliciel.talismane.utils.MessageResource;
import com.joliciel.talismane.utils.ProgressMonitor;

public class TextController extends GenericForwardComposer<Window> {
  private static final long serialVersionUID = 1L;

  private static final Logger LOG = LoggerFactory.getLogger(TextController.class);
  private final JochreSession jochreSession;

  private JochreDocument currentDoc;
  private JochreImage currentImage;
  private User currentUser;
  private File currentFile;
  private JochreDocumentGenerator documentGenerator;
  private DocumentHtmlGenerator documentHtmlGenerator;
  private ProgressMonitor progressMonitor;

  private int currentHtmlIndex = 0;

  @Wire
  Window winJochreText;
  @Wire
  Grid documentGrid;
  @Wire
  Grid gridPages;
  @Wire
  Label lblDocName;
  @Wire
  Div htmlContent;
  @Wire
  Timer startRenderTimer;
  @Wire
  Progressmeter progressMeter1;
  @Wire
  Hlayout progressBox;
  @Wire
  Panel uploadPanel;
  @Wire
  Fileupload fileUpload1;
  @Wire
  Textbox txtStartPage;
  @Wire
  Textbox txtEndPage;
  @Wire
  Button btnAnalyse;
  @Wire
  Button btnDone;
  @Wire
  Button btnInterrupt;
  @Wire
  Button btnUpload;
  @Wire
  Label lblAwaitingFile;
  @Wire
  Label lblFileName;
  @Wire
  Label lblCurrentAction;
  @Wire
  Timer progressTimer;
  @Wire
  Groupbox errorBox;
  @Wire
  Label lblErrorMessage;

  Thread currentThread = null;

  int currentPageIndex = 0;

  public TextController() throws ReflectiveOperationException {
    jochreSession = JochreProperties.getInstance().getJochreSession();
  }

  @Override
  public void doAfterCompose(Window window) throws Exception {
    try {
      super.doAfterCompose(window);
      Session session = Sessions.getCurrent();
      currentUser = (User) session.getAttribute(LoginController.SESSION_JOCHRE_USER);
      if (currentUser == null)
        Executions.sendRedirect("login.zul");

      HttpServletRequest request = (HttpServletRequest) Executions.getCurrent().getNativeRequest();
      if (request.getParameter("imageId") != null) {
        int imageId = Integer.parseInt(request.getParameter("imageId"));
        GraphicsDao graphicsDao = GraphicsDao.getInstance(jochreSession);
        currentImage = graphicsDao.loadJochreImage(imageId);
        currentDoc = currentImage.getPage().getDocument();
        uploadPanel.setVisible(false);
        progressBox.setVisible(true);
        startRenderTimer.setRunning(true);
      } else if (request.getParameter("docId") != null) {
        int docId = Integer.parseInt(request.getParameter("docId"));
        DocumentDao documentDao = DocumentDao.getInstance(jochreSession);
        currentDoc = documentDao.loadJochreDocument(docId);
        if (request.getParameter("addPages") != null) {
          uploadPanel.setVisible(true);
          // progressBox.setVisible(false);
          lblAwaitingFile.setVisible(true);
        } else {
          uploadPanel.setVisible(false);
          progressBox.setVisible(true);
          startRenderTimer.setRunning(true);
        }
      } else {
        uploadPanel.setVisible(true);
        // progressBox.setVisible(false);
        lblAwaitingFile.setVisible(true);
      }

      if (currentDoc != null && !currentDoc.isLeftToRight())
        htmlContent.setSclass("rightToLeft");

      if (currentDoc != null) {
        documentGrid.setVisible(true);
        lblDocName.setValue(currentDoc.getName());
      }
    } catch (Exception e) {
      LOG.error("Failure in TextController$doAfterCompose", e);
      throw new RuntimeException(e);
    }
  }

  @Listen("onTimer = #startRenderTimer")
  public void onTimer$startRenderTimer(Event event) {
    try {
      progressBox.setVisible(true);

      if (currentImage != null) {
        Html html = new Html();
        StringWriter out = new StringWriter();
        DocumentObserver textGetter = new TextGetter(out, TextFormat.XHTML);
        textGetter.onImageComplete(currentImage);
        html.setContent(out.toString());
        htmlContent.appendChild(html);
        progressMeter1.setValue(100);
        // progressBox.setVisible(false);
      } else {
        if (currentPageIndex < currentDoc.getPages().size()) {
          JochrePage page = currentDoc.getPages().get(currentPageIndex);
          for (JochreImage image : page.getImages()) {
            Html html = new Html();
            StringWriter out = new StringWriter();
            DocumentObserver textGetter = new TextGetter(out, TextFormat.XHTML);
            textGetter.onImageComplete(image);
            out.append("<HR/>");
            html.setContent(out.toString());
            htmlContent.appendChild(html);
          }
          page.clearMemory();
          currentPageIndex++;
          double percentComplete = ((double) currentPageIndex / (double) currentDoc.getPages().size()) * 100;
          progressMeter1.setValue(new Double(percentComplete).intValue());
          startRenderTimer.setRunning(true);
        } else {
          progressMeter1.setValue(100);
          // progressBox.setVisible(false);
        }
      }
    } catch (Exception e) {
      LOG.error("Failure in onTimer$startRenderTimer", e);
      throw new RuntimeException(e);
    }

  }

  @Listen("onUpload = #btnUpload")
  public void onUpload$btnUpload(Event event) {
    try {
      LOG.debug("onUpload$btnUpload");

      ForwardEvent forwardEvent = (ForwardEvent) event;
      UploadEvent uploadEvent = (UploadEvent) forwardEvent.getOrigin();

      Media media = uploadEvent.getMedia();
      // save this to the temp file location.
      ServletContext servletContext = Executions.getCurrent().getDesktop().getWebApp().getServletContext();
      File tempDir = (File) servletContext.getAttribute("javax.servlet.context.tempdir");
      LOG.debug("Temp dir: " + tempDir.getPath());
      currentFile = new File(tempDir, media.getName());
      LOG.debug("Filename: " + media.getName());
      FileOutputStream out = new FileOutputStream(currentFile);
      InputStream in = media.getStreamData();
      byte buf[] = new byte[1024];
      int len;
      while ((len = in.read(buf)) > 0)
        out.write(buf, 0, len);
      out.close();
      in.close();
      lblFileName.setValue(media.getName());
      btnAnalyse.setDisabled(false);
      btnUpload.setDisabled(true);
      // if (media.getName().endsWith(".pdf"))
      // gridPages.setVisible(true);
      gridPages.setVisible(true);
    } catch (Exception e) {
      LOG.error("Failure in onUpload$btnUpload", e);
      throw new RuntimeException(e);
    }
  }

  @Listen("onClick = #btnAnalyse")
  public void onClick$btnAnalyse(Event event) {
    try {
      LOG.debug("onClick$btnAnalyse");
      if (currentFile != null) {
        progressBox.setVisible(true);
        lblAwaitingFile.setVisible(false);

        int startPage = txtStartPage.getValue().length() == 0 ? -1 : Integer.parseInt(txtStartPage.getValue());
        int endPage = txtEndPage.getValue().length() == 0 ? -1 : Integer.parseInt(txtEndPage.getValue());

        if (this.currentDoc != null) {
          this.currentDoc.setFileName(currentFile.getName());
          this.currentDoc.save();
          this.documentGenerator = new JochreDocumentGenerator(this.currentDoc, jochreSession);
          this.documentGenerator.requestSave(currentUser);
        } else {
          this.documentGenerator = new JochreDocumentGenerator(currentFile.getName(), "", jochreSession);
        }

        String letterModelPath = jochreSession.getLetterModelPath();
        if (letterModelPath != null) {
          MostLikelyWordChooser wordChooser = new MostLikelyWordChooser(jochreSession);
          documentGenerator.requestAnalysis(wordChooser);
        }
        this.documentHtmlGenerator = new DocumentHtmlGenerator();

        documentGenerator.addDocumentObserver(this.documentHtmlGenerator);

        String lowerCaseFileName = currentFile.getName().toLowerCase();
        Thread thread = null;
        if (lowerCaseFileName.endsWith(".pdf")) {
          PdfImageVisitor pdfImageVisitor = new PdfImageVisitor(currentFile, startPage, endPage, documentGenerator);
          this.progressMonitor = pdfImageVisitor.monitorTask();
          this.currentHtmlIndex = 0;
          thread = new Thread(pdfImageVisitor);
          thread.setName(currentFile.getName() + " Processor");
          progressTimer.setRunning(true);
        } else if (lowerCaseFileName.endsWith(".png") || lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".jpeg")
            || lowerCaseFileName.endsWith(".gif")) {
          ImageDocumentExtractor extractor = new ImageDocumentExtractor(currentFile, documentGenerator);
          if (startPage >= 0)
            extractor.setPageNumber(startPage);
          this.progressMonitor = extractor.monitorTask();
          this.currentHtmlIndex = 0;
          thread = new Thread(extractor);
          thread.setName(currentFile.getName() + " Processor");
        } else {
          throw new RuntimeException("Unrecognised file extension");
        }
        thread.start();
        currentThread = thread;
        progressTimer.setRunning(true);
        btnAnalyse.setDisabled(true);
        btnInterrupt.setVisible(true);
        btnInterrupt.setDisabled(false);
      }
    } catch (Exception e) {
      LOG.error("Failure in onClick$btnAnalyse", e);
      throw new RuntimeException(e);
    }
  }

  @Listen("onTimer = #progressTimer")
  public void onTimer$progressTimer(Event event) {
    if (this.progressMonitor != null) {
      if (this.documentGenerator != null) {
        List<Html> htmlList = this.documentHtmlGenerator.getHtmlList();
        if (currentHtmlIndex < htmlList.size()) {
          htmlContent.appendChild(htmlList.get(currentHtmlIndex));
          currentHtmlIndex++;
        }
      }
      if (this.progressMonitor.isFinished() || (currentThread != null && (currentThread.isInterrupted() || !currentThread.isAlive()))) {
        if (this.progressMonitor.getException() != null) {
          lblCurrentAction.setValue(Labels.getLabel("imageMonitor.error"));
          errorBox.setVisible(true);
          lblErrorMessage.setValue(LogUtils.getErrorString(this.progressMonitor.getException()));
        } else {
          lblCurrentAction.setValue(Labels.getLabel("imageMonitor.complete"));
        }
        progressMeter1.setValue(100);
        progressTimer.setRunning(false);
        btnInterrupt.setDisabled(true);
      } else {
        double progress = progressMonitor.getPercentComplete() * 100;
        if (progress > 100)
          progress = 100;
        progressMeter1.setValue((int) Math.round(progress));
        List<MessageResource> messages = progressMonitor.getCurrentActions();
        String currentAction = "";
        boolean firstAction = true;
        for (MessageResource message : messages) {
          if (!firstAction)
            currentAction += " - ";
          currentAction += Labels.getLabel(message.getKey(), message.getArguments());
          firstAction = false;
        }
        currentAction += "...";
        lblCurrentAction.setValue(currentAction);
        LOG.debug("currentAction: " + currentAction);
        LOG.debug("progress: " + progress);
      }
    }

  }

  @Listen("onClick = #btnInterrupt")
  public void onClick$btnInterrupt(Event event) {
    try {
      LOG.debug("onClick$btnInterrupt");
      if (currentThread != null) {
        currentThread.interrupt();
        LOG.debug("Is interrupted? " + currentThread.isInterrupted());
      }
      btnInterrupt.setDisabled(true);
      lblCurrentAction.setValue(Labels.getLabel("imageMonitor.error"));
      errorBox.setVisible(true);
      lblErrorMessage.setValue("Analysis interrupted by user - database may not be in clean state - contact administrator for help.");
      progressMeter1.setValue(100);
      progressTimer.setRunning(false);
    } catch (Exception e) {
      LOG.error("Failure in onClick$btnInterrupt", e);
      throw new RuntimeException(e);
    }
  }

  @Listen("onClick = #btnDone")
  public void onClick$btnDone(Event event) {
    try {
      LOG.debug("onClick$btnDone");
      Executions.sendRedirect("docs.zul");
    } catch (Exception e) {
      LOG.error("Failure in onClick$btnDone", e);
      throw new RuntimeException(e);
    }
  }

  private class DocumentHtmlGenerator implements DocumentObserver {
    private List<Html> htmlList = new ArrayList<Html>();

    public DocumentHtmlGenerator() {
      super();
    }

    public List<Html> getHtmlList() {
      return htmlList;
    }

    @Override
    public void onDocumentStart(JochreDocument jochreDocument) {
    }

    @Override
    public void onPageStart(JochrePage jochrePage) {
    }

    @Override
    public void onImageStart(JochreImage jochreImage) {
    }

    @Override
    public void onImageComplete(JochreImage jochreImage) {
      Html html = new Html();
      StringWriter out = new StringWriter();
      DocumentObserver textGetter = new TextGetter(out, TextFormat.XHTML);
      textGetter.onImageComplete(jochreImage);
      out.append("<HR/>");
      html.setContent(out.toString());
      htmlList.add(html);
    }

    @Override
    public void onPageComplete(JochrePage jochrePage) {
    }

    @Override
    public void onDocumentComplete(JochreDocument jochreDocument) {
    }

    @Override
    public void onAnalysisComplete() {  
    }  
  }
}
