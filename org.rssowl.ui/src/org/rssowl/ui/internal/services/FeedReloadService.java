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

package org.rssowl.ui.internal.services;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.persist.pref.CascadingScope;
import org.rssowl.core.internal.persist.pref.DefaultPreferences;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.IMark;
import org.rssowl.core.persist.INewsBin;
import org.rssowl.core.persist.INewsMark;
import org.rssowl.core.persist.dao.OwlDAO;
import org.rssowl.core.persist.event.BookMarkAdapter;
import org.rssowl.core.persist.event.BookMarkEvent;
import org.rssowl.core.persist.event.FolderAdapter;
import org.rssowl.core.persist.event.FolderEvent;
import org.rssowl.core.persist.pref.IPreferenceScope;
import org.rssowl.ui.internal.Controller;
import org.rssowl.ui.internal.OwlUI;
import org.rssowl.ui.internal.OwlUI.FeedViewOpenMode;
import org.rssowl.ui.internal.util.EditorUtils;
import org.rssowl.ui.internal.util.JobRunner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Service managing automatic reload of Feeds in RSSOwl based on the user
 * preferences.
 *
 * @author bpasero
 */
public class FeedReloadService {

  /* The delay-threshold in millis (5 Minutes) */
  private static final int DELAY_THRESHOLD = 5 * 60 * 1000;

  /* The delay-value in millis (30 Seconds) */
  private static final int DELAY_VALUE = 30 * 1000;

  /* Listen to Bookmark Updates */
  private BookMarkAdapter fBookMarkListener;

  /* Listen to Folder Updates (to re-sync when a Folder's forced Update Interval changes) */
  private FolderAdapter fFolderListener;

  /* Map IBookMark to Update-Intervals */
  private final Map<IBookMark, Long> fMapBookMarkToInterval;

  /*
   * This subclass of a Job is making sure to delay the operation for <code>WAKEUP_DELAY</code>
   * millis in case it is detecting that the last run of the Job was some amount
   * of time (<code>DELAY_THRESHOLD</code>) after it was meant to be run due
   * to the given Update-Interval. This fixes a problem, where all Update-Jobs
   * would immediately run after waking up from an OS hibernate (e.g. on
   * Windows). Since all Jobs are scheduled based on a time-dif, once waking up
   * from hibernate, the dif is usually telling the Jobs to schedule
   * immediately, even before network interfaces had any chance to start. Thus,
   * all BookMarks will show errors.
   */
  private class ReloadJob extends Job {
    private final IBookMark fBookMark;
    private long fLastRunInMillis;

    ReloadJob(IBookMark bookMark, String name) {
      super(name);
      fBookMark = bookMark;
      fLastRunInMillis = System.currentTimeMillis();
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {

      /* Update Interval of this BookMark */
      Long updateIntervalInSeconds = fMapBookMarkToInterval.get(fBookMark);

      /* Delay execution if required */
      if (delay(updateIntervalInSeconds) && !monitor.isCanceled()) {
        try {
          Thread.sleep(DELAY_VALUE);
        } catch (InterruptedException e) {
          /* Ignore */
        }
      }

      /* Update field */
      fLastRunInMillis = System.currentTimeMillis();

      /* Reload */
      if (!monitor.isCanceled() && !Controller.getDefault().isShuttingDown())
        Controller.getDefault().reloadQueued(fBookMark, null, null);

      /* Re-Schedule */
      if (!monitor.isCanceled() && !Controller.getDefault().isShuttingDown() && updateIntervalInSeconds != null)
        schedule(updateIntervalInSeconds * 1000);

      return Status.OK_STATUS;
    }

    @Override
    public boolean belongsTo(Object family) {
      return family.equals(fBookMark) || family.equals(FeedReloadService.this);
    }

    private boolean delay(Long updateIntervalInSeconds) {
      if (fLastRunInMillis == 0 || updateIntervalInSeconds == null)
        return false;

      long dif = System.currentTimeMillis() - fLastRunInMillis;
      return dif > ((updateIntervalInSeconds * 1000) + DELAY_THRESHOLD);
    }
  }

