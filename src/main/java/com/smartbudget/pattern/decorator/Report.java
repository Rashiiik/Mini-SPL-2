package com.smartbudget.pattern.decorator;

import java.util.List;

/**
 * A monthly report, as a title and a list of sections.
 *
 * <p>Returns structure rather than a formatted string so the same report can be
 * rendered on screen, exported, or wrapped by a decorator that appends to it.
 * A report that had already decided its own layout would leave a decorator
 * nothing clean to add to.
 */
public interface Report {

    String title();

    List<ReportSection> sections();
}
