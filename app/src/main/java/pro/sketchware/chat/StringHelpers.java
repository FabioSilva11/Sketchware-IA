package pro.sketchware.chat;

public final class StringHelpers {
    private StringHelpers() {
    }

    public static final class FirstLineSplit {
        public final String firstLine;
        public final String rest;

        public FirstLineSplit(String firstLine, String rest) {
            this.firstLine = firstLine;
            this.rest = rest;
        }
    }

    public static FirstLineSplit separateOutFirstLine(String content) {
        int newLineIdx = content.indexOf("\r\n");
        if (newLineIdx != -1) {
            String first = content.substring(0, newLineIdx);
            String rest = content.substring(newLineIdx + 2);
            return new FirstLineSplit(first, rest);
        }

        int newLineIdx2 = content.indexOf('\n');
        if (newLineIdx2 != -1) {
            String first = content.substring(0, newLineIdx2);
            String rest = content.substring(newLineIdx2 + 1);
            return new FirstLineSplit(first, rest);
        }

        return new FirstLineSplit(content, null);
    }
}
