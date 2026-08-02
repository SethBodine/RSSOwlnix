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

package org.rssowl.core.tests.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.persist.pref.DefaultPreferences;
import org.rssowl.core.internal.persist.service.PersistenceServiceImpl;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFeed;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.IModelFactory;
import org.rssowl.core.persist.dao.OwlDAO;
import org.rssowl.core.persist.pref.IPreferenceScope;
import org.rssowl.core.persist.reference.FeedLinkReference;
import org.rssowl.ui.internal.services.FeedReloadService;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Map;

/**
 * Tests the Feature 1 (forced Auto-Update Interval) cascade resolution
 * built into {@link FeedReloadService}. Uses reflection to reach the
 * package-private resolver methods, since exposing them publicly is not
 * otherwise required by the feature.
 *
 * @author bpasero
 */
@SuppressWarnings("nls")
public class FeedReloadServiceCascadeTest extends LargeBlockSizeTest {

  private IModelFactory fFactory;
  private FeedReloadService fService;

  /**
   * @throws Exception
   */
  @Before
  public void setUp() throws Exception {
    ((PersistenceServiceImpl) Owl.getPersistenceService()).recreateSchemaForTests();
    fFactory = Owl.getModelFactory();
    fService = new FeedReloadService();
  }

  /**
   * Ensure the Service (and its Job/Listener registrations) does not
   * outlive the test.
   */
  @After
  public void tearDown() {
    if (fService != null)
      fService.stopService();
  }

  private IBookMark createBookMark(IFolder folder, String linkSuffix) throws Exception {
    IFeed feed = fFactory.createFeed(null, new URI("http://www.rssowl.org/cascade_test_" + linkSuffix + "_" + System.nanoTime()));
    feed = OwlDAO.save(feed);
    return fFactory.createBookMark(null, folder, new FeedLinkReference(feed.getLink()), "BookMark " + linkSuffix);
  }

  private boolean resolveUpdateIntervalState(IBookMark bookMark) throws Exception {
    Method method = FeedReloadService.class.getDeclaredMethod("resolveUpdateIntervalState", IBookMark.class);
    method.setAccessible(true);
    return (Boolean) method.invoke(fService, bookMark);
  }

  private long resolveUpdateInterval(IBookMark bookMark) throws Exception {
    Method method = FeedReloadService.class.getDeclaredMethod("resolveUpdateInterval", IBookMark.class);
    method.setAccessible(true);
    return (Long) method.invoke(fService, bookMark);
  }

  /**
   * A Folder forcing an Auto-Update Interval overrides the Global default
   * for every Bookmark nested inside it, even if that Bookmark has no
   * per-Bookmark setting of its own.
   *
   * @throws Exception
   */
  @Test
  public final void testFolderForcesIntervalOnNestedBookMark() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IBookMark bookMark = createBookMark(root, "a");
    OwlDAO.save(root);

    /* No override: falls back to Bookmark's own (default) setting */
    assertTrue(resolveUpdateIntervalState(bookMark)); // BM_UPDATE_INTERVAL_STATE defaults to true
    assertEquals(60 * 30, resolveUpdateInterval(bookMark)); // default 30 Minutes

    /* Root forces a 5 Minute Interval */
    IPreferenceScope rootScope = Owl.getPreferenceService().getEntityScope(root);
    rootScope.putBoolean(DefaultPreferences.FOLDER_UPDATE_INTERVAL_STATE, true);
    rootScope.putLong(DefaultPreferences.FOLDER_UPDATE_INTERVAL, 5 * 60);

    assertTrue(resolveUpdateIntervalState(bookMark));
    assertEquals(5 * 60, resolveUpdateInterval(bookMark));
  }

  /**
   * A Bookmark's own explicit "do not Auto-Update" setting is honored when
   * no ancestor Folder is enforcing.
   *
   * @throws Exception
   */
  @Test
  public final void testBookMarkOwnSettingHonoredWithoutFolderOverride() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IBookMark bookMark = createBookMark(root, "b");
    OwlDAO.save(root);

    IPreferenceScope bookMarkScope = Owl.getPreferenceService().getEntityScope(bookMark);
    bookMarkScope.putBoolean(DefaultPreferences.BM_UPDATE_INTERVAL_STATE, false);

    assertFalse(resolveUpdateIntervalState(bookMark));
  }

  /**
   * Changing a Folder's forced Interval fires an update event that causes
   * {@link FeedReloadService} to re-sync every Bookmark nested underneath
   * it (recursively, through sub-Folders too) - exercised here through the
   * real event path (saving the Folder after changing its preference,
   * rather than calling the resolver directly), then inspecting the
   * Service's internal Bookmark-to-Interval map via reflection.
   *
   * @throws Exception
   */
  @Test
  public final void testFolderChangeCascadesToNestedSubfolders() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IFolder sub = fFactory.createFolder(null, root, "Sub");
    IBookMark bookMarkInRoot = createBookMark(root, "root-bm");
    IBookMark bookMarkInSub = createBookMark(sub, "sub-bm");
    root = OwlDAO.save(root);

    /* Allow the entitiesAdded events for both Bookmarks to be processed */
    waitForBackgroundEvents();

    /* Force a 5 Minute Interval on Root and persist the change - this fires a FolderEvent that must cascade to both nested Bookmarks */
    IPreferenceScope rootScope = Owl.getPreferenceService().getEntityScope(root);
    rootScope.putBoolean(DefaultPreferences.FOLDER_UPDATE_INTERVAL_STATE, true);
    rootScope.putLong(DefaultPreferences.FOLDER_UPDATE_INTERVAL, 5 * 60);
    OwlDAO.save(root);

    waitForBackgroundEvents();

    assertEquals(Long.valueOf(5 * 60), getTrackedInterval(bookMarkInRoot));
    assertEquals(Long.valueOf(5 * 60), getTrackedInterval(bookMarkInSub));
  }

  /* Entity events from OwlDAO.save() are dispatched asynchronously; give listeners a moment to run */
  private void waitForBackgroundEvents() throws InterruptedException {
    Thread.sleep(500);
  }

  @SuppressWarnings("unchecked")
  private Long getTrackedInterval(IBookMark bookMark) throws Exception {
    java.lang.reflect.Field field = FeedReloadService.class.getDeclaredField("fMapBookMarkToInterval");
    field.setAccessible(true);
    Map<IBookMark, Long> map = (Map<IBookMark, Long>) field.get(fService);
    return map.get(bookMark);
  }
}
