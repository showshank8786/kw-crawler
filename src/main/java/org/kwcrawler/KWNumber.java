package org.kwcrawler;

import org.kwcrawler.structure.Chapter;
import org.kwcrawler.structure.Filenames;

import java.util.ArrayList;
import java.util.List;

public class KWNumber {

    private final CourtCode courtCode;
    private final Integer ledgerNumber;
    private final byte controlDigit;

    public KWNumber(CourtCode courtCode, Integer ledgerNumber) {
        validateLedgerNumber(ledgerNumber);
        this.courtCode = courtCode;
        this.ledgerNumber = ledgerNumber;
        this.controlDigit = ControlDigit.calculate(courtCode, ledgerNumber);
    }

    public KWNumber(CourtCode courtCode, Integer ledgerNumber, Byte controlDigit) {
        this(courtCode, ledgerNumber);

        if (controlDigit != this.controlDigit) {
            throw new IllegalArgumentException("Invalid control digit, expected: " + controlDigit + ", calculated: " + this.controlDigit);
        }
    }

    public KWNumber(String courtCode, String ledgerNumber, String controlDigit) {
        this(new CourtCode(courtCode), Integer.parseInt(ledgerNumber), Byte.parseByte(controlDigit));
    }

    public KWNumber(String kwNumber) {
        this(kwNumber.split("/")[0], kwNumber.split("/")[1], kwNumber.split("/")[2]);
    }

    private void validateLedgerNumber(Integer ledgerNumber) {
        if (ledgerNumber < 0 || ledgerNumber > 99999999) {
            throw new IllegalArgumentException("Invalid ledger number: " + ledgerNumber);
        }
    }

    public CourtCode getCourtCode() {
        return courtCode;
    }

    public String getLedgerNumber() {
        return String.format("%08d", ledgerNumber);
    }

    public String getControlDigit() {
        return Byte.toString(controlDigit);
    }

    public String toCode() {
        return courtCode + "/" + getLedgerNumber() + "/" + controlDigit;
    }

    @Override
    public String toString() {
        var filename = Filenames.getFilename(this, Chapter.CHAPTER_II);
        return "\u001B]8;;file://" + filename.toAbsolutePath() + "\u001B\\" + toCode() + "\u001B]8;;\u001B\\";
    }

    public static List<KWNumber> kwNumbersInRange(CourtCode courtCode, int start, int end) {
        var kwNumbers = new ArrayList<KWNumber>();
        for (int i = start; i < end; i++) {
            try {
                var kwNumber = new KWNumber(courtCode, i);
                kwNumbers.add(kwNumber);
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
        return kwNumbers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        var kwNumber = (KWNumber) obj;

        if (controlDigit != kwNumber.controlDigit) {
            return false;
        }
        if (!courtCode.equals(kwNumber.courtCode)) {
            return false;
        }
        return ledgerNumber.equals(kwNumber.ledgerNumber);
    }
}
