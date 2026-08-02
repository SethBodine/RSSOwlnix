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

package org.rssowl.core.internal.persist.pref;

import org.eclipse.core.runtime.Assert;
import org.rssowl.core.persist.IFolder;
import org.rssowl.core.persist.pref.IPreferenceScope;

import java.util.Collections;
import java.util.List;

/**
 * A generic, root-first cascading {@link IPreferenceScope} used to resolve
 * Folder-level overrides that apply to everything nested below them (for
 * example a forced Auto-Update Interval or a Proxy bypass choice).
 * <p>
 * Precedence is <b>topmost-enforced-wins</b>: walking the chain of ancestor
 * Folders from the root down to the immediate parent of the leaf, the first
 * Folder whose <code>stateKey</code> is <code>true</code> wins, even if a
 * Folder further down the chain (closer to the leaf) also has an override
 * configured. That closer override simply stays dormant - it is never
 * deleted or modified - and will apply automatically the moment the
 * ancestor's override is disabled.
 * </p>
 * <p>
 * If no Folder in the chain is enforcing, resolution falls through to the
 * leaf's own scope (which, for a Bookmark, may itself hold a per-Bookmark
 * override), and ultimately to the Global scope.
 * </p>
 * <p>
 * This class has no knowledge of what it is resolving - it is driven purely
 * by the given <code>stateKey</code> and the generic
 * {@link IPreferenceScope} contract - so both the forced Auto-Update
 * Interval feature and the Proxy bypass feature (and any future per-Folder
 * override) can reuse it unchanged.
 * </p>
 *
 * @author bpasero
 */
public class CascadingScope implements IPreferenceScope {

  private final List<EntityScope> fFolderChainRootFirst;
  private final List<IFolder> fFolderChainEntitiesRootFirst;
  private final IPreferenceScope fLeafScope;
  private final String fStateKey;

  /**
   * @param folderChainRootFirst the {@link EntityScope}s of all ancestor
   * Folders of the leaf, ordered root-first (index 0 is the root Folder,
   * the last entry is the leaf's immediate parent).
   * @param folderChainEntitiesRootFirst the {@link IFolder}s backing
   * <code>folderChainRootFirst</code>, in the same order. Kept as a
   * parallel list since {@link EntityScope} does not expose its wrapped
   * entity outside of this package.
   * @param leafScope the leaf's own {@link IPreferenceScope} (typically an
   * {@link EntityScope} for the leaf, itself falling back to the Global
   * scope on a miss).
   * @param stateKey the boolean preference key that indicates whether a
   * given Folder is enforcing an override.
   */
  public CascadingScope(List<EntityScope> folderChainRootFirst, List<IFolder> folderChainEntitiesRootFirst, IPreferenceScope leafScope, String stateKey) {
    Assert.isNotNull(folderChainRootFirst, "folderChainRootFirst cannot be null"); //$NON-NLS-1$
    Assert.isNotNull(folderChainEntitiesRootFirst, "folderChainEntitiesRootFirst cannot be null"); //$NON-NLS-1$
    Assert.isTrue(folderChainRootFirst.size() == folderChainEntitiesRootFirst.size(), "chains must be of equal size"); //$NON-NLS-1$
    Assert.isNotNull(leafScope, "leafScope cannot be null"); //$NON-NLS-1$
    Assert.isNotNull(stateKey, "stateKey cannot be null"); //$NON-NLS-1$

    fFolderChainRootFirst = folderChainRootFirst;
    fFolderChainEntitiesRootFirst = folderChainEntitiesRootFirst;
    fLeafScope = leafScope;
    fStateKey = stateKey;
  }

