package org.kwcrawler;


public class CourtCode {
    private final String code;

    public CourtCode(String code) {
        this.code = code;

        if (!CourtCodeValidator.isValidCourtCode(code)) {
            throw new IllegalArgumentException("Invalid court code: " + code);
        }
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        var other = (CourtCode) obj;
        return code.equals(other.code);
    }

    @Override
    public String toString() {
        return code;
    }
}
