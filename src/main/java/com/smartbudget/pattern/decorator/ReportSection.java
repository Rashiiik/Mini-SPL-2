package com.smartbudget.pattern.decorator;

import java.util.List;

/** One headed block of lines within a report. */
public record ReportSection(String heading, List<String> lines) {

    public static ReportSection of(String heading, String... lines) {
        return new ReportSection(heading, List.of(lines));
    }
}
