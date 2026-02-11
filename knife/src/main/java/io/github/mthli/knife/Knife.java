package io.github.mthli.knife;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Handler;
import android.text.Editable;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BulletSpan;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import io.github.mthli.knife.spans.KnifeBoldSpan;
import io.github.mthli.knife.spans.KnifeBulletSpan;
import io.github.mthli.knife.spans.KnifeItalicSpan;
import io.github.mthli.knife.spans.KnifeQuoteSpan;
import io.github.mthli.knife.spans.KnifeStrikethroughSpan;
import io.github.mthli.knife.spans.KnifeURLSpan;
import io.github.mthli.knife.spans.KnifeUnderlineSpan;

@SuppressWarnings({"WeakerAccess", "unused"}) // Public API
public class Knife {

  public static final Class<?> BOLD = KnifeBoldSpan.class;
  public static final Class<?> ITALIC = KnifeItalicSpan.class;
  public static final Class<?> UNDERLINE = KnifeUnderlineSpan.class;
  public static final Class<?> STRIKE = KnifeStrikethroughSpan.class;
  public static final Class<?> BULLET = KnifeBulletSpan.class;
  public static final Class<?> QUOTE = KnifeQuoteSpan.class;
  public static final Class<?> URL = KnifeURLSpan.class;

  private final EditText editText; // Changed to EditText

  private OnSelectionChangedListener selectionListener;
  private SpanWatcher spanWatcher;

  private int bulletColor = Color.BLUE;
  private int bulletRadius = 2;
  private int bulletGap = 8;
  private int linkColor = 0;
  private boolean linkUnderline = true;
  private int quoteColor = Color.BLUE;
  private int quoteStripeWidth = 2;
  private int quoteGap = 8;

  private boolean historyEnable = true;
  private int historySize = 99;
  private final List<HistoryEntry> historyList = new ArrayList<>();
  private int historyCursor = 0;
  private final Handler handler = new Handler();
  private final Runnable updateHistoryRunnable = this::addHistory;

  private static class HistoryEntry {
    final SpannableStringBuilder text;
    final int start;
    final int end;

    HistoryEntry(SpannableStringBuilder t, int s, int e) {
      text = t;
      start = s;
      end = e;
    }
  }

  private String currentUrl;

