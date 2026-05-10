/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2009 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.ui.internal;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.ui.application.IWorkbenchConfigurer;
import org.eclipse.ui.application.IWorkbenchWindowConfigurer;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.application.WorkbenchWindowAdvisor;
import org.rssowl.core.util.LoggingSafeRunnable;
import org.rssowl.core.util.SecurityUtils;
import java.io.File;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author bpasero
 */
public class ApplicationWorkbenchAdvisor extends WorkbenchAdvisor {

  /* Default Perspective */
  private static final String PERSPECTIVE_ID = "org.rssowl.ui.perspective"; //$NON-NLS-1$

  /** Keep a static reference to the primary Workbench Window Advisor */
  public static ApplicationWorkbenchWindowAdvisor fgPrimaryApplicationWorkbenchWindowAdvisor;

  private final Runnable fRunAfterUIStartup;

  /**
   * @param runAfterUIStartup A <code>Runnable</code> to be executed after the
   * UI has been started and fully created.
   */
  public ApplicationWorkbenchAdvisor(Runnable runAfterUIStartup) {
    fRunAfterUIStartup = runAfterUIStartup;

    SecurityUtils.setUnlimitedSecurity();
  }

  /*
   * @see org.eclipse.ui.application.WorkbenchAdvisor#createWorkbenchWindowAdvisor(org.eclipse.ui.application.IWorkbenchWindowConfigurer)
   */
  @Override
  public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer) {
    ApplicationWorkbenchWindowAdvisor advisor = new ApplicationWorkbenchWindowAdvisor(configurer);

    /* Store primary advisor if not yet done */
    if (fgPrimaryApplicationWorkbenchWindowAdvisor == null)
      fgPrimaryApplicationWorkbenchWindowAdvisor = advisor;

    return advisor;
  }

  /* Provide access to the primary WorkbenchWindowAdvisor */
  ApplicationWorkbenchWindowAdvisor getPrimaryWorkbenchWindowAdvisor() {
    return fgPrimaryApplicationWorkbenchWindowAdvisor;
  }

  /**
   * The ID of the perspective that is initially shown when the Workbench shows.
   *
   * @see org.eclipse.ui.application.WorkbenchAdvisor#getInitialWindowPerspectiveId()
   */
  @Override
  public String getInitialWindowPerspectiveId() {
    return PERSPECTIVE_ID;
  }

  /*
   * @see org.eclipse.ui.application.WorkbenchAdvisor#getMainPreferencePageId()
   */
  @Override
  public String getMainPreferencePageId() {
    return "org.eclipse.ui.preferencePages.Workbench"; //$NON-NLS-1$
  }

  /**
   * !!! NOT YET USED !!! The default input for workbench pages if no input is
   * defined.
   *
   * @see org.eclipse.ui.application.WorkbenchAdvisor#getDefaultPageInput()
   */
  @Override
  public IAdaptable getDefaultPageInput() {
    return super.getDefaultPageInput();
  }

  /**
   * Possible to tweak the UI here before any Window has opened.
   * <p>
   * <cite>This marks the beginning of the advisor's lifecycle and is called
   * during Workbench initialization prior to any windows being opened. This is
   * a good place to parse the command line and register adaptors.</cite>
   * </p>
   *
   * @see org.eclipse.ui.application.WorkbenchAdvisor#initialize(org.eclipse.ui.application.IWorkbenchConfigurer)
   */
  @Override
  public void initialize(IWorkbenchConfigurer configurer) {
    IWorkbenchConfigurer workbenchConfigurer = getWorkbenchConfigurer();

    /*
     * Attempt to repair a corrupt workbench.xmi before Eclipse tries to load
     * it. Corruption typically occurs when the JVM is killed mid-write
     * (truncated file, null bytes, or a broken closing tag). Rather than
     * deleting the whole file  which loses all window/tab/perspective state 
     * we try to salvage as much as possible by trimming back to the last
     * well-formed closing tag and re-closing the document.
     */
    repairWorkbenchXmi();

    /* Save UI state and restore after restart */
    workbenchConfigurer.setSaveAndRestore(true);

    super.initialize(configurer);
  }

  /**
   * Locates the workbench.xmi in the instance data area and validates it. If
   * the file is corrupt (typically truncated by a JVM kill mid-write), it is
   * repaired in place by trimming back to the last complete closing tag and
   * re-closing any open root element, preserving as much state as possible.
   * A backup is written alongside the file before any modification.
   */
  private void repairWorkbenchXmi() {
    try {
      /* workbench.xmi lives at:
       * <instance-area>/.metadata/.plugins/org.eclipse.e4.workbench/workbench.xmi */
      java.io.File workDir = new java.io.File(
          Platform.getInstanceLocation().getURL().getPath(),
          ".metadata/.plugins/org.eclipse.e4.workbench"); //$NON-NLS-1$
      java.io.File xmi = new java.io.File(workDir, "workbench.xmi"); //$NON-NLS-1$

      if (!xmi.exists() || xmi.length() == 0)
        return;

      /* Read the file */
      byte[] raw = java.nio.file.Files.readAllBytes(xmi.toPath());
      String content = new String(raw, java.nio.charset.StandardCharsets.UTF_8);

      /* Quick well-formedness check via SAX - if it parses, nothing to do */
      if (isWellFormedXml(content))
        return;

      /* File is corrupt - write a backup before touching it */
      java.io.File backup = new java.io.File(workDir, "workbench.xmi.corrupt.bak"); //$NON-NLS-1$
      java.nio.file.Files.copy(xmi.toPath(), backup.toPath(),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);

      /* Attempt repair: find the last occurrence of a proper closing tag.
       * workbench.xmi's root element is always <workbench:Application ...>
       * containing <children> and <snippets> elements. We scan backwards for
       * the last complete </...> or self-closing /> and truncate there,
       * then re-close the root if needed. */
      String repaired = repairXmlContent(content);

      if (repaired != null && isWellFormedXml(repaired)) {
        java.nio.file.Files.write(xmi.toPath(),
            repaired.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      } else {
        /* Repair failed - delete so Eclipse starts fresh rather than hanging */
        xmi.delete();
      }
    } catch (Exception e) {
      /* Non-fatal - Eclipse will detect the missing/invalid file itself */
    }
  }

  private boolean isWellFormedXml(String content) {
    try {
      javax.xml.parsers.SAXParserFactory factory = javax.xml.parsers.SAXParserFactory.newInstance();
      factory.setValidating(false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); //$NON-NLS-1$
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false); //$NON-NLS-1$
      javax.xml.parsers.SAXParser parser = factory.newSAXParser();
      parser.parse(new org.xml.sax.InputSource(
          new java.io.StringReader(content)), new org.xml.sax.helpers.DefaultHandler());
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String repairXmlContent(String content) {
    /* Find the last well-formed closing tag working backwards through the file.
     * Each iteration removes the last incomplete or truncated element and tests
     * whether the remainder is valid XML. This preserves the maximum amount of
     * state. The root closing tag is re-appended if missing. */
    String root = "</workbench:Application>"; //$NON-NLS-1$

    /* Strip any trailing null bytes or non-XML characters */
    int end = content.length();
    while (end > 0 && content.charAt(end - 1) == '\0')
      end--;
    String trimmed = content.substring(0, end).stripTrailing();

    /* If it already ends with the root close tag, just test and return */
    if (trimmed.endsWith(root)) {
      return isWellFormedXml(trimmed) ? trimmed : null;
    }

    /* Try appending the root close tag to the truncated content */
    String candidate = trimmed;
    if (!candidate.contains(root)) {
      /* Find the last complete child closing tag */
      int lastClose = Math.max(
          candidate.lastIndexOf("</children>"), //$NON-NLS-1$
          candidate.lastIndexOf("</snippets>")); //$NON-NLS-1$

      if (lastClose >= 0) {
        candidate = candidate.substring(0, lastClose + candidate.substring(lastClose).indexOf('>') + 1);
        candidate = candidate + "\n" + root; //$NON-NLS-1$
        if (isWellFormedXml(candidate))
          return candidate;
      }

      /* Last resort - just append the closing tag */
      candidate = trimmed + "\n" + root; //$NON-NLS-1$
      if (isWellFormedXml(candidate))
        return candidate;
    }

    return null;
  }

  /**
   * This method is called just after the windows have been opened.
   * <p>
   * <cite>Performs arbitrary actions after the Workbench windows have been
   * opened or restored, but before the main event loop is run. This is a good
   * place to start any background jobs such as auto-update daemons</cite>
   * </p>
   *
   * @see org.eclipse.ui.application.WorkbenchAdvisor#postStartup()
   */
  @Override
  public void postStartup() {
    super.postStartup();

    /* Run Runnable if provided */
    if (fRunAfterUIStartup != null) {
      SafeRunner.run(new LoggingSafeRunnable() {
        @Override
        public void run() throws Exception {
          fRunAfterUIStartup.run();
        }
      });
    }
  }

  /**
   * This method is called immediately prior to workbench shutdown before any
   * windows have been closed.
   * <p>
   * <cite>Called immediately prior to Workbench shutdown before any windows
   * have been closed. The advisor may veto a regular shutdown by returning
   * false. Advisors should check
   * <code>IWorkbenchConfigurer.emergencyClosing()</code> before attempting to
   * communicate with the user. </cite>
   * </p>
   *
   * @see org.eclipse.ui.application.WorkbenchAdvisor#preShutdown()
   */
  @Override
  public boolean preShutdown() {
    final boolean res[] = new boolean[] { true };

    /* Pre-Shutdown Controller */
    SafeRunner.run(new LoggingSafeRunnable() {
      @Override
      public void run() throws Exception {
        res[0] = Controller.getDefault().preUIShutdown();
      }
    });

    return res[0];
  }
}
