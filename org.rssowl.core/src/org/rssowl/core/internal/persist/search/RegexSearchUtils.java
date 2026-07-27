/*   **********************************************************************  **
 **  Copyright notice                                                       **
 **  **
 **  This file is part of the RSSOwl project. See rssowl.org for details.   **
 **                                                                          **
 **  This program is free software; you can redistribute it and/or modify   **
 **  it under the terms of the Eclipse Public License v1.0                  **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.core.internal.persist.search;

import org.rssowl.core.internal.Activator;
import org.rssowl.core.persist.IAttachment;
import org.rssowl.core.persist.IEntity;
import org.rssowl.core.persist.INews;
import org.rssowl.core.persist.IPerson;
import org.rssowl.core.persist.ISearchCondition;
import org.rssowl.core.persist.SearchSpecifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Shared logic for the "matches regex" / "doesn't match regex" search
 * condition specifiers ({@link SearchSpecifier#MATCHES_REGEX} and
 * {@link SearchSpecifier#MATCHES_REGEX_NOT}).
 * <p>
 * Regex conditions cannot be expressed as a Lucene {@link org.apache.lucene.search.Query}
 * (Lucene operates on indexed/analyzed terms, not on the raw field text a
 * {@link java.util.regex.Pattern} needs to run against), so every caller that
 * wants to support them has to: (1) split them out from the conditions Lucene
 * *can* handle, and (2) apply them as a Java post-filter against the resolved
 * {@link INews}. This class centralizes both steps so the two current callers
 * ({@link ModelSearchImpl}, for interactive "Search News", and
 * {@code ApplicationServiceImpl}, for automatic Search Filters) stay in sync -
 * this is deliberately factored out because those two engines drifting apart
 * is exactly what caused Search Filters to throw for regex conditions in the
 * first place.
 */
public class RegexSearchUtils {

  private RegexSearchUtils() {}

  /** A single successfully-compiled regex condition. */
  public static final class CompiledRegexCondition {
    private final Pattern fPattern;
    private final boolean fNegate;
    private final int fFieldId;

    private CompiledRegexCondition(Pattern pattern, boolean negate, int fieldId) {
      fPattern = pattern;
      fNegate = negate;
      fFieldId = fieldId;
    }
  }

  /**
   * Splits the given conditions into regex-specifier conditions and all
   * other conditions.
   *
   * @param conditions the conditions to split.
   * @param regexOut regex conditions ({@link SearchSpecifier#MATCHES_REGEX}
   * or {@link SearchSpecifier#MATCHES_REGEX_NOT}) are added here.
   * @param otherOut every other condition is added here.
   */
  public static void splitConditions(Collection<ISearchCondition> conditions, List<ISearchCondition> regexOut, List<ISearchCondition> otherOut) {
    for (ISearchCondition condition : conditions) {
      SearchSpecifier specifier = condition.getSpecifier();
      if (specifier == SearchSpecifier.MATCHES_REGEX || specifier == SearchSpecifier.MATCHES_REGEX_NOT)
        regexOut.add(condition);
      else
        otherOut.add(condition);
    }
  }

  /**
   * Regex conditions are only supported as the sole kind of condition in a
   * search. Once Lucene-handled conditions are combined with a Java-side
   * regex post-filter, the two only combine correctly under "match all"
   * semantics; under "match any" the post-filter cannot tell whether an item
   * already satisfied the search through a non-regex condition; and an item
   * that could only ever match through the regex condition never becomes a
   * Lucene candidate in the first place. Rather than silently returning
   * incomplete/wrong results in that case, callers should treat this as
   * "ignore the regex conditions" (see {@link #hasMixedConditionTypes}).
   *
   * @param regexConditions the regex conditions found via
   * {@link #splitConditions}.
   * @param otherConditions the non-regex conditions found via
   * {@link #splitConditions}.
   * @return <code>true</code> if the given conditions mix regex conditions
   * with other, non-Scope conditions.
   */
  public static boolean hasMixedConditionTypes(List<ISearchCondition> regexConditions, List<ISearchCondition> otherConditions) {
    if (regexConditions.isEmpty())
      return false;

    for (ISearchCondition condition : otherConditions) {

      /* Scope is a structural restriction (which Bookmark/Folder/Bin to search), not a content condition, so it may always be combined with regex conditions */
      if (condition.getSpecifier() == SearchSpecifier.SCOPE)
        continue;

      return true;
    }

    return false;
  }