  public Knife(EditText editText) {
    this.editText = editText;

    bulletRadius = convertDpToPixels(bulletRadius);
    bulletGap = convertDpToPixels(bulletGap);
    quoteStripeWidth = convertDpToPixels(quoteStripeWidth);
    quoteGap = convertDpToPixels(quoteGap);

    TypedArray arr = editText.getContext().obtainStyledAttributes(null, R.styleable.KnifeText);

    bulletColor = arr.getColor(R.styleable.KnifeText_knife_bulletColor, bulletColor);
    bulletRadius = arr.getDimensionPixelSize(R.styleable.KnifeText_knife_bulletRadius, bulletRadius);
    bulletGap = arr.getDimensionPixelSize(R.styleable.KnifeText_knife_bulletGapWidth, bulletGap);
    linkColor = arr.getColor(R.styleable.KnifeText_knife_linkColor, linkColor);
    linkUnderline = arr.getBoolean(R.styleable.KnifeText_knife_linkUnderline, linkUnderline);
    quoteColor = arr.getColor(R.styleable.KnifeText_knife_quoteColor, quoteColor);
    quoteStripeWidth = arr.getDimensionPixelSize(R.styleable.KnifeText_knife_quoteStripeWidth, quoteStripeWidth);
    quoteGap = arr.getDimensionPixelSize(R.styleable.KnifeText_knife_quoteGapWidth, quoteGap);
    historyEnable = arr.getBoolean(R.styleable.KnifeText_knife_historyEnable, historyEnable);
    historySize = arr.getInt(R.styleable.KnifeText_knife_historySize, historySize);

    arr.recycle();

    editText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable text) {
            ensureSpanWatcher();
            clearNonKnifeStyles(text);
            fixParagraphs(text, BULLET);
            fixParagraphs(text, QUOTE);

            if (historyEnable) {
              handler.removeCallbacks(updateHistoryRunnable);
              handler.postDelayed(updateHistoryRunnable, 800);
            }
          }
        });

    spanWatcher =
        new SpanWatcher() {
          @Override
          public void onSpanAdded(Spannable text, Object what, int start, int end) {
            if (what.getClass() == UnderlineSpan.class) {
              text.removeSpan(what);
            }
          }

          @Override
          public void onSpanRemoved(Spannable text, Object what, int start, int end) {}

          @Override
          public void onSpanChanged(
              Spannable text, Object what, int ostart, int oend, int nstart, int nend) {
            if (selectionListener != null && what == Selection.SELECTION_END) {
              selectionListener.onSelectionChanged();
            }
          }
        };

    ensureSpanWatcher();
  }

  public void undo() {
    if (historyCursor <= 0) return;
    historyCursor--;
    HistoryEntry entry = historyList.get(historyCursor);
    editText.setText(entry.text);
    editText.setSelection(entry.start, entry.end);
  }

  public void redo() {
    if (historyCursor >= historyList.size() - 1) return;
    historyCursor++;
    HistoryEntry entry = historyList.get(historyCursor);
    editText.setText(entry.text);
    editText.setSelection(entry.start, entry.end);
  }

  private void addHistory() {
    Editable current = editText.getText();
    if (current == null) return;

    SpannableStringBuilder copy = new SpannableStringBuilder(current);
    int start = editText.getSelectionStart();
    int end = editText.getSelectionEnd();

    HistoryEntry entry = new HistoryEntry(copy, start, end);

    // Remove future entries
    while (historyList.size() > historyCursor) {
      historyList.remove(historyList.size() - 1);
    }

    historyList.add(entry);

    // Limit history size
    if (historyList.size() > historySize) {
      historyList.remove(0);
      historyCursor--;
    }

    historyCursor = historyList.size();
  }

  private int convertDpToPixels(int value) {
    return Math.round(
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, editText.getResources().getDisplayMetrics()));
  }

  private void ensureSpanWatcher() {
    Spannable text = getText();
    SpanWatcher[] watchers = text.getSpans(0, 0, SpanWatcher.class);

    for (SpanWatcher watcher : watchers) {
      if (watcher == spanWatcher) return;
    }

    text.setSpan(spanWatcher, 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
  }

  private void logSpans() {
    Spanned text = getText();
    for (Object span : text.getSpans(0, text.length(), Object.class)) {
      int start = text.getSpanStart(span);
      int end = text.getSpanEnd(span);
      Log.d(
          "SPAN",
          span.getClass().getSimpleName()
              + ": "
              + start
              + " - "
              + end
              + " // "
              + text.subSequence(start, end)
              + " // "
              + text.getSpanFlags(span));
    }
  }

  private Spannable getText() {
    CharSequence seq = editText.getText();
    if (seq instanceof Spannable) {
      return (Spannable) seq;
    }
    SpannableString spannable = new SpannableString(seq);
    editText.setText(spannable);
    return spannable;
  }

  public void setHtml(String html) {
    if (html == null) {
      editText.setText(null);
      return;
    }
    SpannableStringBuilder builder = new SpannableStringBuilder();
    builder.append(KnifeParser.fromHtml(html));
    switchToKnifeStyle(builder);
    editText.setText(builder);
  }

  public String getHtml() {
    return KnifeParser.toHtml(getText());
  }

  public void set(Class<?> spanClass) {
    set(spanClass, editText.getSelectionStart(), editText.getSelectionEnd());
  }

  public void set(Class<?> spanClass, int start, int end) {
    Spannable text = getText();
    if (isParagraphSpan(spanClass)) {
      setParagraph(text, spanClass, start, end);
    } else {
      setSpan(text, spanClass, start, end);
    }
    if (selectionListener != null) selectionListener.onSelectionChanged();
  }

  public void remove(Class<?> spanClass) {
    remove(spanClass, editText.getSelectionStart(), editText.getSelectionEnd());
  }

  public void remove(Class<?> spanClass, int start, int end) {
    Spannable text = getText();
    if (isParagraphSpan(spanClass)) {
      removeParagraph(text, spanClass, start, end);
    } else {
      removeSpan(text, spanClass, start, end);
    }
    if (selectionListener != null) selectionListener.onSelectionChanged();
  }

  public boolean has(Class<?> spanClass) {
    return has(spanClass, editText.getSelectionStart(), editText.getSelectionEnd());
  }

  public boolean has(Class<?> spanClass, int start, int end) {
    Spannable text = getText();
    if (isParagraphSpan(spanClass)) {
      return isFullOfParagraphs(text, spanClass, start, end);
    }
    return isFullySpanned(text, spanClass, start, end);
  }

  public void toggle(Class<?> spanClass) {
    toggle(spanClass, editText.getSelectionStart(), editText.getSelectionEnd());
  }

  public void toggle(Class<?> spanClass, int start, int end) {
    Spannable text = getText();
    if (isParagraphSpan(spanClass)) {
      toggleParagraph(text, spanClass, start, end);
    } else {
      toggleSpan(text, spanClass, start, end);
    }
    if (selectionListener != null) selectionListener.onSelectionChanged();
  }

  public void clearFormat() {
    remove(BOLD);
    remove(ITALIC);
    remove(UNDERLINE);
    remove(STRIKE);
    remove(BULLET);
    remove(QUOTE);
    remove(URL);
  }

  public void setLink(String url, int start, int end) {
    currentUrl = url;
    set(URL, start, end);
    currentUrl = null;
  }

  public Span<String> getLink(int start) {
    Spannable text = getText();
    URLSpan[] urls = text.getSpans(start, start, URLSpan.class);
    if (urls.length == 0) return null;
    URLSpan span = urls[0];
    return new Span<>(span.getURL(), text.getSpanStart(span), text.getSpanEnd(span));
  }

  public void setSelectionListener(OnSelectionChangedListener listener) {
    selectionListener = listener;
  }

  private void clearNonKnifeStyles(Spannable text) {
    ParcelableSpan[] spans = text.getSpans(0, text.length(), ParcelableSpan.class);
    for (ParcelableSpan span : spans) {
      if (!span.getClass().getSimpleName().startsWith("Knife")) {
        text.removeSpan(span);
      }
    }
  }

  private void switchToKnifeStyle(Spannable text) {
    Object[] spans = text.getSpans(0, text.length(), Object.class);
    for (Object span : spans) {
      if (span.getClass().getSimpleName().startsWith("Knife")) continue;

      int s = text.getSpanStart(span);
      int e = text.getSpanEnd(span);

      if (span instanceof StyleSpan) {
        int style = ((StyleSpan) span).getStyle();
        text.removeSpan(span);
        if (style == KnifeBoldSpan.STYLE) setSpan(text, BOLD, s, e);
        else if (style == KnifeItalicSpan.STYLE) setSpan(text, ITALIC, s, e);
      } else if (span instanceof UnderlineSpan) {
        text.removeSpan(span);
        setSpan(text, UNDERLINE, s, e);
      } else if (span instanceof StrikethroughSpan) {
        text.removeSpan(span);
        setSpan(text, STRIKE, s, e);
      } else if (span instanceof BulletSpan) {
        text.removeSpan(span);
        setParagraph(text, BULLET, s, e);
      } else if (span instanceof QuoteSpan) {
        text.removeSpan(span);
        setParagraph(text, QUOTE, s, e);
      } else if (span instanceof URLSpan) {
        currentUrl = ((URLSpan) span).getURL();
        text.removeSpan(span);
        setSpan(text, URL, s, e);
        currentUrl = null;
      }
    }
  }

  private Object createSpan(Class<?> spanClass) {
    if (spanClass == BOLD) return new KnifeBoldSpan();
    if (spanClass == ITALIC) return new KnifeItalicSpan();
    if (spanClass == UNDERLINE) return new KnifeUnderlineSpan();
    if (spanClass == STRIKE) return new KnifeStrikethroughSpan();
    if (spanClass == BULLET) return new KnifeBulletSpan(bulletColor, bulletRadius, bulletGap);
    if (spanClass == QUOTE) return new KnifeQuoteSpan(quoteColor, quoteStripeWidth, quoteGap);
    if (spanClass == URL) {
      if (currentUrl == null || currentUrl.isEmpty()) {
        throw new IllegalArgumentException("Use setLink() method to add links");
      }
      return new KnifeURLSpan(currentUrl, linkColor, linkUnderline);
    }
    throw new IllegalArgumentException("Unknown span type: " + spanClass.getSimpleName());
  }

  private boolean isParagraphSpan(Class<?> spanClass) {
    return spanClass == BULLET || spanClass == QUOTE;
  }

  private boolean isSplittableSpan(Class<?> spanClass) {
    return spanClass != URL;
  }

  private void setSpan(Spannable text, Class<?> spanClass, int start, int end) {
    if (start == end) {
      Object[] spans = text.getSpans(start, end, spanClass);
      for (Object span : spans) {
        if (text.getSpanEnd(span) == end) {
          setSpanFlag(text, span, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        }
      }
      return;
    }

    removeSpan(text, spanClass, start, end);

    if (isSplittableSpan(spanClass)) {
      Object[] before = text.getSpans(start, start, spanClass);
      for (Object span : before) {
        start = Math.min(start, text.getSpanStart(span));
        text.removeSpan(span);
      }

      Object[] after = text.getSpans(end, end, spanClass);
      for (Object span : after) {
        end = Math.max(end, text.getSpanEnd(span));
        text.removeSpan(span);
      }
    }

    text.setSpan(createSpan(spanClass), start, end, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
  }

  private void removeSpan(Spannable text, Class<?> spanClass, int start, int end) {
    if (start == end) {
      Object[] spans = text.getSpans(start, end, spanClass);
      for (Object span : spans) {
        if (text.getSpanEnd(span) == end) {
          setSpanFlag(text, span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      return;
    }

    Object[] spans = text.getSpans(start, end, spanClass);
    for (Object span : spans) {
      int s = text.getSpanStart(span);
      int e = text.getSpanEnd(span);
      text.removeSpan(span);

      if (isSplittableSpan(spanClass)) {
        if (s < start) setSpan(text, spanClass, s, start);
        if (end < e) setSpan(text, spanClass, end, e);
      }
    }
  }

  private boolean isFullySpanned(Spannable text, Class<?> spanClass, int start, int end) {
    Object[] spans = text.getSpans(start, end, spanClass);

    if (start == end) {
      for (Object span : spans) {
        int s = text.getSpanStart(span);
        int e = text.getSpanEnd(span);
        int flag = text.getSpanFlags(span);
        if ((start > s && end < e) || (end == e && flag == Spanned.SPAN_EXCLUSIVE_INCLUSIVE)) {
          return true;
        }
      }
      return false;
    }

    for (int pos = start; pos < end; pos++) {
      boolean found = false;
      for (Object span : spans) {
        if (pos >= text.getSpanStart(span) && pos < text.getSpanEnd(span)) {
          found = true;
          break;
        }
      }
      if (!found) return false;
    }
    return true;
  }

  private void toggleSpan(Spannable text, Class<?> spanClass, int start, int end) {
    if (isFullySpanned(text, spanClass, start, end)) {
      removeSpan(text, spanClass, start, end);
    } else {
      setSpan(text, spanClass, start, end);
    }
  }

  private static void setSpanFlag(Spannable text, Object span, int flag) {
    int s = text.getSpanStart(span);
    int e = text.getSpanEnd(span);
    text.removeSpan(span);
    text.setSpan(span, s, e, flag);
  }

  private void setParagraph(Spannable text, Class<?> spanClass, int start, int end) {
    start = findLineStart(text, start);
    end = findLineEnd(text, end);

    int lineStart = start;
    while (lineStart < end) {
      int lineEnd = findLineEnd(text, lineStart);
      if (lineStart != lineEnd && !containsSpan(text, spanClass, lineStart, lineEnd)) {
        text.setSpan(createSpan(spanClass), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
      lineStart = lineEnd + 1;
    }
  }

  private void removeParagraph(Spannable text, Class<?> spanClass, int start, int end) {
    start = findLineStart(text, start);
    end = findLineEnd(text, end);

    Object[] spans = text.getSpans(start, end, spanClass);
    for (Object span : spans) {
      text.removeSpan(span);
    }
  }

  private boolean isFullOfParagraphs(Spannable text, Class<?> spanClass, int start, int end) {
    start = findLineStart(text, start);
    end = findLineEnd(text, end);
    if (start == end) return false;

    int lineStart = start;
    while (lineStart <= end) {
      int lineEnd = findLineEnd(text, lineStart);
      if (!containsSpan(text, spanClass, lineStart, lineEnd)) return false;
      lineStart = lineEnd + 1;
    }
    return true;
  }

  private void toggleParagraph(Spannable text, Class<?> spanClass, int start, int end) {
    if (isFullOfParagraphs(text, spanClass, start, end)) {
      removeParagraph(text, spanClass, start, end);
    } else {
      setParagraph(text, spanClass, start, end);
    }
  }

  private void fixParagraphs(Spannable text, Class<?> spanClass) {
    Object[] spans = text.getSpans(0, text.length(), spanClass);
    for (Object span : spans) {
      int s = text.getSpanStart(span);
      int e = text.getSpanEnd(span);
      int lineStart = findLineStart(text, s);
      int lineEnd = findLineEnd(text, e);

      if (s == lineStart && e == lineEnd) continue;

      text.removeSpan(span);

      int pos = lineStart;
      while (pos < lineEnd) {
        int next = findLineEnd(text, pos);
        if (pos != next && !containsSpan(text, spanClass, pos, next)) {
          text.setSpan(createSpan(spanClass), pos, next, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        pos = next + 1;
      }
    }
  }

  private static int findLineStart(CharSequence text, int pos) {
    if (pos <= 0) return 0;
    for (int i = pos - 1; i >= 0; i--) {
      if (text.charAt(i) == '\n') return i + 1;
    }
    return 0;
  }

  private static int findLineEnd(CharSequence text, int pos) {
    if (pos >= text.length()) return text.length();
    for (int i = pos; i < text.length(); i++) {
      if (text.charAt(i) == '\n') return i;
    }
    return text.length();
  }

  private static boolean containsSpan(Spanned text, Class<?> spanClass, int start, int end) {
    return text.getSpans(start, end, spanClass).length > 0;
  }

  public interface OnSelectionChangedListener {
    void onSelectionChanged();
  }
}