  /**
   * Instantiates the Feed Reload Service.
   */
  public FeedReloadService() {
    fMapBookMarkToInterval = new ConcurrentHashMap<>();

    /* Register Listeners */
    registerListeners();

    /* Init from a Background Thread */
    JobRunner.runInBackgroundThread(new Runnable() {
      @Override
      public void run() {
        init();
      }
    });
  }

  /** Unregister from Listeners and cancel all Jobs */
  public void stopService() {
    unregisterListeners();
    Job.getJobManager().cancel(this);
  }

  private void init() {

    /* Query Update Intervals and reload/open state */
    Collection<IBookMark> bookmarks = OwlDAO.loadAll(IBookMark.class);
    Collection<INewsBin> newsbins = OwlDAO.loadAll(INewsBin.class);

    final Set<IBookMark> bookmarksToReloadOnStartup = new HashSet<>();
    final List<INewsMark> newsmarksToOpenOnStartup = new ArrayList<>();

    /* For each Bookmark */
    for (IBookMark bookMark : bookmarks) {
      IPreferenceScope entityPreferences = Owl.getPreferenceService().getEntityScope(bookMark);

      /* BookMark is to reload in a certain Interval (Folder override may apply, see resolveUpdateIntervalState/resolveUpdateInterval) */
      if (resolveUpdateIntervalState(bookMark)) {
        long updateInterval = resolveUpdateInterval(bookMark);
        fMapBookMarkToInterval.put(bookMark, updateInterval);

        /* BookMark is to reload on startup */
        if (entityPreferences.getBoolean(DefaultPreferences.BM_RELOAD_ON_STARTUP))
          bookmarksToReloadOnStartup.add(bookMark);
      }

      /* BookMark is to open on startup */
      if (entityPreferences.getBoolean(DefaultPreferences.BM_OPEN_ON_STARTUP))
        newsmarksToOpenOnStartup.add(bookMark);
    }

    /* For each Newsbin */
    for (INewsBin bin : newsbins) {
      IPreferenceScope entityPreferences = Owl.getPreferenceService().getEntityScope(bin);

      /* Newsbin is to open on startup */
      if (entityPreferences.getBoolean(DefaultPreferences.BM_OPEN_ON_STARTUP))
        newsmarksToOpenOnStartup.add(bin);
    }

    /* Reload the ones that reload on startup */
    if (!bookmarksToReloadOnStartup.isEmpty()) {
      JobRunner.runInUIThread(null, new Runnable() {
        @Override
        public void run() {
          Controller.getDefault().reloadQueued(bookmarksToReloadOnStartup, null, null);
        }
      });
    }

    /* Initialize the Jobs that manages Updates */
    Set<Entry<IBookMark, Long>> entries = fMapBookMarkToInterval.entrySet();
    for (Entry<IBookMark, Long> entry : entries) {
      IBookMark bookMark = entry.getKey();
      scheduleUpdate(bookMark, entry.getValue());
    }

    /* Open BookMarks which are to open on startup */
    if (!newsmarksToOpenOnStartup.isEmpty()) {
      JobRunner.runInUIThread(null, new Runnable() {
        @Override
        public void run() {
          IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
          IWorkbenchPage page = (window != null) ? window.getActivePage() : null;
          if (page != null) {
            int maxOpenEditors = EditorUtils.getOpenEditorLimit();
            int openFeedViewCount = OwlUI.getOpenFeedViewCount();

            /* Do not open any Feed if already showing max number of Feeds */
            if (openFeedViewCount >= maxOpenEditors)
              return;

            /* Open in Feedview */
            OwlUI.openInFeedView(page, new StructuredSelection(newsmarksToOpenOnStartup), FeedViewOpenMode.asSet(FeedViewOpenMode.IGNORE_ALREADY_OPENED, FeedViewOpenMode.IGNORE_REUSE));
          }
        }
      });
    }
  }

