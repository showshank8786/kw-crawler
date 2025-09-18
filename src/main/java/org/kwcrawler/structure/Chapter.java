package org.kwcrawler.structure;


public enum Chapter {
    SUMMARY("Summary", "-"),
    COVER("Okładka", "OZNACZENIE KSIĘGI WIECZYSTEJ"),
    CHAPTER_I_O("Dział I-O", "DZIAŁ I-O - OZNACZENIE NIERUCHOMOŚCI"),
    CHAPTER_I_SP("Dział I-Sp", "DZIAŁ I-SP - SPIS PRAW ZWIĄZANYCH Z WŁASNOŚCIĄ"),
    CHAPTER_II("Dział II", "DZIAŁ II - WŁASNOŚĆ"),
    CHAPTER_III("Dział III", "DZIAŁ III - PRAWA, ROSZCZENIA I OGRANICZENIA"),
    CHAPTER_IV("Dział IV", "DZIAŁ IV - HIPOTEKA");

    private final String tabName;
    private final String fullName;

    Chapter(String tabName, String fullName) {
        this.tabName = tabName;
        this.fullName = fullName;
    }

    public String getTabName() {
        return tabName;
    }

    public String getFullName() {
        return fullName;
    }

    public static Chapter[] all() {
        return new Chapter[] {COVER, CHAPTER_I_O, CHAPTER_I_SP, CHAPTER_II, CHAPTER_III, CHAPTER_IV};
    }

    public static Chapter[] allWithoutCover() {
        return new Chapter[] {CHAPTER_I_O, CHAPTER_I_SP, CHAPTER_II, CHAPTER_III, CHAPTER_IV};
    }
}
