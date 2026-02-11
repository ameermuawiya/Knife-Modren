package io.github.mthli.knifedemo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.mthli.knife.Knife;
import io.github.mthli.knife.Span;

public class MainActivity extends AppCompatActivity {

  private static final int MENU_BOLD = 1;
  private static final int MENU_ITALIC = 2;
  private static final int MENU_UNDERLINE = 3;

  private static final float ALPHA_SELECTED = 1f;
  private static final float ALPHA_DIM = 0.7f;

  /*
  Sample HTML for testing supported formats.
  */
  private static final String INITIAL_HTML = "<b>The Message of Islam</b><br><br>" +
        "Islam invites humanity to <b>truth</b>, <b>justice</b>, and <b>peace</b>.<br>" +
        "It calls the heart to know <b>One God</b>, to live with purpose, and to treat every human being with dignity and compassion.<br><br>" +
        "<blockquote>" +
        "Indeed, in the remembrance of Allah do hearts find rest." +
        "</blockquote><br>" +
        "<b>Core Beliefs</b><br><br>" +
        "<ul>" +
        "<li><b>Tawheed</b>: Belief in One God, the Creator and Sustainer of all existence</li>" +
        "<li><b>Prophethood</b>: Divine guidance delivered through chosen messengers</li>" +
        "<li><b>Revelation</b>: The Qur'an as the final and preserved message</li>" +
        "<li><b>Hereafter</b>: Accountability, justice, and eternal life after death</li>" +
        "</ul><br>" +
        "<b>Why Islam</b><br><br>" +
        "Islam is not merely a religion. It is a <i>complete way of life</i> that guides both the inner soul and outward actions.<br><br>" +
        "<ul>" +
        "<li>It connects the <b>soul</b> to its Creator</li>" +
        "<li>It balances <b>faith</b> and <b>reason</b></li>" +
        "<li>It builds <b>strong moral character</b></li>" +
        "<li>It establishes <b>social justice</b> and responsibility</li>" +
        "</ul><br>" +
        "<b>A Beautiful Reminder</b><br><br>" +
        "<blockquote>" +
        "Islam commands kindness to parents, relatives, neighbors, the poor,<br>" +
        "and even to those who disagree with us." +
        "</blockquote><br>" +
        "<b>Knowledge and Guidance</b><br><br>" +
        "The Qur'an invites reflection, understanding, and wisdom.<br>" +
        "<a href=\"https://quran.com\">Read the Holy Qur'an online</a><br><br>" +
        "To learn more about Islamic beliefs and teachings:<br>" +
        "<a href=\"https://www.islamreligion.com\">Explore authentic Islamic resources</a><br><br>" +
        "<b>Final Reflection</b><br><br>" +
        "Islam does not force belief upon anyone.<br>" +
        "It <b>invites</b>, <b>explains</b>, and <b>waits</b> with patience and mercy.<br><br>" +
        "The choice is yours.";

  private EditText editor;
  private Knife knife;