  /**
   * Compiles the given regex conditions, silently skipping (and logging) any
   * with an invalid pattern.
   *
   * @param regexConditions the conditions to compile.
   * @return the successfully compiled conditions.
   */
  public static List<CompiledRegexCondition> compile(List<ISearchCondition> regexConditions) {
    List<CompiledRegexCondition> compiled = new ArrayList<CompiledRegexCondition>(regexConditions.size());
    for (ISearchCondition condition : regexConditions) {
      String value = String.valueOf(condition.getValue());
      try {
        Pattern pattern = Pattern.compile(value, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        compiled.add(new CompiledRegexCondition(pattern, condition.getSpecifier() == SearchSpecifier.MATCHES_REGEX_NOT, condition.getField().getId()));
      } catch (PatternSyntaxException e) {
        Activator.safeLogError("Skipping invalid regex in search condition: " + value, e); //$NON-NLS-1$
      }
    }

    return compiled;
  }

  /**
   * @param news the news to test.
   * @param compiled the compiled regex conditions to test with, as returned
   * by {@link #compile(List)}.
   * @param matchAllConditions <code>true</code> if all conditions must
   * match, <code>false</code> if any one condition matching is sufficient.
   * @return <code>true</code> if the given news matches according to the
   * given conditions and combination mode.
   */
  public static boolean matches(INews news, List<CompiledRegexCondition> compiled, boolean matchAllConditions) {
    if (compiled.isEmpty())
      return true;

    boolean overallMatch = matchAllConditions;
    for (CompiledRegexCondition condition : compiled) {
      String fieldText = getFieldText(news, condition.fFieldId);
      boolean matches = condition.fPattern.matcher(fieldText != null ? fieldText : "").find(); //$NON-NLS-1$
      if (condition.fNegate)
        matches = !matches;

      if (matchAllConditions) {
        if (!matches) {
          overallMatch = false;
          break;
        }
      } else {
        if (matches) {
          overallMatch = true;
          break;
        }
      }
    }

    return overallMatch;
  }

  /**
   * Resolves the raw text of the given field for regex matching purposes.
   * Mirrors what is actually indexed for that field (see
   * {@link SearchDocument#createAttachmentsField}) so regex results stay
   * consistent with how the same field behaves for non-regex searches.
   */
  private static String getFieldText(INews news, int fieldId) {
    switch (fieldId) {
      case IEntity.ALL_FIELDS: {
        StringBuilder sb = new StringBuilder();
        if (news.getTitle() != null)
          sb.append(news.getTitle()).append(' ');
        if (news.getDescription() != null)
          sb.append(news.getDescription()).append(' ');
        IPerson author = news.getAuthor();
        if (author != null && author.getName() != null)
          sb.append(author.getName()).append(' ');
        sb.append(getAttachmentsText(news));
        return sb.toString();
      }

      case INews.TITLE:
        return news.getTitle();

      case INews.DESCRIPTION:
        return news.getDescription();

      case INews.AUTHOR: {
        IPerson author = news.getAuthor();
        return author != null ? author.getName() : null;
      }

      case INews.ATTACHMENTS_CONTENT:
        return getAttachmentsText(news);

      default:
        return null;
    }
  }

  /* Mirrors SearchDocument#createAttachmentsField: link + type of every attachment */
  private static String getAttachmentsText(INews news) {
    List<IAttachment> attachments = news.getAttachments();
    if (attachments == null || attachments.isEmpty())
      return ""; //$NON-NLS-1$

    StringBuilder sb = new StringBuilder();
    for (IAttachment attachment : attachments) {
      if (attachment.getLink() != null)
        sb.append(attachment.getLink()).append(' ');

      if (attachment.getType() != null)
        sb.append(attachment.getType()).append(' ');
    }

    return sb.toString();
  }
}