  private void scheduleUpdate(final IBookMark bookMark, Long intervalInSeconds) {
    Job updateJob = new ReloadJob(bookMark, ""); //$NON-NLS-1$
    updateJob.setSystem(true);
    updateJob.schedule(intervalInSeconds * 1000);
  }

  private void registerListeners() {
    fBookMarkListener = new BookMarkAdapter() {

      @Override
      public void entitiesAdded(Set<BookMarkEvent> events) {
        if (!Controller.getDefault().isShuttingDown())
          onBookMarksAdded(events);
      }

      @Override
      public void entitiesUpdated(Set<BookMarkEvent> events) {
        if (!Controller.getDefault().isShuttingDown())
          onBookMarksUpdated(events);
      }

      @Override
      public void entitiesDeleted(Set<BookMarkEvent> events) {
        if (!Controller.getDefault().isShuttingDown())
          onBookMarksDeleted(events);
      }
    };

    OwlDAO.addEntityListener(IBookMark.class, fBookMarkListener);

    fFolderListener = new FolderAdapter() {
      @Override
      public void entitiesUpdated(Set<FolderEvent> events) {
        if (!Controller.getDefault().isShuttingDown())
          onFoldersUpdated(events);
      }
    };

    OwlDAO.addEntityListener(IFolder.class, fFolderListener);
  }

  private void unregisterListeners() {
    OwlDAO.removeEntityListener(IBookMark.class, fBookMarkListener);
    OwlDAO.removeEntityListener(IFolder.class, fFolderListener);
  }

  /*
   * A Folder's forced Auto-Update Interval (or its enabled-state) changed.
   * Since resolution is cascading and root-first, this can affect every
   * Bookmark nested anywhere below the Folder (including through other
   * Folders that were previously - or are now - shadowed by this one), so
   * walk the whole subtree and re-sync each Bookmark found.
   */
  private void onFoldersUpdated(Set<FolderEvent> events) {
    for (FolderEvent event : events) {
      IFolder updatedFolder = event.getEntity();
      for (IBookMark bookMark : collectBookMarksRecursively(updatedFolder))
        sync(bookMark);
    }
  }

  /* Recursively collects all Bookmarks nested (at any depth) inside the given Folder */
  private List<IBookMark> collectBookMarksRecursively(IFolder folder) {
    List<IBookMark> result = new ArrayList<>();

    for (IMark mark : folder.getMarks()) {
      if (mark instanceof IBookMark)
        result.add((IBookMark) mark);
    }

    for (IFolder childFolder : folder.getFolders())
      result.addAll(collectBookMarksRecursively(childFolder));

    return result;
  }

  /**
   * Resolves whether the given Bookmark is to Auto-Update, taking any
   * enforced ancestor Folder override into account (topmost-enforced-wins),
   * falling back to the Bookmark's own setting, and ultimately the Global
   * default.
   * <p>
   * Note: the Folder-only <code>FOLDER_UPDATE_INTERVAL_STATE</code> key is
   * only used to detect <em>whether</em> a Folder is enforcing (via
   * {@link CascadingScope#getEnforcingSource()}); the actual boolean value
   * is always read from whichever scope (Folder or Bookmark) turns out to
   * be in control, using that scope's own dedicated key - the two features
   * intentionally use separate keys so an eager property copy-down (e.g.
   * during reparenting) can never freeze a stale Folder value onto a
   * Bookmark that reads nothing of its own.
   * </p>
   *
   * @param bookMark the Bookmark to resolve the setting for.
   * @return whether the Bookmark is to Auto-Update.
   */
  private boolean resolveUpdateIntervalState(IBookMark bookMark) {
    IFolder enforcingFolder = getEnforcingUpdateIntervalFolder(bookMark);
    if (enforcingFolder != null)
      return Owl.getPreferenceService().getEntityScope(enforcingFolder).getBoolean(DefaultPreferences.FOLDER_UPDATE_INTERVAL_STATE);

    return Owl.getPreferenceService().getEntityScope(bookMark).getBoolean(DefaultPreferences.BM_UPDATE_INTERVAL_STATE);
  }