  private boolean showingHtml = false;
  private String cachedHtml;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    setupToolbar();
    setupEditor();
    setupKnife();
    setupFormattingButtons();
    setupSelectionMenu();
    setupUndoRedoButtons();
  }

  // Sets toolbar as action bar
  private void setupToolbar() {
    MaterialToolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
  }

  // Initializes editor view
  private void setupEditor() {
    editor = findViewById(R.id.text);
  }

  // Initializes Knife editor and selection listener
  private void setupKnife() {
    knife = new Knife(editor);
    knife.setHtml(INITIAL_HTML);

    knife.setSelectionListener(
        () -> {
          updateFormatState(R.id.bold, knife.has(Knife.BOLD));
          updateFormatState(R.id.italic, knife.has(Knife.ITALIC));
          updateFormatState(R.id.underline, knife.has(Knife.UNDERLINE));
          updateFormatState(R.id.strikethrough, knife.has(Knife.STRIKE));
          updateFormatState(R.id.bullet, knife.has(Knife.BULLET));
          updateFormatState(R.id.quote, knife.has(Knife.QUOTE));
          updateFormatState(R.id.link, knife.has(Knife.URL));
        });
  }

  // Sets formatting button actions
  private void setupFormattingButtons() {

    setFormatButton(R.id.bold, Knife.BOLD);
    setFormatButton(R.id.italic, Knife.ITALIC);
    setFormatButton(R.id.underline, Knife.UNDERLINE);
    setFormatButton(R.id.strikethrough, Knife.STRIKE);
    setFormatButton(R.id.bullet, Knife.BULLET);
    setFormatButton(R.id.quote, Knife.QUOTE);

    findViewById(R.id.link)
        .setOnClickListener(v ->
            showLinkDialog(knife.getLink(editor.getSelectionStart())));

    findViewById(R.id.clear)
        .setOnClickListener(v -> confirmClear());

    findViewById(R.id.code)
        .setOnClickListener(v -> toggleCodeView());

    keepOriginalAlpha(R.id.clear);
    keepOriginalAlpha(R.id.code);
  }

  // Sets undo and redo actions
  private void setupUndoRedoButtons() {
    keepOriginalAlpha(R.id.undo);
    keepOriginalAlpha(R.id.redo);

    findViewById(R.id.undo).setOnClickListener(v -> knife.undo());
    findViewById(R.id.redo).setOnClickListener(v -> knife.redo());
  }

  /*
  Adds formatting options without breaking system actions
  */
  private void setupSelectionMenu() {
    editor.setCustomSelectionActionModeCallback(
        new ActionMode.Callback() {

          @Override
          public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            menu.add(Menu.NONE, MENU_BOLD, Menu.NONE, R.string.toast_bold);
            menu.add(Menu.NONE, MENU_ITALIC, Menu.NONE, R.string.toast_italic);
            menu.add(Menu.NONE, MENU_UNDERLINE, Menu.NONE, R.string.toast_underline);
            return true;
          }

          @Override
          public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
          }

          @Override
          public boolean onActionItemClicked(ActionMode mode, MenuItem item) {

            if (item.getItemId() == MENU_BOLD) {
              knife.toggle(Knife.BOLD);
              return true;
            }

            if (item.getItemId() == MENU_ITALIC) {
              knife.toggle(Knife.ITALIC);
              return true;
            }

            if (item.getItemId() == MENU_UNDERLINE) {
              knife.toggle(Knife.UNDERLINE);
              return true;
            }

            // Let system handle copy, cut, select all, etc
            return false;
          }

          @Override
          public void onDestroyActionMode(ActionMode mode) {}
        });
  }

  // Toggles HTML and styled editor view
  private void toggleCodeView() {
    if (!showingHtml) {
      cachedHtml = knife.getHtml();
      editor.setText(cachedHtml);
    } else {
      knife.setHtml(editor.getText().toString());
    }
    showingHtml = !showingHtml;
  }

  // Shows confirmation before clearing formatting
  private void confirmClear() {
    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_clear_title)
        .setMessage(R.string.dialog_clear_message)
        .setPositiveButton(
            R.string.dialog_button_ok,
            (d, w) -> knife.clearFormat())
        .setNegativeButton(R.string.dialog_button_cancel, null)
        .show();
  }

  // Sets formatting button behavior
  private void setFormatButton(int id, Class<?> format) {
    View v = findViewById(id);
    v.setAlpha(ALPHA_DIM);
    v.setOnClickListener(view -> knife.toggle(format));
  }

  // Updates alpha for active formatting
  private void updateFormatState(int id, boolean active) {
    findViewById(id).setAlpha(active ? ALPHA_SELECTED : ALPHA_DIM);
  }

  // Keeps button fully visible
  private void keepOriginalAlpha(int id) {
    findViewById(id).setAlpha(ALPHA_SELECTED);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.github) {
      startActivity(
          new Intent(
              Intent.ACTION_VIEW,
              Uri.parse(getString(R.string.github_repo_url))));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  // Shows dialog to insert or edit link
  private void showLinkDialog(Span<String> span) {
    int start = span != null ? span.start : editor.getSelectionStart();
    int end = span != null ? span.end : editor.getSelectionEnd();
    String link = span != null ? span.data : null;

    if (start == end) return;

    View view = getLayoutInflater().inflate(R.layout.dialog_link, null, false);
    EditText edit = view.findViewById(R.id.edit);
    if (link != null) edit.setText(link);

    new MaterialAlertDialogBuilder(this)
        .setTitle(R.string.dialog_link_title)
        .setView(view)
        .setPositiveButton(
            R.string.dialog_button_ok,
            (d, w) -> {
              String input = edit.getText().toString().trim();
              if (TextUtils.isEmpty(input)) {
                knife.remove(Knife.URL, start, end);
              } else {
                knife.setLink(input, start, end);
              }
            })
        .setNegativeButton(R.string.dialog_button_cancel, null)
        .show();
  }
}