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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;
import org.rssowl.core.Owl;
import org.rssowl.core.internal.persist.pref.CascadingScope;
import org.rssowl.core.internal.persist.pref.GlobalScope;
import org.rssowl.core.internal.persist.service.PersistenceServiceImpl;
import org.rssowl.core.persist.IBookMark;
import org.rssowl.core.persist.IFeed;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.IModelFactory;
import org.rssowl.core.persist.pref.IPreferenceScope;
import org.rssowl.core.persist.reference.FeedLinkReference;

import java.net.URI;

/**
 * Tests for {@link CascadingScope}, the generic root-first resolver shared
 * by the Folder-level "forced Auto-Update Interval" and "Proxy bypass"
 * override features.
 *
 * @author bpasero
 */
@SuppressWarnings("nls")
public class CascadingScopeTest extends LargeBlockSizeTest {

  private static final String STATE_KEY = "org.rssowl.tests.cascade.state";
  private static final String VALUE_KEY = "org.rssowl.tests.cascade.value";

  private IModelFactory fFactory;

  /**
   * @throws Exception
   */
  @Before
  public void setUp() throws Exception {
    ((PersistenceServiceImpl) Owl.getPersistenceService()).recreateSchemaForTests();
    ((GlobalScope) Owl.getPreferenceService().getGlobalScope()).clearCache();
    fFactory = Owl.getModelFactory();
  }

  /* Creates Root -> Sub -> Leaf(BookMark) and returns the BookMark */
  private IBookMark createBookMarkNestedIn(IFolder sub) throws Exception {
    IFeed feed = fFactory.createFeed(null, new URI("http://www.example.com/feed" + System.nanoTime()));
    IBookMark bookMark = fFactory.createBookMark(null, sub, new FeedLinkReference(feed.getLink()), "BookMark");
    return bookMark;
  }

  /**
   * Topmost-enforced-wins: if both the root and a subfolder have the
   * override enabled, the root (topmost) governs - not the nearer subfolder.
   *
   * @throws Exception
   */
  @Test
  public final void testTopmostEnforcedWins() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IFolder sub = fFactory.createFolder(null, root, "Sub");
    IBookMark bookMark = createBookMarkNestedIn(sub);

    IPreferenceScope rootScope = Owl.getPreferenceService().getEntityScope(root);
    IPreferenceScope subScope = Owl.getPreferenceService().getEntityScope(sub);

    rootScope.putBoolean(STATE_KEY, true);
    rootScope.putLong(VALUE_KEY, 111L);

    subScope.putBoolean(STATE_KEY, true);
    subScope.putLong(VALUE_KEY, 222L);