  /**
   * Resolves the Auto-Update Interval (in seconds) to use for the given
   * Bookmark, taking any enforced ancestor Folder override into account
   * (topmost-enforced-wins), falling back to the Bookmark's own setting,
   * and ultimately the Global default. See {@link #resolveUpdateIntervalState(IBookMark)}
   * for why the Folder and Bookmark values are read from separate keys.
   *
   * @param bookMark the Bookmark to resolve the Interval for.
   * @return the Auto-Update Interval, in seconds.
   */
  private long resolveUpdateInterval(IBookMark bookMark) {
    IFolder enforcingFolder = getEnforcingUpdateIntervalFolder(bookMark);
    if (enforcingFolder != null)
      return Owl.getPreferenceService().getEntityScope(enforcingFolder).getLong(DefaultPreferences.FOLDER_UPDATE_INTERVAL);

    return Owl.getPreferenceService().getEntityScope(bookMark).getLong(DefaultPreferences.BM_UPDATE_INTERVAL);
  }

  /* Returns the topmost ancestor Folder of the Bookmark that is currently forcing the Auto-Update Interval, or null if none is */
  private IFolder getEnforcingUpdateIntervalFolder(IBookMark bookMark) {
    IPreferenceScope cascadingScope = Owl.getPreferenceService().getCascadingScope(bookMark, DefaultPreferences.FOLDER_UPDATE_INTERVAL_STATE);
    if (cascadingScope instanceof CascadingScope)
      return ((CascadingScope) cascadingScope).getEnforcingSource();

    return null;
  }

  private void onBookMarksAdded(Set<BookMarkEvent> events) {
    for (BookMarkEvent event : events) {
      IBookMark addedBookMark = event.getEntity();

      Long interval = resolveUpdateInterval(addedBookMark);
      boolean autoUpdateState = resolveUpdateIntervalState(addedBookMark);

      /* BookMark wants to Auto-Update */
      if (autoUpdateState)
        addUpdate(event.getEntity(), interval);
    }
  }

  private void onBookMarksUpdated(Set<BookMarkEvent> events) {
    for (BookMarkEvent event : events) {
      IBookMark updatedBookMark = event.getEntity();
      sync(updatedBookMark);
    }
  }

  private void onBookMarksDeleted(Set<BookMarkEvent> events) {
    for (BookMarkEvent event : events) {
      removeUpdate(event.getEntity());
    }
  }

  /**
   * Synchronizes the reload-service on the given BookMark. Performs no
   * operation in case the given bookmarks update-interval is matching the
   * stored one.
   *
   * @param updatedBookmark The Bookmark to synchronize with the reload-service.
   */
  public void sync(IBookMark updatedBookmark) {
    Long oldInterval = fMapBookMarkToInterval.get(updatedBookmark);
    Long newInterval = resolveUpdateInterval(updatedBookmark);

    boolean autoUpdateState = resolveUpdateIntervalState(updatedBookmark);

    /* BookMark known to the Service */
    if (oldInterval != null) {

      /* BookMark no longer Auto-Updating */
      if (!autoUpdateState)
        removeUpdate(updatedBookmark);

      /* New Interval different to Old Interval */
      else if (!newInterval.equals(oldInterval)) {
        Job.getJobManager().cancel(updatedBookmark);
        fMapBookMarkToInterval.put(updatedBookmark, newInterval);
        scheduleUpdate(updatedBookmark, newInterval);
      }
    }

    /* BookMark not yet known to the Service and wants to Auto-Update */
    else if (autoUpdateState) {
      addUpdate(updatedBookmark, newInterval);
    }
  }

  private void removeUpdate(IBookMark bookmark) {
    fMapBookMarkToInterval.remove(bookmark);
    Job.getJobManager().cancel(bookmark);
  }

  private void addUpdate(IBookMark bookmark, Long intervalInSeconds) {
    fMapBookMarkToInterval.put(bookmark, intervalInSeconds);
    scheduleUpdate(bookmark, intervalInSeconds);
  }
}