  /**
   * Resolves the {@link IPreferenceScope} that is currently in control:
   * the topmost (root-first) ancestor Folder that has its own
   * <code>stateKey</code> explicitly set to <code>true</code>, or - if none
   * of the ancestors are enforcing - the leaf's own scope.
   *
   * @return the winning {@link IPreferenceScope}, never <code>null</code>.
   */
  private IPreferenceScope resolveWinningScope() {
    for (EntityScope folderScope : fFolderChainRootFirst) {
      if (folderScope.hasKey(fStateKey) && folderScope.getBoolean(fStateKey))
        return folderScope;
    }

    return fLeafScope;
  }

  /**
   * @return the {@link IFolder} that is currently enforcing the override
   * represented by <code>stateKey</code>, i.e. the topmost ancestor Folder
   * with <code>stateKey</code> set to <code>true</code>, or
   * <code>null</code> if nothing above the leaf is enforcing (in which
   * case resolution falls through to the leaf's own setting or the global
   * default).
   */
  public IFolder getEnforcingSource() {
    for (int i = 0; i < fFolderChainRootFirst.size(); i++) {
      EntityScope folderScope = fFolderChainRootFirst.get(i);
      if (folderScope.hasKey(fStateKey) && folderScope.getBoolean(fStateKey))
        return fFolderChainEntitiesRootFirst.get(i);
    }

    return null;
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getParent()
   */
  @Override
  public IPreferenceScope getParent() {
    return resolveWinningScope().getParent();
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#flush()
   */
  @Override
  public void flush() {
    resolveWinningScope().flush();
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#delete(java.lang.String)
   */
  @Override
  public void delete(String key) {
    resolveWinningScope().delete(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getBoolean(java.lang.String)
   */
  @Override
  public boolean getBoolean(String key) {
    return resolveWinningScope().getBoolean(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getInteger(java.lang.String)
   */
  @Override
  public int getInteger(String key) {
    return resolveWinningScope().getInteger(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getIntegers(java.lang.String)
   */
  @Override
  public int[] getIntegers(String key) {
    return resolveWinningScope().getIntegers(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getLong(java.lang.String)
   */
  @Override
  public long getLong(String key) {
    return resolveWinningScope().getLong(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getLongs(java.lang.String)
   */
  @Override
  public long[] getLongs(String key) {
    return resolveWinningScope().getLongs(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getString(java.lang.String)
   */
  @Override
  public String getString(String key) {
    return resolveWinningScope().getString(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#getStrings(java.lang.String)
   */
  @Override
  public String[] getStrings(String key) {
    return resolveWinningScope().getStrings(key);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#putBoolean(java.lang.String, boolean)
   */
  @Override
  public void putBoolean(String key, boolean value) {
    resolveWinningScope().putBoolean(key, value);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#putInteger(java.lang.String, int)
   */
  @Override
  public void putInteger(String key, int value) {
    resolveWinningScope().putInteger(key, value);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#putIntegers(java.lang.String, int[])
   */
  @Override
  public void putIntegers(String key, int[] values) {
    resolveWinningScope().putIntegers(key, values);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#putLong(java.lang.String, long)
   */
  @Override
  public void putLong(String key, long value) {
    resolveWinningScope().putLong(key, value);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#putLongs(java.lang.String, long[])
   */
  @Override
  public void putLongs(String key, long[] values) {
    resolveWinningScope().putLongs(key, values);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#putString(java.lang.String, java.lang.String)
   */
  @Override
  public void putString(String key, String value) {
    resolveWinningScope().putString(key, value);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#putStrings(java.lang.String, java.lang.String[])
   */
  @Override
  public void putStrings(String key, String[] values) {
    resolveWinningScope().putStrings(key, values);
  }

  /*
   * @see org.rssowl.core.persist.pref.IPreferenceScope#hasKey(java.lang.String)
   */
  @Override
  public boolean hasKey(String key) {
    return resolveWinningScope().hasKey(key);
  }

  /**
   * @return the root-first chain of ancestor Folders considered by this
   * Scope (excludes the leaf itself). Exposed mainly for testing.
   */
  public List<IFolder> getFolderChainRootFirst() {
    return Collections.unmodifiableList(fFolderChainEntitiesRootFirst);
  }
}
