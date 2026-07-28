package pro.sketchware.activities.chat.port;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.QuickQuoteHandler;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.lang.completion.CompletionPublisher;
import io.github.rosemoe.sora.lang.format.Formatter;
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.SymbolPairMatch;

/**
 * Sora language wrapper that keeps the existing syntax/completion language and
 * adds one Void AI completion item while the user types.
 */
public final class VoidPortAiAutocompleteLanguage implements Language {
    private final Context appContext;
    private final String scId;
    private final String filePath;
    private final String languageName;
    private final Language delegate;
    private long lastAcceptedAt = 0L;

    public VoidPortAiAutocompleteLanguage(Context context, String scId, String filePath,
                                          String languageName, Language delegate) {
        this.appContext = context == null ? null : context.getApplicationContext();
        this.scId = scId == null ? "" : scId;
        this.filePath = filePath == null ? "" : filePath;
        this.languageName = languageName == null ? "text" : languageName;
        this.delegate = delegate;
    }

    public static Language wrap(Context context, String scId, String filePath, String languageName, Language language) {
        if (language instanceof VoidPortAiAutocompleteLanguage) {
            return language;
        }
        return new VoidPortAiAutocompleteLanguage(context, scId, filePath, languageName, language);
    }

    @NonNull
    @Override
    public AnalyzeManager getAnalyzeManager() {
        return delegate.getAnalyzeManager();
    }

    @Override
    public int getInterruptionLevel() {
        return delegate.getInterruptionLevel();
    }

    @Override
    public void requireAutoComplete(@NonNull ContentReference content, @NonNull CharPosition position,
                                    @NonNull CompletionPublisher publisher,
                                    @NonNull Bundle extraArguments) {
        delegate.requireAutoComplete(content, position, publisher, extraArguments);
        if (appContext == null || !VoidPortAutocompleteService.isEnabled(VoidPortSettings.prefs(appContext))) {
            return;
        }

        String fullText = readAll(content);
        int cursorOffset = content.getCharIndex(position.line, position.column);
        boolean justAccepted = System.currentTimeMillis() - lastAcceptedAt < 500;
        VoidPortAutocompleteService.CompletionResult result = VoidPortAutocompleteService.complete(
                appContext,
                fullText,
                cursorOffset,
                languageName,
                filePath,
                justAccepted
        );
        if (!result.generated || result.insertText.trim().isEmpty()) {
            return;
        }
        publisher.addItem(new AiCompletionItem(result.insertText));
        publisher.updateList(true);
    }

    @Override
    public int getIndentAdvance(@NonNull ContentReference content, int line, int column) {
        return delegate.getIndentAdvance(content, line, column);
    }

    @Override
    public int getIndentAdvance(@NonNull ContentReference content, int line, int column,
                                int spaceCountOnLine, int tabCountOnLine) {
        return delegate.getIndentAdvance(content, line, column, spaceCountOnLine, tabCountOnLine);
    }

    @Override
    public boolean useTab() {
        return delegate.useTab();
    }

    @NonNull
    @Override
    public Formatter getFormatter() {
        return delegate.getFormatter();
    }

    @Override
    public SymbolPairMatch getSymbolPairs() {
        return delegate.getSymbolPairs();
    }

    @Override
    public NewlineHandler[] getNewlineHandlers() {
        return delegate.getNewlineHandlers();
    }

    @Nullable
    @Override
    public QuickQuoteHandler getQuickQuoteHandler() {
        return delegate.getQuickQuoteHandler();
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    private String readAll(ContentReference content) {
        StringBuilder builder = new StringBuilder();
        int lineCount = content.getLineCount();
        for (int i = 0; i < lineCount; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(content.getLine(i));
        }
        return builder.toString();
    }

    private final class AiCompletionItem extends CompletionItem {
        private final String insertText;

        AiCompletionItem(String insertText) {
            super("AI: " + preview(insertText), "Void autocomplete");
            this.insertText = insertText == null ? "" : insertText;
        }

        @Override
        public void performCompletion(@NonNull CodeEditor editor, @NonNull Content text, int line, int column) {
            if (insertText.isEmpty()) {
                return;
            }
            text.insert(line, column, insertText);
            // Content's indexer can still point to the pre-insert snapshot here.
            // Calling getCharPosition with the new offset then crashes the XML
            // editor with ArrayIndexOutOfBoundsException. CodeEditor already
            // advances the caret for completion inserts, so do not re-index it.
            lastAcceptedAt = System.currentTimeMillis();
        }
    }

    private static String preview(String value) {
        String clean = value == null ? "" : value.replace('\n', ' ').trim();
        if (clean.length() > 48) {
            clean = clean.substring(0, 48) + "...";
        }
        return clean.isEmpty() ? "completion" : clean;
    }
}