    CascadingScope cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);

    /* Root wins, even though Sub is nearer and also enforcing */
    assertEquals(root, cascade.getEnforcingSource());
  }

  /**
   * A Folder's own override value is never deleted or overwritten when it
   * is outranked by an ancestor - it stays dormant and reactivates
   * automatically once the ancestor's override is disabled.
   *
   * @throws Exception
   */
  @Test
  public final void testDormantReactivation() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IFolder sub = fFactory.createFolder(null, root, "Sub");
    IBookMark bookMark = createBookMarkNestedIn(sub);

    IPreferenceScope rootScope = Owl.getPreferenceService().getEntityScope(root);
    IPreferenceScope subScope = Owl.getPreferenceService().getEntityScope(sub);

    rootScope.putBoolean(STATE_KEY, true);
    rootScope.putLong(VALUE_KEY, 111L);

    subScope.putBoolean(STATE_KEY, true);
    subScope.putLong(VALUE_KEY, 222L);

    /* Root enforces first */
    CascadingScope cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertEquals(root, cascade.getEnforcingSource());

    /* Disable the Root's override - Sub's dormant override should now apply, unmodified */
    rootScope.putBoolean(STATE_KEY, false);

    cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertEquals(sub, cascade.getEnforcingSource());
    assertEquals(222L, Owl.getPreferenceService().getEntityScope(sub).getLong(VALUE_KEY));

    /* Re-enable the Root's override - it should win again immediately */
    rootScope.putBoolean(STATE_KEY, true);

    cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertEquals(root, cascade.getEnforcingSource());
  }

  /**
   * If nothing in the Folder path is enforcing, resolution reports no
   * enforcing source at all (callers are expected to fall back to the
   * leaf's own per-entity setting, then the Global default).
   *
   * @throws Exception
   */
  @Test
  public final void testFallsThroughWhenNoAncestorEnforces() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IFolder sub = fFactory.createFolder(null, root, "Sub");
    IBookMark bookMark = createBookMarkNestedIn(sub);

    CascadingScope cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertNull(cascade.getEnforcingSource());
  }

  /**
   * A deeper nesting: only a middle Folder enforces; a Folder above it does
   * not, and a Folder below it also enforces but should stay dormant.
   *
   * @throws Exception
   */
  @Test
  public final void testMiddleFolderWinsOverDeeperFolder() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IFolder middle = fFactory.createFolder(null, root, "Middle");
    IFolder leafFolder = fFactory.createFolder(null, middle, "LeafFolder");
    IBookMark bookMark = createBookMarkNestedIn(leafFolder);

    IPreferenceScope middleScope = Owl.getPreferenceService().getEntityScope(middle);
    IPreferenceScope leafFolderScope = Owl.getPreferenceService().getEntityScope(leafFolder);

    middleScope.putBoolean(STATE_KEY, true);
    leafFolderScope.putBoolean(STATE_KEY, true);

    CascadingScope cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertEquals(middle, cascade.getEnforcingSource());

    /* Sanity: chain is root-first and includes all three ancestors */
    assertEquals(3, cascade.getFolderChainRootFirst().size());
    assertEquals(root, cascade.getFolderChainRootFirst().get(0));
    assertEquals(middle, cascade.getFolderChainRootFirst().get(1));
    assertEquals(leafFolder, cascade.getFolderChainRootFirst().get(2));
  }

  /**
   * A Folder with the state key present but explicitly false is not
   * enforcing (distinguishes "explicitly off" from "never configured";
   * both currently behave the same for resolution purposes, but hasKey()
   * must not be conflated with getBoolean()).
   *
   * @throws Exception
   */
  @Test
  public final void testExplicitlyDisabledFolderDoesNotEnforce() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IBookMark bookMark = createBookMarkNestedIn(root);

    IPreferenceScope rootScope = Owl.getPreferenceService().getEntityScope(root);
    rootScope.putBoolean(STATE_KEY, false);

    CascadingScope cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertNull(cascade.getEnforcingSource());
  }

  /**
   * getBoolean()/getLong() on the CascadingScope itself delegate to the
   * currently-winning scope.
   *
   * @throws Exception
   */
  @Test
  public final void testDelegatesToWinningScope() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IBookMark bookMark = createBookMarkNestedIn(root);

    IPreferenceScope rootScope = Owl.getPreferenceService().getEntityScope(root);
    rootScope.putBoolean(STATE_KEY, true);
    rootScope.putLong(VALUE_KEY, 999L);

    IPreferenceScope cascade = Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertEquals(999L, cascade.getLong(VALUE_KEY));

    /* Once disabled, the cascading scope falls through to the leaf's own (unset) value -> Global default (0) */
    rootScope.putBoolean(STATE_KEY, false);
    assertEquals(0L, cascade.getLong(VALUE_KEY));
  }

  /**
   * Sanity check that {@link Owl#getPreferenceService()}'s cascading Scope
   * factory always returns a non-null Scope even for a Bookmark directly
   * under the Root (no intermediate Folders).
   *
   * @throws Exception
   */
  @Test
  public final void testDirectRootChildHasEmptyAncestorChain() throws Exception {
    IFolder root = fFactory.createFolder(null, null, "Root");
    IBookMark bookMark = createBookMarkNestedIn(root);

    CascadingScope cascade = (CascadingScope) Owl.getPreferenceService().getCascadingScope(bookMark, STATE_KEY);
    assertNotNull(cascade);
    assertEquals(1, cascade.getFolderChainRootFirst().size());
    assertEquals(root, cascade.getFolderChainRootFirst().get(0));
  }
}
