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

package org.rssowl.core.tests.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.rssowl.core.internal.persist.Category;
import org.rssowl.core.internal.persist.ComplexMergeResult;
import org.rssowl.core.internal.persist.Label;
import org.rssowl.core.internal.persist.Person;
import org.rssowl.core.persist.ICategory;
import org.rssowl.core.persist.ILabel;
import org.rssowl.core.persist.IPerson;
import org.rssowl.core.util.MergeUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests methods in {@link MergeUtils}.
 */
public class MergeUtilsTest {

  /**
   * Tests
   * {@link MergeUtils#merge(org.rssowl.core.persist.MergeCapable, org.rssowl.core.persist.MergeCapable)}
   * .
   */
  @Test
  public void testSingleItemMergeWithNullDestination() {
    IPerson person = new Person((Long) null);
    ComplexMergeResult<IPerson> mergeResult = MergeUtils.merge(null, person);
    assertEquals(person, mergeResult.getMergedObject());
    assertEquals(true, mergeResult.isStructuralChange());
  }

  /**
   * Tests
   * {@link MergeUtils#merge(org.rssowl.core.persist.MergeCapable, org.rssowl.core.persist.MergeCapable)}
   * .
   */
  @Test
  public void testSingleItemMergeWithNonNullDestinationAndNullOrigin() {
    IPerson person = new Person((Long) null);
    ComplexMergeResult<IPerson> mergeResult = MergeUtils.merge(person, null);
    assertNull(mergeResult.getMergedObject());
    assertEquals(true, mergeResult.isStructuralChange());
  }

  /**
   * Tests
   * {@link MergeUtils#merge(List, List, org.rssowl.core.persist.IPersistable)}.
   */
  @Test
  public void testCollectionMergeWithNullExistingListAndNullNewParent() {
    List<ICategory> categories = new ArrayList<ICategory>();
    categories.add(new Category());
    MergeUtils.merge(null, categories, null);
  }

  /**
   * Tests
   * {@link MergeUtils#mergeProperties(org.rssowl.core.persist.IEntity, org.rssowl.core.persist.IEntity)}
   * .
   */
  @Test
  public void testMergeProperties() {
    ILabel label0 = new Label(null, "label0");
    String key0 = "key0";
    String value0 = "value0";
    label0.setProperty(key0, value0);
    String key1 = "key1";
    String value1 = "value1";
    label0.setProperty(key1, value1);
    String key2 = "key2";
    String value2 = "value2";
    label0.setProperty(key2, value2);

    ILabel label1 = new Label(null, "label1");
    label1.setProperty(key1, value1);
    String newValue2 = "newValue2";
    label1.setProperty(key2, newValue2);

    ComplexMergeResult<?> mergeResult = MergeUtils.mergeProperties(label0, label1);
    assertEquals(true, mergeResult.getRemovedObjects().contains(value0));
    assertEquals(true, mergeResult.getRemovedObjects().contains(value2));
    assertEquals(true, mergeResult.isStructuralChange());

    assertEquals(2, label0.getProperties().size());
    assertEquals(2, label1.getProperties().size());
    assertEquals(value1, label0.getProperties().get(key1));
    assertEquals(newValue2, label0.getProperties().get(key2));
  }
}
